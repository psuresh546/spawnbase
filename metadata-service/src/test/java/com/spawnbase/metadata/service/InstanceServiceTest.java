package com.spawnbase.metadata.service;

import com.spawnbase.common.model.InstanceState;
import com.spawnbase.common.model.DatabaseType;
import com.spawnbase.metadata.dto.CreateInstanceRequest;
import com.spawnbase.metadata.dto.InstanceResponse;
import com.spawnbase.metadata.dto.UpdateStateRequest;
import com.spawnbase.metadata.entity.Instance;
import com.spawnbase.metadata.event.InstanceEventPublisher;
import com.spawnbase.metadata.exception.InstanceNotFoundException;
import com.spawnbase.metadata.repository.InstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstanceServiceTest {

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private InstanceEventPublisher eventPublisher; // ← ADDED

    @InjectMocks
    private InstanceService instanceService;

    private UUID instanceId;
    private Instance testInstance;

    @BeforeEach
    void setUp() {
        instanceId = UUID.randomUUID();

        testInstance = Instance.builder()
                .id(instanceId)
                .name("test-db")
                .dbType(DatabaseType.POSTGRESQL)
                .state(InstanceState.REQUESTED)
                .ownerId("user-001")
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createInstance — saves with REQUESTED state")
    void createInstance_saves_with_requested_state() {
        // ARRANGE
        CreateInstanceRequest request = new CreateInstanceRequest();
        request.setName("test-db");
        request.setDbType(DatabaseType.POSTGRESQL);
        request.setOwnerId("user-001");

        when(instanceRepository.save(any(Instance.class)))
                .thenReturn(testInstance);

        // ACT
        InstanceResponse response =
                instanceService.createInstance(request);

        // ASSERT
        assertThat(response.getState())
                .isEqualTo(InstanceState.REQUESTED);
        assertThat(response.getName()).isEqualTo("test-db");
        assertThat(response.getDbType())
                .isEqualTo(DatabaseType.POSTGRESQL);

        verify(instanceRepository, times(1))
                .save(any(Instance.class));

        // Verify event was published
        verify(eventPublisher, times(1))
                .publishCreated(any(), any(), any());
    }

    @Test
    @DisplayName("getInstance — returns response when found")
    void getInstance_returns_when_found() {
        // ARRANGE
        when(instanceRepository.findById(instanceId))
                .thenReturn(Optional.of(testInstance));

        // ACT
        InstanceResponse response =
                instanceService.getInstance(instanceId);

        // ASSERT
        assertThat(response.getId()).isEqualTo(instanceId);
        assertThat(response.getName()).isEqualTo("test-db");
    }

    @Test
    @DisplayName("getInstance — throws InstanceNotFoundException when not found")
    void getInstance_throws_when_not_found() {
        // ARRANGE
        when(instanceRepository.findById(instanceId))
                .thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThatThrownBy(() ->
                instanceService.getInstance(instanceId))
                .isInstanceOf(InstanceNotFoundException.class)
                .hasMessageContaining(instanceId.toString());
    }

    @Test
    @DisplayName("updateState — updates state correctly")
    void updateState_updates_correctly() {
        // ARRANGE
        UpdateStateRequest request = new UpdateStateRequest();
        request.setState(InstanceState.PROVISIONING);

        Instance updatedInstance = Instance.builder()
                .id(instanceId)
                .name("test-db")
                .dbType(DatabaseType.POSTGRESQL)
                .state(InstanceState.PROVISIONING)
                .ownerId("user-001")
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(instanceRepository.findById(instanceId))
                .thenReturn(Optional.of(testInstance));
        when(instanceRepository.save(any(Instance.class)))
                .thenReturn(updatedInstance);

        // ACT
        InstanceResponse response =
                instanceService.updateState(instanceId, request);

        // ASSERT
        assertThat(response.getState())
                .isEqualTo(InstanceState.PROVISIONING);

        // Verify event was published
        verify(eventPublisher, times(1))
                .publishStateChanged(any(), any(), any(),
                        any(), any());
    }

    @Test
    @DisplayName("updateState — throws when instance not found")
    void updateState_throws_when_not_found() {
        // ARRANGE
        UpdateStateRequest request = new UpdateStateRequest();
        request.setState(InstanceState.PROVISIONING);

        when(instanceRepository.findById(instanceId))
                .thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThatThrownBy(() ->
                instanceService.updateState(instanceId, request))
                .isInstanceOf(InstanceNotFoundException.class);

        verify(instanceRepository, never()).save(any());

        // Verify event was NOT published
        verify(eventPublisher, never())
                .publishStateChanged(any(), any(), any(),
                        any(), any());
    }
}