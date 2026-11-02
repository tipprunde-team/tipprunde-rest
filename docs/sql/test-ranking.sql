-- Beispielpunkte für die Rangliste (Postman-Fälle 35 und 36), nur für Testkonten mit Präfix TEST.
-- Voraussetzung: docs/rest-client/ranking.http Fall 1 bis 4 wurde gesendet, also existieren die Konten
-- TEST_rang_a bis TEST_rang_d und die Gruppe "TEST Rangliste" mit allen vier als Mitglieder,
-- und die Tabelle matches enthält beendete Spiele von Spieltag 6 (Seed-Skript).
-- Ergebnis: a = 7 Punkte (4 + 3, ein exakter Tipp), b = 3, c = 3, d = ohne Tipp = 0, also Rang 1, 2, 2, 4.
-- Ausführen mit SqlRunner.java (java -cp $jar "$env:USERPROFILE\SqlRunner.java" docs\sql\test-ranking.sql) oder in pgAdmin. Das Skript ist mehrfach ausführbar, es ersetzt die Tipps der Gruppe.

BEGIN;

DELETE FROM tips
WHERE group_id = (SELECT id FROM groups WHERE name = 'TEST Rangliste' ORDER BY id DESC LIMIT 1);

INSERT INTO tips (group_id, user_id, match_id, home_goals, away_goals, points)
SELECT g.id, u.id, m.id, 1, 0, v.points
FROM (VALUES ('TEST_rang_a', 1, 4), ('TEST_rang_a', 2, 3), ('TEST_rang_b', 1, 3), ('TEST_rang_c', 2, 3))
         AS v(username, n, points)
JOIN users u ON u.username = v.username
JOIN (SELECT id FROM groups WHERE name = 'TEST Rangliste' ORDER BY id DESC LIMIT 1) g ON TRUE
JOIN (SELECT id, row_number() OVER (ORDER BY kickoff_utc, id) AS n
      FROM matches WHERE matchday = 6 AND status = 'FINISHED' ORDER BY kickoff_utc, id LIMIT 2) m ON m.n = v.n;

COMMIT;
