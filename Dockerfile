# syntax=docker/dockerfile:1.6

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -e -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -e -ntp package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --home /app spring
COPY --from=build /workspace/target/pacman.jar /app/pacman.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/pacman.jar"]
