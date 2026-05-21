# Cherry Brook Weather

A Spring Boot web app that shows the **last 12 months of weather for Cherry Brook**
(temperature and rainfall) and how much each month varied from the long-term
climate normal.

## What it shows

- A dashboard at `/` with summary cards, two charts (temperature and rainfall,
  each plotted against the 1991–2020 normal) and a month-by-month table of anomalies.
- A JSON endpoint at `/api/weather`.
- A health check at `/healthz`.

Weather data comes from the free [Open-Meteo](https://open-meteo.com/) historical
archive (ERA5 reanalysis). For each calendar month the app computes the
1991–2020 normal and reports the **anomaly** = observed value − normal.

## Run locally

```bash
mvn spring-boot:run
# then open http://localhost:8080
```

Or with Docker:

```bash
docker build -t cherrybrook-weather .
docker run -p 8080:8080 cherrybrook-weather
```

## Deploy to Render

This repo includes a `Dockerfile` and a `render.yaml` blueprint.

1. Push the repo to GitHub.
2. In Render, choose **New → Blueprint** and point it at the repo (it reads
   `render.yaml`), **or** **New → Web Service** with **Runtime: Docker**.
3. Render builds the `Dockerfile` and starts the service. It injects the
   listening port via the `PORT` environment variable, which the app reads
   automatically (`server.port=${PORT:8080}`).

## Configuration

Override these via environment variables (or `application.properties`):

| Property | Env var | Default |
| --- | --- | --- |
| `weather.location.name` | `WEATHER_LOCATION_NAME` | `Cherrybrook, NSW, Australia` |
| `weather.location.latitude` | `WEATHER_LOCATION_LATITUDE` | `-33.7217` |
| `weather.location.longitude` | `WEATHER_LOCATION_LONGITUDE` | `151.0458` |
| `weather.baseline.start-year` | `WEATHER_BASELINE_START_YEAR` | `1991` |
| `weather.baseline.end-year` | `WEATHER_BASELINE_END_YEAR` | `2020` |
| `weather.cache.ttl-minutes` | `WEATHER_CACHE_TTL_MINUTES` | `360` |

The default location is Cherrybrook in New South Wales, Australia. To use a
different "Cherry Brook" (e.g. Cherry Brook, Nova Scotia), set the latitude and
longitude environment variables.
