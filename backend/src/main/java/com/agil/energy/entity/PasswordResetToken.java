package com.agil.energy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Single-use, time-limited token backing the "forgot password" flow.
 *
 * Mirrors {@link OtpToken}: the client only ever receives an opaque random
 * token by email; we persist a BCrypt hash of it so a database read cannot
 * reveal a usable token.
 */
@Entity
@Table(name = "password_reset_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public selector returned to the client (and embedded in the reset link).
     * Lets us look the row up by an indexed column without exposing the secret.
     */
    @Column(name = "selector", nullable = false, unique = true, length = 64)
    private String selector;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** BCrypt hash of the secret verifier (the part of the token that is emailed but never stored in clear). */
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
