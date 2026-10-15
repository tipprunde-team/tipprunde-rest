# tipprunde-rest

Backend der Fußball-Tippseite (Spring Boot 4.1, Gradle Groovy, Java 17 bis 24, PostgreSQL bei aiven.io).

## Setup unter Windows

Voraussetzung: JDK 17 oder neuer (`java -version`). Gradle muss nicht installiert werden, der Wrapper `gradlew.bat` lädt es selbst.

### Umgebungsvariablen

Zugangsdaten stehen nie im Code oder Repo. Sie werden pro Person in der Eingabeaufforderung gesetzt (`cmd`):

```
setx DB_URL "jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require"
setx DB_USER "avnadmin"
setx DB_PASSWORD "<Passwort aus der Privatnachricht>"
setx JWT_SECRET "<gemeinsames Secret aus der Privatnachricht>"
```

Danach das Terminal bzw. IntelliJ neu starten, damit die Variablen ankommen. Prüfen in `cmd` mit `echo %DB_URL%`, in PowerShell mit `$env:DB_URL`.

Die aiven-Service-URI sieht so aus: `postgres://USER:PASSWORD@HOST:PORT/defaultdb?sslmode=require`. Für `DB_URL` wird daraus nur `jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require`. Benutzer und Passwort kommen getrennt in `DB_USER` und `DB_PASSWORD`.

`JWT_SECRET` signiert die Login-Tokens und muss mindestens 32 Zeichen lang sein. Alle im Team verwenden denselben Wert. Eine Person erzeugt ihn einmal (PowerShell) und teilt ihn privat:

```powershell
$b = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b)
```

Optional:

| Variable | Bedeutung | Standard |
|---|---|---|
| `PORT` | Port des Backends | `8080` |
| `CORS_ALLOWED_ORIGINS` | erlaubte Frontend-Origins, kommagetrennt | `http://localhost:5500,http://127.0.0.1:5500` |
| `FOOTBALL_DATA_TOKEN` | API-Key von football-data.org (später für den Spielplan-Import) | – |

Fehlen `DB_URL`, `DB_USER` oder `DB_PASSWORD`, startet das Backend nicht. Typische Meldung: `'url' must start with "jdbc"`. Fehlt `JWT_SECRET` oder ist es zu kurz, bricht der Start mit einer Meldung zu `JWT_SECRET` ab.

### Starten und testen

```
cd %USERPROFILE%\Desktop\tipprunde-wi25a\tipprunde-rest
gradlew.bat bootRun
gradlew.bat test
```

In PowerShell stattdessen `.\gradlew.bat bootRun`.

Im Browser prüfen:

- http://localhost:8080/api/ping liefert `{"status":"ok"}`
- http://localhost:8080/actuator/health liefert `{"status":"UP"}` (bei nicht erreichbarer Datenbank `DOWN`)

Die API-Aufrufe zum Testen liegen für die VS-Code-Erweiterung REST Client in `docs/rest-client/`.

## Datenbankschema auf aiven einspielen (pgAdmin)

Das Schema liegt in `src/main/resources/db/schema.sql` und wird **nicht** automatisch beim Start ausgeführt. Es ist idempotent, kann also mehrfach laufen, ohne etwas kaputtzumachen (`CREATE ... IF NOT EXISTS`). Es legt `users`, `groups`, `group_members`, `matches` und `tips` an. Eine Token-Tabelle gibt es nicht, weil der Login über JWT zustandslos ist.

1. In pgAdmin per Rechtsklick auf "Servers" den Server registrieren (Register > Server). Name z. B. `tipprunde-aiven`.
2. Reiter "Connection": Host, Port, Maintenance database `defaultdb`, Username und Passwort aus der Privatnachricht eintragen.
3. Reiter "SSL": SSL mode auf `require` stellen. Speichern.
4. Unter dem Server die Datenbank `defaultdb` markieren und das Query Tool öffnen (Tools > Query Tool).
5. Dort `schema.sql` über das Ordner-Symbol laden und mit F5 ausführen. Es darf keine rote Fehlermeldung kommen.
6. Prüfen: Diese Abfrage muss genau die fünf Tabellen liefern.

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' ORDER BY table_name;
```

Erwartet: `group_members`, `groups`, `matches`, `tips`, `users`.

## Registrierung

`POST /api/users` legt einen Nutzer an und braucht kein Token. Eingaben und Fehler:

| Feld | Regel |
|---|---|
| `email` | gültige Adresse, höchstens 254 Zeichen, wird getrimmt und klein gespeichert |
| `username` | 3 bis 30 Zeichen aus Buchstaben (A bis Z), Ziffern, `_`, `.` und `-` |
| `password` | 8 bis 72 Zeichen, höchstens 72 Bytes (Umlaute zählen doppelt) |

Antwort bei Erfolg: `201` mit `id`, `email` und `username`. Fehler: `400 VALIDATION_ERROR`, `409 EMAIL_TAKEN`, `409 USERNAME_TAKEN`. Das Passwort wird mit BCrypt gehasht gespeichert. Die Aufrufe zum Testen stehen in `docs/rest-client/users.http`.

## Geschützte Endpunkte und Token

Alle Endpunkte unter `/api` brauchen ein Token im Header `Authorization: Bearer <token>`, außer diese vier:

| Methode | Pfad |
|---|---|
| `POST` | `/api/users` (Registrierung) |
| `POST` | `/api/auth` (Login) |
| `GET` | `/api/ping` |
| `GET` | `/api/invites/**` (Einladungsvorschau, folgt) |

Die Methode zählt: `DELETE /api/auth` (Logout) ist geschützt. Fehlt das Token oder ist es ungültig oder abgelaufen, antwortet das Backend mit `401 UNAUTHORIZED` und der Meldung "Bitte melde dich erneut an.". Ein Token in der URL zählt nicht. CORS-Preflight-Anfragen (`OPTIONS`) laufen immer durch.

Die Nutzer-ID kommt ausschließlich aus dem geprüften Token, nie aus Body oder URL. In Controllern gibt es sie über den Parameter `@CurrentUserId long userId`. Der Logout ist zustandslos: `DELETE /api/auth` antwortet mit 204, das Token bleibt bis zum Ablauf technisch gültig, das Frontend verwirft es.

`GET /api/users/me` liefert das eigene Profil `{id, email, username, isSystemAdmin}`. Alle Aufrufe mit Fehlerfällen stehen in `docs/rest-client/alle-endpoints.http`.

## Frontend lokal

Das Frontend (Repo `tipprunde-web`) wird lokal auf Port 5500 ausgeliefert, z. B. mit der VS-Code-Erweiterung Live Server. Andere Origins lassen sich über `CORS_ALLOWED_ORIGINS` freigeben.

## Team

Max Schramberger und Arlind Hulaj (Backend), Maximilian Göllnitz (PO), Niklas Schusser (SM)
