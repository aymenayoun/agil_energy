package com.agil.energy.service;

import com.agil.energy.entity.Alert;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEmailNotifier {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmailService emailService;
    private final UserRepository userRepository;

    @Value("${app.alert.email.enabled}")
    private boolean enabled;

    @Value("${app.alert.email.recipients}")
    private String extraRecipientsCsv;

    @Async
    public void notifyNewAlert(Alert alert) {
        if (!enabled) return;
        // Don't notify when an alert transitions to RESOLVED
        if (alert.getStatus() != null && !"ACTIVE".equals(alert.getStatus().name())) return;

        Set<String> recipients = resolveRecipients();
        if (recipients.isEmpty()) {
            log.debug("Alert email enabled but no recipients found");
            return;
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("alertType", alert.getAlertType().name());
        vars.put("severity", alert.getSeverity().name());
        vars.put("stationName", alert.getStation() != null ? alert.getStation().getName() : "—");
        vars.put("message", alert.getMessage());
        vars.put("createdAt", alert.getCreatedAt() != null ? alert.getCreatedAt().format(FMT) : "");

        String subject = String.format("[AGIL] Nouvelle alerte %s — %s",
                alert.getSeverity().name(), alert.getAlertType().name());

        for (String to : recipients) {
            emailService.sendHtml(to, subject, "alert-email", vars);
        }
    }

    private Set<String> resolveRecipients() {
        Stream<String> admins = userRepository.findByRoleName("ADMIN").stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(User::getEmail);

        Stream<String> extras = (extraRecipientsCsv == null || extraRecipientsCsv.isBlank())
                ? Stream.empty()
                : Arrays.stream(extraRecipientsCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty());

        return Stream.concat(admins, extras).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}