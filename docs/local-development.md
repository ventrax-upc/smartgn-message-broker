# Local development

## Requirements

- Docker Desktop with Docker Compose v2.
- An EMQX 5 broker reachable from the container, with an MQTT account for `broker-service` and a management API key/secret.
- A reachable SmartGN Admin Server if registration is required.

The broker service is an HTTP/MQTT adapter. Its local Compose does not create EMQX or Admin containers. `MQTT_BROKER_HOST`, `MQTT_BROKER_PORT`, `EMQX_API_URL`, `EMQX_API_KEY` and `EMQX_API_SECRET` must point to the broker and its management API. Use `host.docker.internal` to reach a broker published on the Docker host, or join this container to the broker's Docker network.

## Start and verify

```powershell
./scripts/start-local.ps1 -Build
./scripts/smoke-local.ps1
```

The start script copies `.env.example` to `.env` only if no local file exists. Set a non-empty local `INTERNAL_SERVICE_TOKEN`; the smoke command uses it to verify the protected recent-message query. No publish or device authorization request is sent, so verification does not change external broker state.

## Endpoints

- HTTP API: `http://127.0.0.1:8087`
- Health: `http://127.0.0.1:8087/actuator/health`
- Swagger UI: `http://127.0.0.1:8087/swagger-ui.html`
- OpenAPI: `http://127.0.0.1:8087/v3/api-docs`
- Recent adapter events: `GET /api/v1/broker/messages` with `Authorization: Bearer <INTERNAL_SERVICE_TOKEN>`

`tools/verify-runtime.ps1` checks health, required OpenAPI paths and `serviceAuth`, then calls the read-only recent-message endpoint with the configured token.

## Integrated platform

The canonical local platform is the 11-service composition in `../smartgn-api-gateway`. Run `./scripts/start-integrated.ps1` there. That composition provides EMQX, Config and Admin, configures the broker service on port 8087, and supplies generated local credentials. See [integrated local SmartGN](../../smartgn-api-gateway/docs/integrated-local.md).

Stop the standalone service with `docker compose down`. This does not stop or remove the separately managed EMQX broker.
