package com.spawnbase.provisioning.controller;

import com.spawnbase.common.model.DatabaseType;
import com.spawnbase.common.util.PasswordGenerator;
import com.spawnbase.provisioning.dto.ProvisionRequest;
import com.spawnbase.provisioning.provider.DatabaseProvider;
import com.spawnbase.provisioning.provider.DatabaseProviderFactory;
import com.spawnbase.provisioning.service.ProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/provisioning")
@Slf4j
@RequiredArgsConstructor
public class ProvisioningController {

    private final ProvisioningService provisioningService;
    private final DatabaseProviderFactory providerFactory;

    @PostMapping("/instances/{id}/provision")
    public ResponseEntity<Map<String, Object>> provision(
            @PathVariable UUID id,
            @Valid @RequestBody ProvisionRequest request) {

        log.info("Provision request for instance {} — type: {}",
                id, request.getDbType());

        String password = (request.getPassword() == null
                || request.getPassword().isBlank())
                ? PasswordGenerator.generate()
                : request.getPassword();

        log.info("Password {} for instance {}",
                request.getPassword() == null
                        ? "auto-generated" : "provided by caller",
                id);

        CompletableFuture.runAsync(() ->
                provisioningService.provision(
                        id,
                        request.getDbType(),
                        password,
                        request.getDbName(),
                        request.getUsername()
                )
        );

        return ResponseEntity.accepted().body(Map.of(
                "message", "Provisioning started",
                "instanceId", id.toString(),
                "dbType", request.getDbType().name(),
                "status", "PROVISIONING",
                "passwordGenerated",
                request.getPassword() == null
                        || request.getPassword().isBlank()
        ));
    }

    @PostMapping("/instances/{id}/stop")
    public ResponseEntity<Map<String, Object>> stop(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String containerId = body.get("containerId");
        log.info("Stop request for instance {}", id);
        CompletableFuture.runAsync(() ->
                provisioningService.stop(id, containerId));

        return ResponseEntity.accepted().body(Map.of(
                "message", "Stop initiated",
                "instanceId", id.toString()
        ));
    }

    @PostMapping("/instances/{id}/start")
    public ResponseEntity<Map<String, Object>> start(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String containerId = body.get("containerId");
        DatabaseType dbType = DatabaseType.valueOf(
                body.get("dbType"));
        DatabaseProvider provider =
                providerFactory.getProvider(dbType);

        log.info("Start request for instance {}", id);
        CompletableFuture.runAsync(() ->
                provisioningService.start(
                        id, containerId, provider));

        return ResponseEntity.accepted().body(Map.of(
                "message", "Start initiated",
                "instanceId", id.toString()
        ));
    }

    @PostMapping("/instances/{id}/restart")
    public ResponseEntity<Map<String, Object>> restart(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String containerId = body.get("containerId");
        log.info("Restart request for instance {}", id);
        CompletableFuture.runAsync(() ->
                provisioningService.restart(id, containerId));

        return ResponseEntity.accepted().body(Map.of(
                "message", "Restart initiated",
                "instanceId", id.toString()
        ));
    }

    @DeleteMapping("/instances/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable UUID id,
            @RequestBody(required = false)
            Map<String, String> body) {

        String containerId = body != null
                ? body.get("containerId") : null;
        log.info("Delete request for instance {}", id);
        CompletableFuture.runAsync(() ->
                provisioningService.delete(id, containerId));

        return ResponseEntity.accepted().body(Map.of(
                "message", "Deletion initiated",
                "instanceId", id.toString()
        ));
    }

    @GetMapping("/supported-databases")
    public ResponseEntity<Map<String, Object>> supportedDatabases() {
        return ResponseEntity.ok(Map.of(
                "supported", providerFactory.getSupportedTypes()
        ));
    }
}