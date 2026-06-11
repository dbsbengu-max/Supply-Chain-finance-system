FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace/backend/scf-server

COPY backend/scf-server/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY backend/scf-server/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/backend/scf-server/target/scf-server-*.jar /app/scf-server.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/scf-server.jar"]
