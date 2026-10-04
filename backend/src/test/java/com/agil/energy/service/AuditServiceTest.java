package com.agil.energy.service;

import com.agil.energy.entity.AuditLog;
import com.agil.energy.entity.User;
import com.agil.energy.repository.AuditLogRepository;
import com.agil.energy.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AuditService auditService;

    @Test
    @DisplayName("log — saves audit entry with user when userId found")
    void log_withUser() {
        User user = new User(); user.setId(1L); user.setName("Admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        auditService.log(1L, "CREATE", "STATION", 5L, "{\"name\":\"S1\"}", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getEntity()).isEqualTo("STATION");
        assertThat(saved.getEntityId()).isEqualTo(5L);
        assertThat(saved.getDetails()).isEqualTo("{\"name\":\"S1\"}");
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("log — saves audit entry with null user when userId is null")
    void log_nullUserId() {
        auditService.log(null, "SYSTEM_CHECK", "HEALTH", null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getAction()).isEqualTo("SYSTEM_CHECK");
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("log — saves with null user when userId not found in DB")
    void log_userNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        auditService.log(999L, "DELETE", "USER", 10L, null, "10.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getAction()).isEqualTo("DELETE");
    }
}