# AGENTS.md

## Commit Messages

- Commit-Nachrichten immer mit Ueberschrift und Liste an Aenderungen schreiben.
- password

## Docker Image Build

- QueueDos Docker Images fuer Docker Hub unter `kryptikker/queuedos` bauen und pushen.
- Images immer als Multi-Arch-Image fuer `linux/amd64` und `linux/arm64` bauen, damit Deployments auf beiden Plattformen
  funktionieren.
- Wenn der Buildx-Builder noch nicht existiert oder nicht aktiv ist, zuerst ausfuehren:

```bash
docker buildx create --name queuedos-builder --driver docker-container --use
```

- Danach Images mit `buildx build` direkt pushen. Immer `latest` und den kurzen Git-Commit-SHA taggen:

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t kryptikker/queuedos:latest \
  -t kryptikker/queuedos:<short-sha> \
  --push .
```

- Kein normales `docker build` plus `docker push` fuer Release-Images verwenden, weil dabei leicht nur die
  Host-Architektur gebaut wird.
