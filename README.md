# SmartGN Message Broker

## Overview

Manages EMQX device credentials and topic permissions. Publishes service events over MQTT and keeps a bounded history of recent publish acknowledgments.

**Stack**  Java 21, Spring Boot, EMQX, MQTT 5.

## Run locally

Requirements are Docker Desktop with the Linux engine and PowerShell 7. Keep the SmartGN repositories as siblings for the integrated stack.

```powershell
./scripts/start-local.ps1 -Build
```

Run `./scripts/start-integrated.ps1` from the sibling `smartgn-api-gateway` repository.

| Endpoint | Local URL |
| --- | --- |
| Swagger UI | [Open](http://localhost:8087/swagger-ui/index.html) |
| OpenAPI | [JSON](http://localhost:8087/v3/api-docs) |
| Health | [Open](http://localhost:8087/actuator/health) |

## Configuration

Standalone settings use the variables in `.env.example`. Copy it to an ignored `.env` when the local startup script requires it. Integrated credentials and generated files are stored under `.local` in the Gateway repository.

## Verification

```powershell
./scripts/smoke-local.ps1
```

Run the integrated smoke from the Gateway repository to verify cross-service workflows.

## Repository layout

- `src/main` contains the service implementation.
- `docs` contains service documentation.
- `scripts` contains local startup and verification commands.
- `tools` contains verification helpers and supporting assets.
- `fixtures/db.json` defines demo fixtures for local verification.

## Documentation

- [Local development](docs/local-development.md)
- [OpenAPI export](docs/openapi.json)

## License

Distributed under the [MIT License](LICENSE).
