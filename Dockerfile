FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY VERSION /workspace/VERSION
COPY frontend ./
RUN npm run build

FROM gradle:8.11.1-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts VERSION ./
COPY --chown=gradle:gradle src ./src
COPY --chown=gradle:gradle frontend ./frontend
COPY --from=frontend-build --chown=gradle:gradle /workspace/frontend/dist/queuedos-frontend/browser ./src/main/resources/static
RUN gradle --no-daemon installDist

FROM eclipse-temurin:21-jre
ARG APP_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="QueueDos" \
      org.opencontainers.image.description="Jira-like ticket management application" \
      org.opencontainers.image.source="https://github.com/ljunker/QueueDos" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"
WORKDIR /app
COPY --from=build /home/gradle/project/build/install/queuedos /app
RUN adduser --system --group --uid 10001 queuedos \
    && chown -R queuedos:queuedos /app
USER queuedos
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/queuedos"]
