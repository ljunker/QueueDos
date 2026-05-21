# QueueDos

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

- Anmeldung mit E-Mail und Passwort, vorbereitet für spätere OAuth- oder SSO-Anbindung.
- Eine sichtbare Organisation mit mehreren Projekten.
- Projektbezogene Ticketschlüssel wie `QDOS-1`.
- Konfigurierbare Tickettypen.
- Konfigurierbare Workflows mit Status, Übergängen, Rollenbeschränkungen und Metadaten für Pflichtfelder.
- Tickets mit Titel, Beschreibung, Status, Typ, Priorität, verantwortlicher Person, Labels, Fälligkeitsdatum, Schätzung und meldender Person.
- Ticket-Detailansicht mit Kommentaren und Änderungshistorie.
- Kanban-Board mit Drag-and-Drop.
- Projekt-Dashboard mit Drilldown in gefilterte Ticketlisten.
- Ticketliste mit Suche, Filtern, Sortierung, URL-Zustand, privaten gespeicherten Filtern und Bulk-Aktionen.
- Persönliche projektübergreifende Ansicht für eigene Tickets.
- Admin-Oberflächen für Nutzer, Projekte, Tickettypen und Workflows.

Das Docker-Compose-Setup startet PostgreSQL und speichert QueueDos-Daten in relationalen Tabellen für Organisationen, Nutzer, Projekte, Tickettypen, Workflows, Workflow-Status, Workflow-Übergänge und Tickets. Für mehrere API-Container muss `QUEUEDOS_SESSION_SECRET` auf denselben starken Wert gesetzt werden, weil Anmeldungen als signierte stateless Tokens ausgegeben werden.

Wichtige Umgebungsvariablen:

- `QUEUEDOS_DATABASE_URL`: JDBC-URL für PostgreSQL, z. B. `jdbc:postgresql://db:5432/queuedos`.
- `QUEUEDOS_DATABASE_USER` / `QUEUEDOS_DATABASE_PASSWORD`: PostgreSQL-Zugangsdaten.
- `QUEUEDOS_DATA_FILE`: optionaler JSON-Dateipfad, nur wenn `QUEUEDOS_DATABASE_URL` nicht gesetzt ist.
- `QUEUEDOS_SESSION_SECRET`: gemeinsamer HMAC-Schlüssel für stateless Session-Tokens.
- `QUEUEDOS_SESSION_TTL_HOURS`: Token-Laufzeit in Stunden, Standard `12`.

Ohne `QUEUEDOS_DATABASE_URL` fällt die Anwendung für lokale Experimente weiterhin auf `QUEUEDOS_DATA_FILE` zurück. Bestehende Daten aus dem früheren PostgreSQL-Snapshot `queuedos_state` werden beim ersten Start in die relationalen Tabellen migriert.

## Zustandsprüfung

```bash
curl http://localhost:8080/api/health
```

## Angular-Frontend

Das Frontend liegt als Angular-Subprojekt in `frontend/`. Es nutzt standalone Components, eine Atomic-Design-Struktur unter `shared/atoms`, `shared/molecules` und `shared/organisms` sowie NgRx Store/Effects für Authentifizierung, Bootstrap-Daten, URL-Zustand, Tickets, Admin-Aktionen und Workflow-Entwürfe.

```bash
cd frontend
npm install
npm start
```

`npm start` startet den Angular-Dev-Server und proxyt `/api` an die Ktor-API auf `http://localhost:8080`. Das Subprojekt ist auf Angular 21 ausgelegt; dafür sollte Node.js 20.19+, 22.12+ oder 24 verwendet werden.

Der Docker-Build baut das Angular-Frontend in einer eigenen Node-Stage und kopiert das Ergebnis in die Ktor-Ressourcen, damit `http://localhost:8080` die Angular-App ausliefert.
