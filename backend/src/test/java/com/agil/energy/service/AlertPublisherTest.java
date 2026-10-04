package com.agil.energy.service;

import com.agil.energy.entity.Alert;
import com.agil.energy.entity.Station;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.AlertType;
import com.agil.energy.enums.Severity;
import com.agil.energy.enums.StationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertPublisherTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private AlertEmailNotifier emailNotifier;
    @InjectMocks private AlertPublisher publisher;

    private Alert makeAlert() {
        Station station = new Station();
        station.setId(1L); station.setName("Station A");
        station.setRegion("Nord"); station.setStatus(StationStatus.ACTIVE);

        return Alert.builder()
                .id(10L)
                .station(station)
                .alertType(AlertType.STOCK_RUPTURE)
                .severity(Severity.HIGH)
                .message("Stock critique")
                .status(AlertStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("publish — broadcasts via WebSocket and notifies by email")
    void publish_broadcastsAndEmails() {
        Alert alert = makeAlert();

        publisher.publish(alert);

        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(Object.class));
        verify(emailNotifier).notifyNewAlert(alert);
    }

    @Test
    @DisplayName("publish — continues email even if WebSocket fails")
    void publish_wsFailure_stillEmails() {
        Alert alert = makeAlert();
        doThrow(new RuntimeException("WS down")).when(messagingTemplate)
                .convertAndSend(anyString(), any(Object.class));

        publisher.publish(alert);

        verify(emailNotifier).notifyNewAlert(alert);
    }

    @Test
    @DisplayName("publish — continues even if email fails")
    void publish_emailFailure_noException() {
        Alert alert = makeAlert();
        doThrow(new RuntimeException("SMTP down")).when(emailNotifier).notifyNewAlert(any());

        // Should not throw
        publisher.publish(alert);

        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(Object.class));
    }
}