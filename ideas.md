# Ideen und nächste Schritte

## Kurzfristig

- [x] Persistenz auf PostgreSQL vorbereiten, weil die aktuelle Dateiablage für Docker-MVP reicht, aber nicht für mehrere Container.
- [x] Passwort-Hashing auf BCrypt oder Argon2 umstellen. SHA-256 mit Salt ist für ein MVP verständlich, aber nicht stark genug für echte Nutzerkonten.
- [x] Sessions stateless machen, z. B. mit signierten JWTs oder zentralem Session-Store. In-Memory-Sessions verhindern Autoscaling.
- [x] API-Tests für Tickets, Projekte, Tickettypen, Workflows und Rechte erweitern.
- [x] UI-Tests für Login, Ticketanlage, Drag-and-Drop und Admin-Workflow ergänzen.

## Fachliche MVP-Erweiterungen

- Kommentare pro Ticket hinzufügen.
- Änderungshistorie für Statuswechsel und Feldänderungen ergänzen.
- Labels, Fälligkeitsdatum und Schätzung als optionale Ticketfelder einführen.
- Anhänge über lokalen Speicher im Docker-MVP oder direkt über S3-kompatiblen Object Storage vorbereiten.
- Ticket-Detailseite als eigene Ansicht ergänzen, nicht nur als Dialog.
- Such- und Filterzustand in der URL speichern.
- Workflow-Regeln ausbauen: Pflichtfelder, erlaubte Rollen, Rücksprünge, globale Übergänge.

## Mandantenfähigkeit

- Datenmodell konsequent auf mehrere Organisationen testen.
- Organisationsauswahl und Organisationsverwaltung im Admin-Bereich ergänzen.
- Zugriffskontrollen so absichern, dass Nutzer niemals Daten anderer Organisationen lesen oder verändern können.
- Später Projektrollen statt nur globaler Rollen einführen.

## Cluster- und Betriebsfähigkeit

- PostgreSQL als externe Datenbank einführen.
- Datenbankmigrationen mit Flyway oder Liquibase ergänzen.
- Container stateless machen.
- Healthchecks aufteilen in `live` und `ready`.
- Konfiguration vollständig über Environment-Variablen abbilden.
- Strukturierte JSON-Logs für Cluster-Betrieb vorbereiten.
- Graceful Shutdown testen.
- Docker-Image kleiner und reproduzierbarer machen, z. B. mit fest gepinnten Versionen und Layer-Caching.

## Architektur

- Store-Schicht von Dateiablage auf Repository-Interfaces trennen.
- Domänenlogik für Workflow-Transitions aus dem Store herauslösen.
- Authentifizierung und Autorisierung als eigene Module strukturieren.
- API-DTOs von Persistenzmodellen trennen.
- Validierung zentralisieren, damit UI und API konsistente Regeln haben.

## Produktideen

- Persönliche Arbeitsansicht: „Meine Tickets“.
- Projekt-Dashboard mit Ticketzahlen nach Status, Priorität und Verantwortlichem.
- Gespeicherte Filter.
- Board-Spalten pro Projekt frei sortierbar machen.
- Bulk-Aktionen in der Liste.
- Import und Export für Tickets.
- Webhooks für Statuswechsel.
- Benachrichtigungen per E-Mail oder In-App.

## Nicht sofort bauen

- Sprints und Burndown-Charts.
- Frei konfigurierbare Custom Fields.
- Marketplace-ähnliche Plugin-Struktur.
- Vollständiges Jira-Rechtemodell.
- OAuth/SSO, bevor die lokale Auth-Schicht sauber modularisiert ist.
