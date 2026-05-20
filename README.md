# QueueDos

QueueDos ist ein Kotlin-MVP für ein Jira-ähnliches Ticketsystem. Die Anwendung läuft als ein Docker-Container mit Ktor-REST-API, statischer Weboberfläche und dateibasierter Persistenz für das MVP.

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
- Tickets mit Titel, Beschreibung, Status, Typ, Priorität, verantwortlicher Person und meldender Person.
- Kanban-Board mit Drag-and-Drop.
- Ticketliste mit Suche, Filtern und Sortierung.
- Admin-Oberflächen für Nutzer, Projekte, Tickettypen und Workflows.

Die Anwendung speichert in `QUEUEDOS_DATA_FILE`; das Docker-Compose-Setup bindet diesen Pfad als Volume ein. Für späteres Autoscaling im Cluster sollte die Persistenz in eine externe Datenbank wandern, damit der API-Container zustandslos bleibt.

## Zustandsprüfung

```bash
curl http://localhost:8080/api/health
```

## Angular-Frontend

Das aktuelle Frontend wird weiterhin aus `src/main/resources/static` ausgeliefert. Für die spätere Migration liegt ein eigenes Angular-Subprojekt in `frontend/`.

```bash
cd frontend
npm install
npm start
```

`npm start` startet den Angular-Dev-Server und proxyt `/api` an die Ktor-API auf `http://localhost:8080`. Das Subprojekt ist auf Angular 21 ausgelegt; dafür sollte Node.js 20, 22 oder 24 verwendet werden.
