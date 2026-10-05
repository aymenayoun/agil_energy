-- ============================================================
-- Demo accounts
-- ============================================================
-- Runs after init.sql (files are executed in alphabetical order).
--
-- Gives the three demo accounts a shared, known password and the
-- role scoping the application expects:
--
--   demo.admin@agil.tn     ADMIN            toutes les stations
--   demo.manager@agil.tn   MANAGER          region Tunis
--   demo.station@agil.tn   STATION_MANAGER  Station La Marsa (id 2)
--
-- Mot de passe commun : Admin@2026
--
-- Identifiants jetables pour une base locale de donnees synthetiques.
-- A changer avant toute exposition reseau.
-- ============================================================

USE agil_energy;

-- Same bcrypt hash for all three, so one password opens every role.
SET @demo_hash := (SELECT password_hash FROM users WHERE email = 'demo.admin@agil.tn');

UPDATE users
SET password_hash = @demo_hash
WHERE email IN ('demo.manager@agil.tn', 'demo.station@agil.tn');

-- Scoping: MANAGER sees one region, STATION_MANAGER sees one station.
UPDATE users SET region = NULL,    station_id = NULL WHERE email = 'demo.admin@agil.tn';
UPDATE users SET region = 'Tunis', station_id = NULL WHERE email = 'demo.manager@agil.tn';
UPDATE users SET region = NULL,    station_id = 2    WHERE email = 'demo.station@agil.tn';
