-- AT-02-10 Obergrenze 200 Mitglieder (FA-23.1), Testdaten für die gemeinsame Datenbank
-- Ablauf: Teil A ausführen, dann die zwei Beitritte in abnahme-uc02.http senden, danach sofort Teil B
-- Alles mit Präfix TEST; die Testkonten haben keinen gültigen Passwort-Hash und können sich nicht anmelden


-- ===== Teil A: Testgruppe mit 199 Mitgliedern anlegen (idempotent) =====
BEGIN;

INSERT INTO groups (name, invite_code)
VALUES ('TEST UC02 Obergrenze', 'TESTLIMIT200')
ON CONFLICT (invite_code) DO NOTHING;

INSERT INTO users (email, username, password_hash)
SELECT 'test-limit-' || n || '@example.de', 'TEST_limit_' || n, 'kein-login'
FROM generate_series(1, 199) AS n
ON CONFLICT DO NOTHING;

INSERT INTO group_members (group_id, user_id, role)
SELECT g.id, u.id,
       CASE WHEN u.email = 'test-limit-1@example.de' THEN 'ADMIN' ELSE 'MEMBER' END
FROM groups g
JOIN users u ON u.email LIKE 'test-limit-%@example.de'
WHERE g.invite_code = 'TESTLIMIT200'
ON CONFLICT (group_id, user_id) DO NOTHING;

COMMIT;

-- Kontrolle: erwartet mitglieder = 199
SELECT g.id, g.name, COUNT(gm.user_id) AS mitglieder
FROM groups g
LEFT JOIN group_members gm ON gm.group_id = g.id
WHERE g.invite_code = 'TESTLIMIT200'
GROUP BY g.id, g.name;


-- ===== Teil B: Aufräumen nach dem Test =====
-- Löscht die Obergrenze-Gruppe, alle Gruppen der UC-02-Testkonten und alle Testkonten aus UC-02
-- Mitgliedschaften und Tipps verschwinden per ON DELETE CASCADE mit
-- BEGIN;
--
-- DELETE FROM groups
-- WHERE invite_code = 'TESTLIMIT200'
--    OR id IN (SELECT gm.group_id
--              FROM group_members gm
--              JOIN users u ON u.id = gm.user_id
--              WHERE gm.role = 'ADMIN'
--                AND (u.email LIKE 'test-uc02-%@example.de' OR u.email LIKE 'test-limit-%@example.de'));
--
-- DELETE FROM users
-- WHERE email LIKE 'test-uc02-%@example.de'
--    OR email LIKE 'test-limit-%@example.de';
--
-- COMMIT;
--
-- Kontrolle: erwartet zweimal 0
-- SELECT COUNT(*) FROM groups WHERE invite_code = 'TESTLIMIT200' OR name LIKE 'TEST UC02%';
-- SELECT COUNT(*) FROM users WHERE email LIKE 'test-uc02-%@example.de' OR email LIKE 'test-limit-%@example.de';