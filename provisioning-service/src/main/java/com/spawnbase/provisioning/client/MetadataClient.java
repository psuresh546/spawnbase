package com.spawnbase.provisioning.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spawnbase.common.model.DatabaseType;
import com.spawnbase.common.model.InstanceState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * HTTP client for metadata-service.
 *
 * URL is configurable via property so it works
 * both locally (localhost:8081) and in Docker Compose
 * (http://metadata-service:8081).
 *
 * Used by DriftDetector to query instances by state
 * and check if their containers still exist in Docker.
 */
@Component
@Slf4j
public class MetadataClient {

    private final RestClient restClient;

    public MetadataClient(
            // Default value allows running locally without
            // Docker Compose environment variables set
            @Value("${metadata.service.url:" +
                    "http://localhost:8081}")
            String metadataServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(metadataServiceUrl)
                .build();
        log.info("MetadataClient → {}", metadataServiceUrl);
    }

    /**
     * Get all instances in a specific state.
     * Used by drift detector to find RUNNING, FAILED,
     * and DELETED instances for reconciliation.
     */
    public List<InstanceSummary> getInstancesByState(
            InstanceState state) {

        log.debug("Fetching instances with state: {}", state);

        try {
            List<InstanceSummary> instances =
                    restClient.get()
                            .uri("/api/instances/admin/all")
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<InstanceSummary>>() {});

            if (instances == null) return List.of();

            return instances.stream()
                    .filter(i -> i.getState() == state)
                    .toList();

        } catch (Exception e) {
            log.error("Failed to fetch instances by " +
                    "state {}: {}", state, e.getMessage());
            return List.of();
        }
    }

    /**
     * Lightweight DTO — only the fields drift
     * detector needs from each instance.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstanceSummary {

        @JsonProperty("id")
        private UUID id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("state")
        private InstanceState state;

        @JsonProperty("dbType")
        private DatabaseType dbType;

        @JsonProperty("containerId")
        private String containerId;

        @JsonProperty("hostPort")
        private Integer hostPort;

        @JsonProperty("updatedAt")
        private LocalDateTime updatedAt;
    }
}