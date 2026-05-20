FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend ./
RUN npm run build

FROM gradle:8.11.1-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle src ./src
COPY --chown=gradle:gradle frontend ./frontend
COPY --from=frontend-build --chown=gradle:gradle /workspace/frontend/dist/queuedos-frontend/browser ./src/main/resources/static
RUN gradle --no-daemon test installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /home/gradle/project/build/install/queuedos /app
RUN adduser --system --group --uid 10001 queuedos \
    && mkdir -p /data \
    && chown -R queuedos:queuedos /app /data
USER queuedos
ENV PORT=8080
ENV QUEUEDOS_DATA_FILE=/data/queuedos.json
EXPOSE 8080
ENTRYPOINT ["/app/bin/queuedos"]
