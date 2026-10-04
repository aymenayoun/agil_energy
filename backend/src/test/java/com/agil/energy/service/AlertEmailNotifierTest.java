package com.agil.energy.service;

import com.agil.energy.entity.Alert;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.User;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.AlertType;
import com.agil.energy.enums.Severity;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEmailNotifierTest {

    @Mock private EmailService emailService;
    @Mock private UserRepository userRepository;

    @InjectMocks private AlertEmailNotifier notifier;

    private Alert alert;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notifier, "enabled", true);
        ReflectionTestUtils.setField(notifier, "extraRecipientsCsv", "");

        Station st = new Station(); st.setId(1L); st.setName("Station Tunis");
        alert = Alert.builder()
                .station(st)
                .alertType(AlertType.STOCK_RUPTURE)
                .severity(Severity.HIGH)
                .message("Stock critique")
                .status(AlertStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("notifyNewAlert — disabled globally is no-op")
    void notifyNewAlert_disabled_noOp() {
        ReflectionTestUtils.setField(notifier, "enabled", false);

        notifier.notifyNewAlert(alert);

        verifyNoInteractions(emailService, userRepository);
    }

    @Test
    @DisplayName("notifyNewAlert — non-ACTIVE alert is skipped")
    void notifyNewAlert_resolved_skipped() {
        alert.setStatus(AlertStatus.RESOLVED);

        notifier.notifyNewAlert(alert);

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("notifyNewAlert — sends to active admins")
    void notifyNewAlert_sendsToAdmins() {
        User admin1 = new User(); admin1.setEmail("a1@x.com"); admin1.setStatus(UserStatus.ACTIVE);
        User admin2 = new User(); admin2.setEmail("a2@x.com"); admin2.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByRoleName("ADMIN")).thenReturn(List.of(admin1, admin2));

        notifier.notifyNewAlert(alert);

        verify(emailService).sendHtml(eq("a1@x.com"), anyString(), eq("alert-email"), anyMap());
        verify(emailService).sendHtml(eq("a2@x.com"), anyString(), eq("alert-email"), anyMap());
    }

    @Test
    @DisplayName("notifyNewAlert — filters out inactive admins")
    void notifyNewAlert_skipsInactiveAdmins() {
        User active = new User(); active.setEmail("a@x.com"); active.setStatus(UserStatus.ACTIVE);
        User inactive = new User(); inactive.setEmail("b@x.com"); inactive.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByRoleName("ADMIN")).thenReturn(List.of(active, inactive));

        notifier.notifyNewAlert(alert);

        verify(emailService).sendHtml(eq("a@x.com"), anyString(), anyString(), anyMap());
        verify(emailService, never()).sendHtml(eq("b@x.com"), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("notifyNewAlert — appends extra recipients from CSV")
    void notifyNewAlert_addsExtraRecipients() {
        ReflectionTestUtils.setField(notifier, "extraRecipientsCsv", "manager@x.com, ops@x.com");
        when(userRepository.findByRoleName("ADMIN")).thenReturn(Collections.emptyList());

        notifier.notifyNewAlert(alert);

        verify(emailService).sendHtml(eq("manager@x.com"), anyString(), anyString(), anyMap());
        verify(emailService).sendHtml(eq("ops@x.com"), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("notifyNewAlert — no recipients sends no email")
    void notifyNewAlert_noRecipients_noEmail() {
        when(userRepository.findByRoleName("ADMIN")).thenReturn(Collections.emptyList());

        notifier.notifyNewAlert(alert);

        verifyNoInteractions(emailService);
    }
}