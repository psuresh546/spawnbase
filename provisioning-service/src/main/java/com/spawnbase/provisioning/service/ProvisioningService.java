package com.spawnbase.provisioning.service;

import com.spawnbase.common.model.DatabaseType;
import com.spawnbase.common.model.InstanceState;
import com.spawnbase.provisioning.client.CredentialClient;
import com.spawnbase.provisioning.docker.DockerApiException;
import com.spawnbase.provisioning.docker.DockerClient;
import com.spawnbase.provisioning.dto.ContainerCreateRequest;
import com.spawnbase.provisioning.dto.ContainerInfo;
import com.spawnbase.provisioning.provider.DatabaseProvider;
import com.spawnbase.provisioning.provider.DatabaseProviderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ProvisioningService {

    private final DockerClient dockerClient;
    private final DatabaseProviderFactory providerFactory;
    private final CredentialClient credentialClient;
    private final RestClient metadataRestClient;
    private final RollbackService rollbackService;

    public ProvisioningService(
            DockerClient dockerClient,
            DatabaseProviderFactory providerFactory,
            CredentialClient credentialClient,
            RollbackService rollbackService,
            // Default value allows running locally without
            // Docker Compose environment variables set
            @Value("${metadata.service.url:http://localhost:8081}")
            String metadataServiceUrl) {
        this.dockerClient = dockerClient;
        this.providerFactory = providerFactory;
        this.credentialClient = credentialClient;
        this.rollbackService = rollbackService;
        this.metadataRestClient = RestClient.builder()
                .baseUrl(metadataServiceUrl)
                .build();
    }

    /**
     * Provision a new database container.
     *
     * Flow:
     * 1. Pull image
     * 2. Create container with health check config
     * 3. Start container
     * 4. Wait for healthy status (poll every 5s, max 2min)
     * 5. Inspect container to get assigned host port
     * 6. Update metadata → RUNNING with containerId + port
     * 7. Store credentials in credential-service
     *
     * On any failure:
     * - RollbackService stops + removes container
     * - Metadata updated to FAILED
     */
    public void provision(
            UUID instanceId,
            DatabaseType dbType,
            String password) {

        log.info("Provisioning {} instance: {}",
                dbType, instanceId);

        DatabaseProvider provider =
                providerFactory.getProvider(dbType);
        String containerName = "spawnbase-" + instanceId;
        String containerId = null;

        try {
            // Step 1 — Pull image
            log.info("Pulling image: {}",
                    provider.getDockerImage());
            dockerClient.pullImage(provider.getDockerImage());

            // Step 2 — Build container config
            ContainerCreateRequest createRequest =
                    buildCreateRequest(provider,
                            password, instanceId);

            // Step 3 — Create container
            containerId = dockerClient.createContainer(
                    containerName, createRequest);

            // Step 4 — Start container
            dockerClient.startContainer(containerId);

            // Step 5 — Wait for health check to pass
            log.info("Waiting for container to " +
                    "become healthy...");
            waitForHealthy(containerId);

            // Step 6 — Get assigned host port
            ContainerInfo info =
                    dockerClient.inspectContainer(containerId);
            Integer hostPort = info.getHostPort(
                    provider.getContainerPort() + "/tcp");

            log.info("Instance {} provisioned. Port: {}",
                    instanceId, hostPort);

            // Step 7 — Update metadata → RUNNING
            updateState(instanceId, InstanceState.RUNNING,
                    containerId, hostPort);

            // Step 8 — Store credentials
            String dbName = buildDbName(instanceId);
            String connectionUrl = provider.getConnectionUrl(
                    "localhost", hostPort, dbName);

            credentialClient.storeCredentials(
                    instanceId,
                    "spawnbase",
                    password,
                    connectionUrl,
                    hostPort,
                    dbName);

        } catch (Exception e) {
            log.error("Failed to provision instance {}: {}",
                    instanceId, e.getMessage(), e);

            // Best-effort rollback — stop + remove container
            rollbackService.rollback(instanceId, containerId);

            // Mark FAILED regardless of rollback result
            updateState(instanceId,
                    InstanceState.FAILED, null, null);
        }
    }

    /**
     * Delete instance — stops container and wipes credentials.
     *
     * Cleanup order matters:
     * 1. Remove container first (stop data plane)
     * 2. Delete credentials (remove secrets)
     * 3. Update metadata → DELETED
     *
     * Each step is independent — failure in one
     * doesn't skip the others.
     */
    public void delete(UUID instanceId, String containerId) {
        log.info("Deleting instance: {}", instanceId);
        try {
            if (containerId != null) {
                dockerClient.removeContainer(containerId);
                log.info("Container removed: {}", containerId);
            }

            credentialClient.deleteCredentials(instanceId);

            updateState(instanceId, InstanceState.DELETED,
                    null, null);

        } catch (DockerApiException e) {
            log.error("Failed to delete instance {}: {}",
                    instanceId, e.getMessage(), e);
            updateState(instanceId, InstanceState.FAILED,
                    null, null);
        }
    }

    /**
     * Stop a running container.
     * Transitions: RUNNING → STOPPED
     */
    public void stop(UUID instanceId, String containerId) {
        log.info("Stopping instance: {}", instanceId);
        try {
            dockerClient.stopContainer(containerId);
            updateState(instanceId, InstanceState.STOPPED,
                    null, null);
        } catch (DockerApiException e) {
            log.error("Failed to stop instance {}: {}",
                    instanceId, e.getMessage(), e);
            updateState(instanceId, InstanceState.FAILED,
                    null, null);
        }
    }

    /**
     * Start a stopped container.
     * Transitions: STOPPED/STARTING → RUNNING
     * Re-inspects port after start (port may change).
     */
    public void start(
            UUID instanceId,
            String containerId,
            DatabaseProvider provider) {
        log.info("Starting instance: {}", instanceId);
        try {
            dockerClient.startContainer(containerId);
            waitForHealthy(containerId);

            ContainerInfo info =
                    dockerClient.inspectContainer(containerId);
            Integer hostPort = info.getHostPort(
                    provider.getContainerPort() + "/tcp");

            updateState(instanceId, InstanceState.RUNNING,
                    containerId, hostPort);

        } catch (Exception e) {
            log.error("Failed to start instance {}: {}",
                    instanceId, e.getMessage(), e);
            updateState(instanceId, InstanceState.FAILED,
                    null, null);
        }
    }

    // ─────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────

    /**
     * Build a deterministic DB name from the instance UUID.
     * Uses first 8 chars of UUID without hyphens.
     * e.g. UUID a67e8ef9-... → db_a67e8ef9
     */
    private String buildDbName(UUID instanceId) {
        return "db_" + instanceId.toString()
                .replace("-", "")
                .substring(0, 8);
    }

    /**
     * Build the container creation request.
     * Includes: image, env vars, port bindings,
     * memory limit, restart policy, health check.
     */
    private ContainerCreateRequest buildCreateRequest(
            DatabaseProvider provider,
            String password,
            UUID instanceId) {

        String dbName = buildDbName(instanceId);
        long memBytes = parseMemory(provider.getMemoryLimit());

        // Port binding: container port → random host port
        String portKey = provider.getContainerPort() + "/tcp";
        Map<String, List<ContainerCreateRequest.PortBinding>>
                ports = new HashMap<>();
        ports.put(portKey, List.of(
                ContainerCreateRequest.PortBinding.builder()
                        .hostPort("0") // 0 = assign random port
                        .build()));

        // Health check command: CMD + provider args
        List<String> healthTest = new ArrayList<>();
        healthTest.add("CMD");
        healthTest.addAll(Arrays.asList(
                provider.getHealthCheckCommand()));

        return ContainerCreateRequest.builder()
                .image(provider.getDockerImage())
                .env(Arrays.asList(
                        provider.getEnvironmentVariables(
                                password, dbName)))
                .hostConfig(
                        ContainerCreateRequest.HostConfig
                                .builder()
                                .portBindings(ports)
                                .memory(memBytes)
                                .restartPolicy(
                                        ContainerCreateRequest
                                                .RestartPolicy.builder()
                                                .name("on-failure")
                                                .maximumRetryCount(3)
                                                .build())
                                .build())
                .healthcheck(
                        ContainerCreateRequest.HealthCheck
                                .builder()
                                .test(healthTest)
                                // Intervals in nanoseconds
                                .interval(10_000_000_000L) // 10s
                                .timeout(5_000_000_000L)   // 5s
                                .retries(5)
                                .startPeriod(30_000_000_000L) // 30s
                                .build())
                .build();
    }

    /**
     * Poll container health every 5 seconds.
     * Max 24 attempts = 2 minutes total.
     * Throws DockerApiException if not healthy in time.
     */
    private void waitForHealthy(String containerId) {
        int maxAttempts = 24;
        int attempts = 0;

        while (attempts < maxAttempts) {
            try {
                Thread.sleep(5000);

                ContainerInfo info =
                        dockerClient.inspectContainer(
                                containerId);

                if (info.isHealthy()) {
                    log.info("Container healthy after {}s",
                            (attempts + 1) * 5);
                    return;
                }

                log.info("Health check attempt {}/{}",
                        attempts + 1, maxAttempts);
                attempts++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DockerApiException(
                        -1, "Interrupted while waiting " +
                        "for healthy status");
            }
        }

        throw new DockerApiException(
                -1, "Container not healthy within 2 minutes");
    }

    /**
     * Update instance state in metadata-service.
     * containerId and hostPort are optional —
     * only sent when non-null.
     *
     * Uses PATCH so partial updates don't overwrite
     * fields we don't intend to change.
     */
    private void updateState(
            UUID instanceId,
            InstanceState state,
            String containerId,
            Integer hostPort) {

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("state", state.name());

            if (containerId != null) {
                body.put("containerId", containerId);
            }
            if (hostPort != null) {
                body.put("hostPort", hostPort);
            }

            metadataRestClient.patch()
                    .uri("/api/instances/{id}/state",
                            instanceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Instance {} → {}", instanceId, state);

        } catch (Exception e) {
            log.error("Failed to update metadata for " +
                            "instance {}: {}",
                    instanceId, e.getMessage(), e);
        }
    }

    /**
     * Parse memory limit string to bytes.
     * Supports: "512m", "512M", "1g", "1G"
     * Default: parse as raw bytes.
     */
    private long parseMemory(String memLimit) {
        if (memLimit.endsWith("m")
                || memLimit.endsWith("M")) {
            return Long.parseLong(
                    memLimit.substring(0,
                            memLimit.length() - 1))
                    * 1024 * 1024;
        } else if (memLimit.endsWith("g")
                || memLimit.endsWith("G")) {
            return Long.parseLong(
                    memLimit.substring(0,
                            memLimit.length() - 1))
                    * 1024 * 1024 * 1024;
        }
        return Long.parseLong(memLimit);
    }
}