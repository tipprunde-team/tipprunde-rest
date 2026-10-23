-- Testnutzer für den Login gegen die Datenbank
-- E-Mail test@tippseite.de, Benutzername tester; der Hash gehört zu einem alten Startpasswort.
-- Das aktuelle Passwort steht nicht im Repo, es wird nur privat im Team weitergegeben.
-- Mehrfach ausführbar: legt den Nutzer nur an, wenn die E-Mail noch nicht existiert
INSERT INTO users (email, username, password_hash)
VALUES ('test@tippseite.de', 'tester', '$2a$10$sxFr/68udlkAhIag4l3CNusQrs70cxXq4iFfcALlsRQoBMUpT4Dxa')
ON CONFLICT (email) DO NOTHING;

-- Kontrolle: Hash muss mit $2a$ beginnen, nie Klartext
SELECT id, email, username, LEFT(password_hash, 7) AS hash_start FROM users;