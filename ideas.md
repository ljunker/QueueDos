# Ideen und nächste Schritte

## Kurzfristig

- [x] Persistenz auf PostgreSQL vorbereiten, weil die aktuelle Dateiablage für Docker-MVP reicht, aber nicht für mehrere Container.
- [x] Passwort-Hashing auf BCrypt oder Argon2 umstellen. SHA-256 mit Salt ist für ein MVP verständlich, aber nicht stark genug für echte Nutzerkonten.
- [x] Sessions stateless machen, z. B. mit signierten JWTs oder zentralem Session-Store. In-Memory-Sessions verhindern Autoscaling.
- [x] API-Tests für Tickets, Projekte, Tickettypen, Workflows und Rechte erweitern.
- [x] UI-Tests für Login, Ticketanlage, Drag-and-Drop und Admin-Workflow ergänzen.

## Fachliche MVP-Erweiterungen

- [x] Kommentare pro Ticket hinzufügen.
- [x] Änderungshistorie für Statuswechsel und Feldänderungen ergänzen.
- [x] Labels, Fälligkeitsdatum und Schätzung als optionale Ticketfelder einführen.
- [x] Ticket-Detailseite als eigene Ansicht ergänzen, nicht nur als Dialog.
- [x] Such- und Filterzustand in der URL speichern.
- [x] Workflow-Regeln ausbauen: Pflichtfelder, erlaubte Rollen, Rücksprünge, globale Übergänge.

## Cluster- und Betriebsfähigkeit

- [x] Datenbankmigrationen mit Flyway oder Liquibase ergänzen.
- Container stateless machen.
- Healthchecks aufteilen in `live` und `ready`.
- Konfiguration vollständig über Environment-Variablen abbilden.
- Strukturierte JSON-Logs für Cluster-Betrieb vorbereiten.
- Graceful Shutdown testen.

## Architektur

- [x] Domänenlogik für Workflow-Transitions aus dem Store herauslösen.
- [x] Authentifizierung und Autorisierung als eigene Module strukturieren.
- [x] API-DTOs von Persistenzmodellen trennen.
- [x] Validierung zentralisieren, damit UI und API konsistente Regeln haben.
- [x] kein Snapshot Storing machen, sondern transactions auf der db.

## Produktideen

- [x] Persönliche Arbeitsansicht: „Meine Tickets“.
- [x] Projekt-Dashboard mit Ticketzahlen nach Status, Priorität und Verantwortlichem.
- [x] Gespeicherte Filter.
- Eigene Boards pro Projekt.
- Eigene Board Spalten pro Board.
- Board-Spalten frei sortierbar machen.
- [x] Bulk-Aktionen in der Liste.
- Import und Export für Tickets.
- Webhooks für Statuswechsel.
- Benachrichtigungen per E-Mail oder In-App.

## Anforderungen

- [x] Login per Microsoft-Account (Single Sign On)
- [x] Sichtbarkeit von Bewegungen auf dem Board und in Tickets (slack hooks sind dafür geeignet. für jedes event wie "
  kommentar", "spalte verschoben" oder so muss eine nachricht konfigurierbar sein)
- [x] irgendwie muss man sich zu einem ticket committen können. Dafür muss eine gruppe user sich eintragen können auf
  das ticket, außer dem assignee.
- [x] gelöschte tickets wiederherstellen können
