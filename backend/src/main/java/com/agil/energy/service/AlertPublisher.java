package com.agil.energy.service;

import com.agil.energy.dto.response.AlertEvent;
import com.agil.energy.entity.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final AlertEmailNotifier emailNotifier;

    public void publish(Alert alert) {
        try {
            AlertEvent event = AlertEvent.from(alert);
            messagingTemplate.convertAndSend("/topic/alerts", event);
            log.debug("Broadcasted alert id={} type={}", event.getId(), event.getAlertType());
        } catch (Exception e) {
            log.warn("Failed to broadcast alert {}: {}", alert.getId(), e.getMessage());
        }
        // Async — won't slow the publishing path
        try {
            emailNotifier.notifyNewAlert(alert);
        } catch (Exception e) {
            log.warn("Failed to enqueue alert email {}: {}", alert.getId(), e.getMessage());
        }
    }
}