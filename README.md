<p align="center">
  <a href="https://queue.kryptikk.de/">
    <img src="docs/assets/queuedos-logo.svg" alt="QueueDos" width="320">
  </a>
</p>

<h1 align="center">QueueDos</h1>

<p align="center">
  <em>Ein selbst gehostetes, Jira-ähnliches Ticketsystem für Teams —<br>
  mit konfigurierbaren Workflows, Kanban-Board, rollenbasiertem Zugriff und Integrationen.</em>
</p>

<p align="center">
  <img alt="Kotlin 2.4" src="https://img.shields.io/badge/Kotlin-2.4-7f52ff.svg">
  <img alt="Ktor 3.5" src="https://img.shields.io/badge/Ktor-3.5-087cfa.svg">
  <img alt="Angular 21" src="https://img.shields.io/badge/Angular-21-dd0031.svg">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Flyway-4169e1.svg">
  <a href="https://hub.docker.com/r/kryptikker/queuedos"><img alt="Docker: Multi-Arch" src="https://img.shields.io/badge/Docker-amd64%20%7C%20arm64-2496ed.svg"></a>
</p>

QueueDos ist ein Kotlin-MVP für ein Jira-ähnliches Ticketsystem. Die Anwendung läuft mit Ktor-REST-API, Angular-Frontend und PostgreSQL-Persistenz.

## Start

```bash
docker compose up --build
```

Danach `http://localhost:8080` öffnen.

Vordefinierte Nutzer:

- Administrator: `admin@queuedos.local` / `admin`
- Mitglied: `member@queuedos.local` / `member`

## MVP-Umfang

- Anmeldung mit E-Mail/Passwort oder optionalem Microsoft-SSO mit automatischer Benutzeranlage.
- Eine sichtbare Organisation mit mehreren Projekten.
- Projektbezogene Zugriffssteuerung mit organisationsweiten Systemadmins sowie Projektadmins und Projekt-Membern.
- Projektbezogene Ticketschlüssel wie `QDOS-1`.
- Konfigurierbare Tickettypen.
- Konfigurierbare Workflows mit Status, Übergängen, Rollenbeschränkungen und Metadaten für Pflichtfelder.
- Tickets mit Titel, Beschreibung, Status, Typ, Priorität, verantwortlicher Person, Labels, Fälligkeitsdatum, Schätzung und meldender Person.
- Ticket-Detailansicht mit Kommentaren, Änderungshistorie und Ticket-Commitments zusätzlich zur verantwortlichen Person.
- Kanban-Board mit Drag-and-Drop.
- Projekt-Dashboard mit Drilldown in gefilterte Ticketlisten.
- Ticketliste mit Suche, Filtern, Sortierung, URL-Zustand, privaten gespeicherten Filtern und Bulk-Aktionen.
- Persönliche projektübergreifende Ansicht für eigene Tickets.
- Admin-Oberflächen für Nutzer, Projekte, Tickettypen, Workflows, wiederherstellbare gelöschte Tickets und
  Slack-Activity-Hooks.

Systemadmins können alle Projekte verwalten und Nutzerkonten, Integrationen sowie Projekte organisationsweit
administrieren. Andere Nutzer sehen ausschließlich Projekte, denen sie als Projektadmin oder Projekt-Member zugeteilt
sind. Projektadmins verwalten Einstellungen, Workflow, Tickettypen, Mitglieder und Papierkorb ihres Projekts.

Das Docker-Compose-Setup startet PostgreSQL und speichert QueueDos-Daten in relationalen Tabellen für Organisationen,
Nutzer, Projekte, Tickettypen, Workflows, Workflow-Status, Workflow-Übergänge und Tickets. Flyway versioniert das
Datenbankschema beim Start. Für mehrere API-Container muss `QUEUEDOS_SESSION_SECRET` auf denselben starken Wert gesetzt
werden, weil Anmeldungen als signierte stateless Tokens ausgegeben werden.

Wichtige Umgebungsvariablen:

- `QUEUEDOS_DATABASE_URL`: verpflichtende JDBC-URL für PostgreSQL, z. B. `jdbc:postgresql://db:5432/queuedos`.
- `QUEUEDOS_DATABASE_USER` / `QUEUEDOS_DATABASE_PASSWORD`: PostgreSQL-Zugangsdaten.
- `QUEUEDOS_SESSION_SECRET`: gemeinsamer HMAC-Schlüssel für stateless Session-Tokens.
- `QUEUEDOS_SESSION_TTL_HOURS`: Token-Laufzeit in Stunden, Standard `12`.
- `QUEUEDOS_PUBLIC_BASE_URL`: öffentliche Basis-URL für Microsoft-SSO-Redirects, z. B. `http://localhost:8080`.
- `QUEUEDOS_MCP_ALLOWED_HOSTS`: optionale, kommaseparierte Liste erlaubter Hostnamen für `/mcp`. Ohne Angabe wird der
  Host aus `QUEUEDOS_PUBLIC_BASE_URL` zusammen mit den lokalen Hostnamen verwendet.
- `QUEUEDOS_MCP_ALLOWED_ORIGINS`: optionale, kommaseparierte Liste erlaubter Origins für `/mcp`. Ohne Angabe wird
  `QUEUEDOS_PUBLIC_BASE_URL` zusammen mit lokalen Origins verwendet. Die Sicherheitsprüfung vergleicht Hostnamen.
- `QUEUEDOS_MICROSOFT_CLIENT_ID` / `QUEUEDOS_MICROSOFT_CLIENT_SECRET`: aktivieren Microsoft-SSO.
- `QUEUEDOS_MICROSOFT_TENANT`: Entra-Tenant für Microsoft-SSO, Standard `common`.
- `QUEUEDOS_MICROSOFT_ALLOWED_DOMAINS`: kommaseparierte Liste erlaubter E-Mail-Domains für Microsoft-SSO, z. B.
  `example.com,example.org`. Die Prüfung ist nicht case-sensitiv, gilt bei jedem Microsoft-Login und erlaubt nur exakte
  Treffer; Unterdomains müssen separat eingetragen werden. Ohne mindestens eine erlaubte Domain bleibt Microsoft-SSO
  deaktiviert.
- `QUEUEDOS_MICROSOFT_REDIRECT_URI`: optionaler expliziter Microsoft-Callback-Redirect; Standard ist
  `${QUEUEDOS_PUBLIC_BASE_URL}/api/auth/microsoft/callback`.

Ohne `QUEUEDOS_DATABASE_URL` startet die Anwendung nicht. Bestehende Daten aus dem früheren PostgreSQL-Snapshot
`queuedos_state` werden beim ersten Flyway-Lauf in die relationalen Tabellen migriert.

Microsoft-SSO meldet vorhandene aktive QueueDos-Nutzer anhand ihrer E-Mail an. Wenn die von Microsoft gelieferte
E-Mail noch keinem QueueDos-Nutzer gehört und ihre Domain freigegeben ist, wird automatisch ein aktives Mitglied in
der Standardorganisation angelegt. Inaktive Nutzer werden nicht reaktiviert. Slack-Hooks werden im Admin-Bereich pro
Activity-Ereignis konfiguriert; eine Vorlage kann Platzhalter wie `{{actorName}}`, `{{ticketKey}}`, `{{ticketTitle}}`,
`{{comment}}`, `{{fromStatusId}}` oder `{{toStatusId}}` verwenden.

## Persönlicher MCP-Zugriff

Unter „API access“ kann jeder Nutzer persönliche MCP-Tokens erstellen und widerrufen. Ein Token wird nur unmittelbar
nach dem Erstellen vollständig angezeigt, nach 90 Tagen ungültig und serverseitig ausschließlich als SHA-256-Hash
gespeichert. Deaktivierung des Nutzers, Widerruf des Tokens und Änderungen an Projektrollen wirken sofort.

Der stateless Streamable-HTTP-Endpunkt liegt unter:

```text
https://queuedos.example/mcp
```

Jeder POST benötigt `Authorization: Bearer <token>`. Der Server bietet Werkzeuge zum Auflisten von Projekten, Abrufen
des Projektkontexts, Suchen und Lesen von Tickets sowie zum Erstellen, Ändern, Verschieben und Kommentieren. Sämtliche
Mutationen verwenden dieselben Rechte-, Workflow-, Revisions- und Activity-Regeln wie die REST-Oberfläche.

## Zustandsprüfung

```bash
curl http://localhost:8080/api/health
```

Die Backend-Tests starten PostgreSQL über Testcontainers:

```bash
./gradlew test
```

## Angular-Frontend

Das Frontend liegt als Angular-Subprojekt in `frontend/`. Es nutzt standalone Components, eine Atomic-Design-Struktur unter `shared/atoms`, `shared/molecules` und `shared/organisms` sowie NgRx Store/Effects für Authentifizierung, Bootstrap-Daten, URL-Zustand, Tickets, Admin-Aktionen und Workflow-Entwürfe.

```bash
cd frontend
npm install
npm start
```

`npm start` startet den Angular-Dev-Server und proxyt `/api` sowie `/mcp` an die Ktor-Anwendung auf
`http://localhost:8080`. Das Subprojekt ist auf Angular 21 ausgelegt; dafür sollte Node.js 20.19+, 22.12+ oder 24
verwendet werden.

Der Docker-Build baut das Angular-Frontend in einer eigenen Node-Stage und kopiert das Ergebnis in die Ktor-Ressourcen, damit `http://localhost:8080` die Angular-App ausliefert.


## Versionierung und Docker-Release

`VERSION` im Projektstamm ist die einzige Quelle der QueueDos-Version. Sie enthält eine SemVer ohne führendes `v`,
zum Beispiel `1.0.0` oder `1.1.0-rc.1`. Gradle, das Angular-Frontend und die Docker-Image-Metadaten verwenden diesen
Wert. Das Frontend zeigt die Version als `v1.0.0` unten in der Seitenleiste an.

Für ein neues Release zuerst `VERSION` erhöhen und die Änderung zusammen mit allen Release-Inhalten committen. Die
Commit-Nachricht besteht gemäß Projektkonvention aus einer Überschrift und einer Liste der Änderungen. Anschließend
bei Docker Hub anmelden und den geplanten Build prüfen:

```bash
docker login
scripts/publish-docker.sh --dry-run
```

Der echte Publish-Lauf akzeptiert nur einen sauberen Git-Arbeitsbaum und verändert weder Git-Commits noch Git-Tags:

```bash
scripts/publish-docker.sh
```

Das Skript erstellt den Buildx-Builder `queuedos-builder` bei Bedarf mit dem `docker-container`-Treiber, aktiviert und
initialisiert ihn und baut ein Multi-Arch-Image für `linux/amd64` und `linux/arm64`. Es pusht drei Docker-Tags:

- `kryptikker/queuedos:latest`
- `kryptikker/queuedos:v<VERSION>`, zum Beispiel `kryptikker/queuedos:v1.0.0`
- `kryptikker/queuedos:<short-sha>`, zum Beispiel `kryptikker/queuedos:98134d33`

Ein erneuter Publish derselben Version darf die bereits vorhandenen Tags aktualisieren. Der Dry-Run nimmt keine
Änderungen an Docker oder dem Builder vor und pusht kein Image.
