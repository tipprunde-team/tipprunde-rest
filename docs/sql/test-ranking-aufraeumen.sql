-- Raeumt die Testdaten der Rangliste (Gruppe "TEST Rangliste", Konten TEST_rang_a bis TEST_rang_e) wieder weg.
-- Loescht nur Daten mit dem Praefix TEST. Mitgliedschaften und Tipps verschwinden per Kaskade mit.

BEGIN;

DELETE FROM groups WHERE name = 'TEST Rangliste';
DELETE FROM users WHERE username LIKE 'TEST\_rang\_%';

COMMIT;

-- Kontrolle: beide Zahlen muessen 0 sein
SELECT (SELECT COUNT(*) FROM groups WHERE name = 'TEST Rangliste') AS gruppen,
       (SELECT COUNT(*) FROM users WHERE username LIKE 'TEST\_rang\_%') AS konten;
