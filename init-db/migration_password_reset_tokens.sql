-- ============================================================
-- Migration: password_reset_tokens
-- Adds the table backing the "forgot password" flow.
-- Safe to run against an existing database (IF NOT EXISTS).
-- Mirrors the structure/conventions of otp_tokens & blacklisted_tokens.
-- ============================================================

CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `selector` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` datetime NOT NULL,
  `used` bit(1) NOT NULL DEFAULT b'0',
  `attempts` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pwreset_selector` (`selector`),
  KEY `idx_pwreset_user` (`user_id`),
  KEY `idx_pwreset_expires` (`expires_at`),
  CONSTRAINT `fk_pwreset_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
