# Spring Boot Pacman

A classic Pac-Man game served by a Spring Boot application. The game runs in the browser using HTML5 Canvas; Spring Boot serves the static assets and a small health endpoint.

## Run locally

```bash
mvn spring-boot:run
```

Then open http://localhost:8080

## Build and run with Docker

```bash
docker build -t pacman .
docker run --rm -p 8080:8080 pacman
```

Then open http://localhost:8080

## Controls

- Arrow keys: move Pac-Man
- `R`: restart the game

## Endpoints

- `GET /` — game UI
- `GET /api/health` — health check
