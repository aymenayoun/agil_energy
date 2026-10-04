package com.agil.energy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklisted_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JWT id claim ("jti") of the revoked access token. */
    @Column(nullable = false, unique = true, length = 64)
    private String jti;

    @Column(name = "user_id")
    private Long userId;

    /** When the original token would have expired (used to clean up the table). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    @Column(length = 50)
    private String reason;
}