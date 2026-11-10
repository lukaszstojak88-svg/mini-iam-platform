# syntax=docker/dockerfile:1
# Build from the repository root: docker build -f auth-service/Dockerfile .
# Tests run via Maven outside the image (./mvnw verify) - Testcontainers cannot run inside docker build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY user-service/pom.xml user-service/
COPY auth-service/pom.xml auth-service/
COPY audit-service/pom.xml audit-service/
COPY auth-service/src auth-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl auth-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service.jar app.jar
USER app
# JSON logs in ECS format on stdout (spec §16.1)
ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
