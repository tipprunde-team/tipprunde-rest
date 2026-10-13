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
```

Danach das Terminal bzw. IntelliJ neu starten, damit die Variablen ankommen. Prüfen in `cmd` mit `echo %DB_URL%`, in PowerShell mit `$env:DB_URL`.

Die aiven-Service-URI sieht so aus: `postgres://USER:PASSWORD@HOST:PORT/defaultdb?sslmode=require`. Für `DB_URL` wird daraus nur `jdbc:postgresql://HOST:PORT/defaultdb?sslmode=require`. Benutzer und Passwort kommen getrennt in `DB_USER` und `DB_PASSWORD`.

Optional:

| Variable | Bedeutung | Standard |
|---|---|---|
| `PORT` | Port des Backends | `8080` |
| `CORS_ALLOWED_ORIGINS` | erlaubte Frontend-Origins, kommagetrennt | `http://localhost:5500,http://127.0.0.1:5500` |
| `FOOTBALL_DATA_TOKEN` | API-Key von football-data.org (später für den Spielplan-Import) | – |

Fehlen `DB_URL`, `DB_USER` oder `DB_PASSWORD`, startet das Backend nicht. Typische Meldung: `'url' must start with "jdbc"`.

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

## Frontend lokal

Das Frontend (Repo `tipprunde-web`) wird lokal auf Port 5500 ausgeliefert, z. B. mit der VS-Code-Erweiterung Live Server. Andere Origins lassen sich über `CORS_ALLOWED_ORIGINS` freigeben.

## Team

Max Schramberger und Arlind Hulaj (Backend), Maximilian Göllnitz (PO), Niklas Schusser (SM)
