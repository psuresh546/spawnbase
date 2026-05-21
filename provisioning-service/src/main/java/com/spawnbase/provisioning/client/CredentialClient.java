package com.spawnbase.provisioning.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for credential-service.
 *
 * URL is configurable via property so it works
 * both locally (localhost:8084) and in Docker Compose
 * (http://credential-service:8084).
 */
@Component
@Slf4j
public class CredentialClient {

    private final RestClient restClient;

    public CredentialClient(
            // Default value allows running locally without
            // Docker Compose environment variables set
            @Value("${credential.service.url:" +
                    "http://localhost:8084}")
            String credentialServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(credentialServiceUrl)
                .build();
        log.info("CredentialClient → {}",
                credentialServiceUrl);
    }

    /**
     * Store credentials for a newly provisioned instance.
     * Called after container reaches RUNNING state.
     *
     * Failure is logged but does NOT fail provisioning —
     * credentials can be re-stored manually if needed.
     */
    public void storeCredentials(
            UUID instanceId,
            String username,
            String password,
            String connectionUrl,
            Integer hostPort,
            String dbName) {

        log.info("Storing credentials for instance: {}",
                instanceId);

        Map<String, Object> body = new HashMap<>();
        body.put("instanceId", instanceId.toString());
        body.put("username", username);
        body.put("password", password);
        body.put("connectionUrl", connectionUrl);
        body.put("hostPort", hostPort);
        body.put("dbName", dbName);

        try {
            restClient.post()
                    .uri("/api/credentials")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Credentials stored successfully " +
                    "for instance: {}", instanceId);

        } catch (Exception e) {
            log.error("Failed to store credentials " +
                            "for instance {}: {}",
                    instanceId, e.getMessage());
        }
    }

    /**
     * Delete credentials when instance is deleted.
     * Called as part of the delete flow cleanup.
     */
    public void deleteCredentials(UUID instanceId) {
        log.info("Deleting credentials for instance: {}",
                instanceId);

        try {
            restClient.delete()
                    .uri("/api/credentials/{instanceId}",
                            instanceId)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Credentials deleted for instance: {}",
                    instanceId);

        } catch (Exception e) {
            log.error("Failed to delete credentials " +
                            "for instance {}: {}",
                    instanceId, e.getMessage());
        }
    }
}