package com.spawnbase.provisioning.dto;

import com.spawnbase.common.model.DatabaseType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProvisionRequest {

    @NotNull(message = "Database type is required")
    private DatabaseType dbType;

    @Size(min = 3, max = 32, message = "Username 3-32 chars")
    @Pattern(
            regexp = "^[a-z][a-z0-9_]*$|^$",
            message = "Username: lowercase letters, digits, underscores"
    )
    private String username;

    // Optional — auto-generated from instance UUID if blank
    private String password;

    // Optional — auto-generated from instance UUID if blank.
    // Only lowercase letters, digits, underscores allowed.
    // PostgreSQL/MySQL database name constraints.
    @Size(max = 63, message = "Database name max 63 chars")
    @Pattern(
            regexp = "^[a-z][a-z0-9_]*$|^$",
            message = "Database name: lowercase letters, " +
                    "digits, underscores only, start with letter"
    )
    private String dbName;
}