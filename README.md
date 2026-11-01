# tipprunde-rest

[![CI](https://github.com/tipprunde-team/tipprunde-rest/actions/workflows/ci.yml/badge.svg)](https://github.com/tipprunde-team/tipprunde-rest/actions/workflows/ci.yml)

Backend der Fußball-Tippseite (Spring Boot 4.1, Gradle Groovy, Java 17 bis 24, PostgreSQL bei aiven.io).

## Live-Version

| Was | Adresse |
|---|---|
| Frontend (GitHub Pages) | https://tipprunde-team.github.io/tipprunde-web/ |
| Backend-API (Heroku) | https://tipprunde-rest-b06734b76aad.herokuapp.com/api |
| Backend-Status | https://tipprunde-rest-b06734b76aad.herokuapp.com/actuator/health |

Das Backend läuft auf einem Eco-Dyno und schläft nach 30 Minuten ohne Aufruf ein. Der erste Aufruf danach dauert einige Sekunden. Die Aufrufe zum Testen gegen das Live-Backend stehen in `docs/rest-client/online.http`.

## Setup unter Windows

Voraussetzung: JDK 17 oder neuer (`java -version`). Gradle muss nicht installiert werden, der Wrapper `gradlew.bat` lädt es selbst.

### Umgebungsvariablen

Zugangsdaten stehen nie im Code oder Repo. Sie werden pro Person einmalig in der Eingabeaufforderung gesetzt (`cmd`, über das Startmenü geöffnet).

**Schritt 1: DB_URL aus der aiven-Service-URI bauen.** Die Service-URI aus der Privatnachricht sieht so aus:

```
postgres://avnadmin:PASSWORT@tipprunde-beispiel.aivencloud.com:12345/defaultdb?sslmode=require
```

Für `DB_URL` davor `jdbc:` setzen, aus `postgres` wird `postgresql`, und Benutzer und Passwort (alles zwischen `//` und `@`) entfallen:

```
jdbc:postgresql://tipprunde-beispiel.aivencloud.com:12345/defaultdb?sslmode=require
```

**Schritt 2: Die vier Variablen setzen.** Alles in Großbuchstaben bzw. spitzen Klammern durch die echten Werte ersetzen, die Klammern selbst entfallen:

```
setx DB_URL "jdbc:postgresql://<HOST>:<PORT>/defaultdb?sslmode=require"
setx DB_USER "<Benutzer, meist avnadmin>"
setx DB_PASSWORD "<Passwort aus der Privatnachricht>"
setx JWT_SECRET "<gemeinsames Secret aus der Privatnachricht>"
```

**Schritt 3: Neues Terminal öffnen.** `setx` wirkt nur in Fenstern, die danach geöffnet werden. Das aktuelle Fenster schließen und ein neues über das Startmenü öffnen. Prüfen mit `echo %DB_URL%` (cmd) bzw. `$env:DB_URL` (PowerShell): Es muss die echte Adresse erscheinen, kein `HOST` oder `PORT`.

Gespeicherte Werte lassen sich unabhängig vom Fenster prüfen mit `reg query HKCU\Environment`.

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
| `DB_POOL_SIZE` | höchstens so viele Datenbankverbindungen je Instanz (alle teilen sich eine Free-Datenbank) | `3` |
| `FOOTBALL_DATA_TOKEN` | API-Key von football-data.org (später für den Spielplan-Import) | – |
| `DB_STARTUP_CHECK` | Verbindung zur Datenbank beim Start prüfen | `true` |
| `LOG_LEVEL` | Log-Level der Anwendung (`INFO`, `DEBUG`, ...), siehe Abschnitt Logging | `INFO` |
| `QUEUE_URL` | AMQP-URL der CloudAMQP-Instanz (`amqps://…`), schaltet den Empfang der Spiele aus dem Match-Service ein, siehe Abschnitt Spiele aus der Queue | leer, Empfang aus |

Beim Start prüft das Backend die Konfiguration (`StartupConfigCheck`): Fehlt eine Variable, steht noch ein Platzhalter darin oder ist die Datenbank nicht erreichbar, bricht der Start mit einer Zeile `Configuration error: …` bzw. `Database not reachable at …` ab. Läuft alles, steht im Log `Database connection OK`. Fehlt `JWT_SECRET` oder ist es kürzer als 32 Zeichen, bricht der Start ebenfalls ab. Die Verbindungsprüfung lässt sich bei Bedarf mit `DB_STARTUP_CHECK=false` abschalten.

### Starten und testen

```
cd "<Pfad zum Repo>\tipprunde-rest"
gradlew.bat bootRun
gradlew.bat test
```

In PowerShell stattdessen `.\gradlew.bat bootRun`.

Im Browser prüfen:

- http://localhost:8080/api/ping liefert `{"status":"ok"}`
- http://localhost:8080/actuator/health liefert `{"status":"UP"}` (bei nicht erreichbarer Datenbank `DOWN`)

Die API-Aufrufe zum Testen liegen für die VS-Code-Erweiterung REST Client in `docs/rest-client/`.

Das Passwort des gemeinsamen Testkontos `test@tippseite.de` steht nicht im Repo. Die `.http`-Dateien lesen es mit `{{$processEnv TEST_PASSWORD}}` aus einer Windows-Variable. Einmalig in PowerShell setzen (Passwort aus der Privatnachricht bei der Abfrage einfügen) und danach VS Code komplett neu starten:

```powershell
$s = Read-Host "Testkonto-Passwort" -AsSecureString
[Environment]::SetEnvironmentVariable("TEST_PASSWORD", [Net.NetworkCredential]::new("", $s).Password, "User")
```

### Fehlerbehebung

| Symptom | Ursache | Lösung |
|---|---|---|
| `Configuration error: DB_URL still contains the placeholder …` | In `DB_URL` steht noch `HOST`, `PORT` oder `<…>` aus dieser README | DB_URL wie oben aus der Service-URI bauen und mit `setx` neu setzen, dann neues Terminal |
| `echo %DB_URL%` zeigt nach `setx` noch den alten Wert | Das Terminal wurde über GitHub Desktop, VS Code oder IntelliJ geöffnet, und das Programm lief schon vor `setx` | Terminal über das Startmenü öffnen oder das Programm komplett neu starten |
| `Database not reachable at …` | Falsches Passwort, falscher Host oder aiven lässt die eigene IP nicht zu | Werte mit `reg query HKCU\Environment` prüfen; bei IP-Filter in aiven die eigene IP freigeben lassen |
| `Port 8080 was already in use` | Ein altes Backend läuft noch | `gradlew.bat --stop`, dann `netstat -ano \| findstr :8080` und `taskkill /PID <PID> /F` |
| `Strg + C` beendet das Backend nicht | Das Fenster reagiert nicht | Fenster schließen, danach wie bei Port 8080 vorgehen |
| Login im Browser meldet „interner Fehler“ | Fehler im Backend, Details stehen im Backend-Fenster | Im Backend-Fenster nach `ERROR` suchen, die Zeile darunter nennt die Ursache |

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
| `GET` | `/api/invites/**` (Einladungsvorschau) |

Die Methode zählt: `DELETE /api/auth` (Logout) ist geschützt. Fehlt das Token oder ist es ungültig oder abgelaufen, antwortet das Backend mit `401 UNAUTHORIZED` und der Meldung "Bitte melde dich erneut an.". Ein Token in der URL zählt nicht. CORS-Preflight-Anfragen (`OPTIONS`) laufen immer durch.

Die Nutzer-ID kommt ausschließlich aus dem geprüften Token, nie aus Body oder URL. In Controllern gibt es sie über den Parameter `@CurrentUserId long userId`. Der Logout ist zustandslos: `DELETE /api/auth` antwortet mit 204, das Token bleibt bis zum Ablauf technisch gültig, das Frontend verwirft es.

`GET /api/users/me` liefert das eigene Profil `{id, email, username, isSystemAdmin}`. Alle Aufrufe mit Fehlerfällen stehen in `docs/rest-client/alle-endpoints.http`.

## Tippgruppen

`POST /api/groups` und `GET /api/groups` brauchen ein Token (siehe oben). Die Nutzer-ID kommt aus dem Token.

| Methode | Pfad | Antwort |
|---|---|---|
| `POST` | `/api/groups` | `201` mit `{id, name, inviteCode, role}`, der Ersteller ist `ADMIN` |
| `GET` | `/api/groups` | `200` mit der Liste der eigenen Gruppen `{id, name, role, memberCount, myRank, myPoints}` |
| `POST` | `/api/groups/join` | `200` mit dem Gruppeneintrag wie in `GET /api/groups` (`role: MEMBER`), Body `{inviteCode}` |
| `GET` | `/api/invites/{inviteCode}` | `200` mit `{groupId, groupName, memberCount}`, ohne Token (`404 INVITE_INVALID`) |
| `GET` | `/api/groups/{groupId}` | `200` mit `{id, name, inviteCode, members[{userId, username, role, joinedAt}]}`, nur für Mitglieder (`403 NOT_A_MEMBER`, `404 GROUP_NOT_FOUND`) |

Der Name wird getrimmt und muss 1 bis 50 Zeichen lang sein (sonst `400 VALIDATION_ERROR`). Der Einladungscode hat 10 Zeichen aus einem URL-sicheren Alphabet ohne leicht verwechselbare Zeichen (ohne `0`, `O`, `1`, `I`, `l`). Gruppe und Admin-Mitgliedschaft werden in einer Transaktion angelegt, bei einer Kollision des Codes wird ein neuer erzeugt. Fehlende Punkte zählen als 0, der Rang ergibt sich per `RANK()` aus den Punkten innerhalb der Gruppe (gleiche Punkte, gleicher Rang).

Der `inviteCode` steht in den Gruppendetails nur für Admins in der Antwort, bei normalen Mitgliedern fehlt das Feld. Die Mitglieder sind sortiert: Admin zuerst, dann nach Beitrittszeit. Der Beitritt prüft Code, Mitgliedschaft und Größe in einer Transaktion. Die Gruppenzeile wird dafür gesperrt, deshalb überschreiten auch gleichzeitige Beitritte die Grenze nicht. Fehler: `404 INVITE_INVALID`, `409 ALREADY_MEMBER` (hat Vorrang, auch bei voller Gruppe), `409 GROUP_FULL` ab 200 Mitgliedern, `401` ohne Token. Wer schon Mitglied ist, findet die Gruppe über die `groupId` aus der Einladungsvorschau. Eine Gruppe darf höchstens 200 Mitglieder haben (FA-23.1).

Für alle Pfade unter `/api/groups/{groupId}/**` prüft `GroupAccess` zentral die Mitgliedschaft: `requireMember` antwortet mit `404 GROUP_NOT_FOUND` oder `403 NOT_A_MEMBER`, `requireAdmin` zusätzlich mit `403 NOT_GROUP_ADMIN`. Die Aufrufe zum Testen stehen in `docs/rest-client/groups.http`.

## Frontend lokal

Das Frontend (Repo `tipprunde-web`) wird lokal auf Port 5500 ausgeliefert, z. B. mit der VS-Code-Erweiterung Live Server. Andere Origins lassen sich über `CORS_ALLOWED_ORIGINS` freigeben.

## Continuous Integration

Bei jedem Pull Request und jedem Push auf `main` baut GitHub Actions das Projekt und führt alle Tests aus (`.github/workflows/ci.yml`). Gemergt wird nur mit grünem Status-Check **CI**.

Die CI hat keinen Zugriff auf die aiven-Datenbank. Neue Tests deshalb ohne Datenbank schreiben (Standalone-MockMvc oder Platzhalter-Manager wie in den bestehenden Tests).

Testbericht: Actions › Lauf öffnen › Artifacts › `test-reports`

Lokal testen: `.\gradlew.bat test`

### Testabdeckung (JaCoCo)

`./gradlew test` erzeugt automatisch einen Abdeckungsbericht mit JaCoCo (Zeilen- und Verzweigungsabdeckung).

- Lokal: `build/reports/jacoco/test/html/index.html` im Browser öffnen
- In der CI: Der Bericht liegt nach jedem Lauf als Artefakt `jacoco-report` an der Workflow-Ausführung (Actions → Lauf → Artifacts)
- Der Bericht ist eine Messung und kein Grenzwert. Der Build schlägt bei niedriger Abdeckung nicht fehl.

## Logging

Das Backend schreibt sicherheitsrelevante Ereignisse in die Konsole (NFA-24). Lokal steht das im Fenster von `bootRun`, auf Heroku im Dashboard unter **More › View logs**. Jede Zeile hat das Format `Zeitpunkt LEVEL [Klasse] Meldung`, die Meldungen sind englisch.

| Ereignis | Level | Beispiel |
|---|---|---|
| Start und Datenbankprüfung | `INFO` | `Database connection OK (host)` |
| Login erfolgreich | `INFO` | `Login successful: userId=7` |
| Login fehlgeschlagen | `WARN` | `Login failed: wrong password, userId=7` oder `Login failed: unknown account a***@example.de` |
| Abgelehnter Zugriff (401, 403) | `WARN` | `Access denied: status=403 code=NOT_A_MEMBER method=GET path=/api/groups/3 user=7` |
| Gruppenaktionen | `INFO` / `WARN` | `Group created: groupId=3, userId=7`, `Join rejected: ALREADY_MEMBER groupId=3, userId=7` |
| Unerwarteter Fehler (500) | `ERROR` | `Unexpected error` mit Stacktrace |

Nie geloggt werden Passwörter, Tokens, Einladungscodes, Zugangsdaten und der Request-Body. E-Mail-Adressen erscheinen höchstens maskiert (`a***@example.de`), der Pfad steht ohne Query-String im Log, damit auch ein Token in der URL nicht auftaucht. Fehlgeschlagene Logins liefern in der Antwort immer dieselbe Meldung, der Grund steht nur im Log.

Das Log-Level der Anwendung ist über die Umgebungsvariable `LOG_LEVEL` einstellbar (Standard `INFO`, z. B. `DEBUG` für mehr Details). Auf Heroku wird sie unter Settings › Config Vars gesetzt, die App startet danach neu.

## Spiele aus der Queue (Match-Service)

Der Match-Service (Repo `tipprunde-matches`) gleicht den Spielplan mit football-data.org ab und schickt für jedes Spiel ein Event `MATCH_UPDATED` in die Queue `tipprunde.match-events` (RabbitMQ bei CloudAMQP, Plan Little Lemur, Region EU-West-1). Der Core liest die Queue (`MatchEventListener`) und speichert die Spiele in der Tabelle `matches`.

Die Übernahme ist idempotent (Idempotent Receiver), Schlüssel ist `external_id`:

| Fall | Ergebnis im Log (`outcome`) |
|---|---|
| Spiel noch nicht in der Tabelle | `INSERTED` |
| Spiel vorhanden, Stand gleich | `UNCHANGED`, keine Schreiboperation |
| Spiel vorhanden, Status, Anstoß oder Ergebnis geändert | `UPDATED` |
| Spiel wurde von Hand korrigiert (`manually_corrected`) | `MANUALLY_CORRECTED`, wird nie überschrieben |

Ein Ergebnis wird nur bei Status `FINISHED` übernommen (Tore 0 bis 20, Stand nach 90 Minuten). `resultChanged=true` im Log zeigt an, dass für dieses Spiel Punkte zu berechnen sind (F-AH-3). Ungültige Nachrichten werden mit `Match event rejected` verworfen und nicht neu zugestellt; der nächste Abgleich schickt die Spiele ohnehin erneut.

Der Empfang ist nur aktiv, wenn `QUEUE_URL` gesetzt ist und `MATCH_MANAGER` auf `postgres` steht. Ohne `QUEUE_URL` startet der Core wie bisher, die CI braucht keine Queue. Die URL enthält das Passwort und gehört nur in die Umgebungsvariable bzw. Config Var, nie ins Repo, in den Chat oder in KI-Tools. Ist sie einmal öffentlich geworden: bei CloudAMQP **Rotate password** und die neue URL überall neu eintragen.

Lokal setzen (PowerShell, die URL wird nicht angezeigt):

```powershell
$s = Read-Host "AMQP-URL einfuegen" -AsSecureString
[Environment]::SetEnvironmentVariable("QUEUE_URL", [System.Net.NetworkCredential]::new("", $s).Password, "User")
Remove-Variable s
```

Danach ein neues Terminal öffnen. Beim Start steht im Log eine Zeile `Created new connection: rabbitConnectionFactory…`, je verarbeitetem Spiel eine Zeile `Match event processed: externalId=… outcome=…`. Die Queue und ihre Nachrichten sind im CloudAMQP-Dashboard unter **RabbitMQ Manager › Queues and Streams** zu sehen.

## Deployment auf Heroku

Der Core-Service läuft auf Heroku (Eco-Dyno) und wird über die GitHub-Verknüpfung im Heroku-Dashboard deployt, ohne Heroku CLI. Jeder Merge auf `main` geht nach grüner CI automatisch live.

Dateien im Repo, die Heroku dafür braucht:

| Datei | Zweck |
|---|---|
| `system.properties` | Java-Version 17 für Heroku |
| `Procfile` | startet `build/libs/tipprunde-rest.jar` mit dem Port, den Heroku vorgibt |
| `build.gradle` | baut nur die ausführbare JAR mit festem Namen `tipprunde-rest.jar` |
| `gradlew` | muss in Git als ausführbar markiert sein, sonst bricht der Build unter Linux mit „Permission denied“ ab |

Heroku baut mit `./gradlew build -x test`; die Tests laufen vorher in der CI.

### Einrichtung im Dashboard (einmalig)

1. App anlegen (Region Europe), Dyno-Typ Eco.
2. Deploy › Deployment method › GitHub, `tipprunde-team/tipprunde-rest` verbinden (ein Owner der Organisation bestätigt den Zugriff).
3. Settings › Config Vars eintragen (Werte nur dort und per Privatnachricht, nie im Repo):

| Config Var | Inhalt |
|---|---|
| `DB_URL` | `jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require` |
| `DB_USER` | aiven-Benutzer |
| `DB_PASSWORD` | aiven-Passwort |
| `JWT_SECRET` | eigener Wert für Heroku, mindestens 32 Zeichen |
| `CORS_ALLOWED_ORIGINS` | Live-URL der MPA, z. B. `https://tipprunde-team.github.io` |
| `DB_POOL_SIZE` | optional, Standard `3` |
| `QUEUE_URL` | AMQP-URL von CloudAMQP (Instanz `tipprunde-queue`), dieselbe wie beim Match-Service |

`PORT` setzt Heroku selbst.

4. Deploy › Manual deploy › Branch `main` deployen und das Build-Log beobachten.
5. Live prüfen: `/actuator/health` liefert `{"status":"UP"}`, `/api/ping` liefert `{"status":"ok"}`.
6. „Enable Automatic Deploys“ für `main` mit „Wait for CI to pass before deploy“ einschalten.
7. Logs: More › View logs.

Eco-Dynos schlafen nach 30 Minuten ohne Aufruf ein; vor jeder Demo `/actuator/health` einmal aufrufen.

### Vor dem ersten Deployment lokal prüfen

```powershell
.\gradlew.bat bootJar
java -jar build\libs\tipprunde-rest.jar
```

Im zweiten Terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Erwartet: `status` ist `UP`. Danach den Server mit Strg+C stoppen.

## Team

Max Schamberger und Arlind Hulaj (Backend), Maximilian Göllnitz (PO), Niklas Schusser (SM)

