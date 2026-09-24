# Restio

Sistema de gestión integral para restaurantes: mesas, pedidos, cocina, carta, caja, almacén y
personal. Backend en la nube con **Java 21 + Spring Boot 3**; los dispositivos del local
(TPV, KDS, tótem) son una app **React Native** que funciona sin conexión.

> Estado: **etapa 0 — Cimientos** (backend base, autenticación, núcleo local-first del TPV,
> vinculación de dispositivos y sincronización mínima). Aún no apto para producción.

## Requisitos

- JDK 21 (o solo Docker: la imagen compila el backend)
- Docker y Docker Compose
- Node 22.13+ y pnpm 9 vía corepack (solo para `frontend/`)

## Backend

```bash
docker compose up -d postgres     # PostgreSQL 16 en localhost:5432
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw verify                     # compila, tests (Testcontainers), Spotless y JaCoCo
```

- API: `http://localhost:8080` · Swagger UI: `/swagger-ui.html` · Salud: `/actuator/health`
- El esquema lo gestiona solo Flyway (`src/main/resources/db/migration`); nunca `ddl-auto`.
- Variables de entorno: `RESTIO_DB_URL`, `RESTIO_DB_USER`, `RESTIO_DB_PASSWORD`,
  `RESTIO_JWT_SECRET`.

Imagen de la aplicación (perfil `full` del compose):

```bash
docker compose --profile full up --build
```

## Frontend

```bash
cd frontend
pnpm install
pnpm test        # también: pnpm lint, pnpm typecheck, pnpm build
```

Monorepo pnpm + Turborepo con los paquetes compartidos `pos-core` (base local SQLite y log de
eventos), `pos-sync` (vinculación, sincronización y PIN offline), `api-client`, `i18n` y `ui`, y
la app `apps/pos` (Expo). La app lee la URL del backend de `EXPO_PUBLIC_API_URL`:

```bash
cd frontend/apps/pos
EXPO_PUBLIC_API_URL=http://<ip-de-tu-máquina>:8080 pnpm expo run:android
```

## Integración continua

`.github/workflows/ci.yml` ejecuta en cada push y pull request `./mvnw verify` (backend) y
formato, lint, typecheck y tests (frontend). `.github/workflows/cla.yml` comprueba la firma del
CLA en los pull requests.

## Contribuir

Lee [CONTRIBUTING.md](CONTRIBUTING.md). Las contribuciones externas requieren firmar el
[CLA](CLA.md).

## Licencia

Restio es *source-available* bajo la [PolyForm Shield License 1.0.0](LICENSE): puedes usarlo,
modificarlo y distribuirlo gratis, también en tu propio restaurante, pero no para ofrecer un
producto que compita con Restio. No es una licencia "open source" aprobada por la OSI.
