# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/cherrybrook-weather-0.0.1-SNAPSHOT.jar app.jar
USER appuser
# Render injects the listening port via the PORT env var; 8080 is the local default.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
