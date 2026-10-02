# Console Adda — Gaming Cafe Management & Billing System

Web application for **Console Adda**, a PS5 gaming cafe in Mumbai. It runs the counter: live PS5
sessions, automatic billing, food & beverages, game rentals, inventory, daily closing and reports.

> **Status: Phase 4 of 6 complete.** Working today: login with roles, live PS5 session board
> (start → add food → END → discount → payment → numbered bill → print), billing & rounding engine,
> pricing master, station management, food menu, counter (walk-in) sales, 15-minute drink credit,
> discounts, per-category GST, stock ledger, bill search and cancellation, A4 and 80 mm invoices,
> customer master with history, game rentals (deposits, late fees, ROI per disc), dashboard with charts,
> 10 reports with CSV/Excel export, and daily closing with cash count and day locking. The admin panel
> for users and business details arrives in Phase 5. See [Roadmap](#roadmap).

## Contents
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Quick start (Docker)](#quick-start-docker)
- [Default development login](#default-development-login)
- [Configuration](#configuration)
- [Running locally without Docker](#running-locally-without-docker)
- [Database](#database)
- [API documentation](#api-documentation)
- [Testing](#testing)
- [Production deployment](#production-deployment)
- [Backup & restore](#backup--restore)
- [Roadmap](#roadmap)

---

## Architecture

```
Browser ──▶ frontend (nginx: React SPA + /api proxy) ──▶ backend (Spring Boot 3, Java 21) ──▶ MySQL 8.4
```

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, Material UI, TanStack Query |
| Backend | Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation), Lombok |
| Auth | JWT (HS256) bearer tokens, BCrypt passwords, roles ADMIN / OPERATOR |
| Database | MySQL 8.4, schema managed by **Flyway** (Hibernate only validates) |
| Packaging | Docker multi-stage images + docker compose |

Backend packages: `controller` → `service` → `repository`, with `entity`, `dto`, `mapper`,
`exception`, `config`, `security`. Controllers hold no business logic; entities never leave the
service layer; money is always `BigDecimal`.

**Full design** (spec analysis, decisions, ERD, billing engine, API map, phases):
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

```
backend/            Spring Boot API (Flyway migrations in src/main/resources/db/migration)
frontend/           React app + nginx config
docs/               Architecture & design
scripts/            Database backup / restore
docker-compose.yml  MySQL + backend + frontend
.env.example        Every configurable value / secret
the-console-adda/   Pre-existing Next.js starter, not used by this system (safe to delete)
```

## Requirements

| To run | To develop |
|---|---|
| Docker Engine 24+ with Compose v2 (Docker Desktop on Windows/macOS) | Java 21, Maven 3.9+, Node.js 22+, Docker (tests use Testcontainers) |

## Quick start (Docker)

```bash
cp .env.example .env
# Edit .env: set MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up --build -d
```

Open **http://localhost:3000**. From a tablet or phone on the cafe Wi-Fi, use
`http://<counter-PC-IP>:3000`.

| Service | URL | Notes |
|---|---|---|
| App | http://localhost:3000 | nginx serves the UI and proxies `/api` |
| Backend | http://localhost:8080 | bound to localhost only |
| MySQL | `127.0.0.1:3307` | bound to localhost only |

Stop: `docker compose down` (data is kept in the `mysql-data` volume).
Logs: `docker compose logs -f backend`.

## Default development login

> ⚠️ **DEVELOPMENT CREDENTIALS. NEVER USE AT THE CAFE.** They exist only when
> `SPRING_PROFILES_ACTIVE=dev`.

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@1234` | ADMIN |
| `operator` | `Operator@1234` | OPERATOR |

With `SPRING_PROFILES_ACTIVE=prod`, no development accounts are created. Instead, one admin is created
from `APP_BOOTSTRAP_ADMIN_USERNAME` / `APP_BOOTSTRAP_ADMIN_PASSWORD`, and that admin **must change the
password at first login**. Until then every API except "change password" is refused. If no users exist and no
bootstrap password is set, the backend refuses to start and says why.

## Configuration

All configuration is through environment variables (`.env` for Docker). Secrets are never committed.

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` (dev accounts, debug logs) or `prod` |
| `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Database |
| `JWT_SECRET` | Token signing key, **≥ 32 characters**. Startup fails if shorter. |
| `JWT_TTL` | Login lifetime (default `12h`, one shift) |
| `APP_BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` | First admin in `prod` |
| `API_DOCS_ENABLED` | Swagger UI on/off (off by default in `prod`) |
| `FRONTEND_PORT`, `BACKEND_HOST_PORT`, `MYSQL_HOST_PORT` | Published ports |

Business settings (prices, GST, stations, menu, bill prefix, business-day start time…) live in the
**database**, not in code or env files, and are edited from the admin panel (Phases 2–5).

## Running locally without Docker

```bash
# 1. MySQL (any MySQL 8.x); for example just the database container:
docker compose up -d mysql            # uses .env; listens on 127.0.0.1:3307

# 2. Backend (dev profile, http://localhost:8080)
cd backend
DB_URL='jdbc:mysql://localhost:3307/console_adda?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false' \
DB_PASSWORD='<MYSQL_PASSWORD from .env>' \
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# 3. Frontend (http://localhost:5173, proxies /api to :8080)
cd frontend
npm install
npm run dev
```

## Database

* Flyway runs migrations automatically at backend start-up: `V1__baseline_schema.sql` (all tables,
  keys, indexes, constraints) and `V2__seed_reference_data.sql` (roles, 5 PS5 stations, base pricing,
  menu, sample rental games, settings).
* `spring.jpa.hibernate.ddl-auto=validate` in every profile: Hibernate never creates or alters tables.
* Schema changes are made **only** by adding a new `V<n>__description.sql` file. Never edit an
  applied migration.
* Data-safety rules are enforced by the database itself as well as the code: one active session
  per station (unique index on a generated column), non-negative prices/quantities (CHECK
  constraints), unique bill numbers.
* Timestamps are stored in UTC; the business timezone is `Asia/Kolkata`.

**Printing:** the invoice page (`/bills/{id}/print`) prints on A4 or 80 mm thermal paper through the
browser's print dialog. For a thermal printer, install its driver, set the paper size to 80 mm in the
print dialog and turn off headers/footers and margins once; the browser remembers it.

**Seed data to review before go-live:** set opening stock and reorder levels in Inventory (low-stock
warnings start once a reorder level is set), food cost prices are `0.00` (needed for profit),
rental game prices are the spec's sample values with no late fee or deposit (set them per disc in
Rentals → Games), GST is disabled, station utilization assumes 12 opening hours a day (setting
`reports.station-hours-per-day`), and the additional-player rule is pending confirmation (see
`docs/ARCHITECTURE.md` §1.1).

**Daily closing:** close each business day from *Daily Closing* after counting the drawer. Once closed,
nothing can be paid, refunded, rented or stocked on that day until an admin reopens it (with a reason,
audited). A day can't be closed while sessions are still running.

## API documentation

With `API_DOCS_ENABLED=true`, interactive docs are at **http://localhost:8080/swagger-ui.html**
(OpenAPI JSON: `/v3/api-docs`). Click *Authorize* and paste the token from `POST /api/auth/login`.

Every error has the same shape:

```json
{ "timestamp": "2026-10-02T11:52:29Z", "status": 401, "error": "Unauthorized",
  "code": "INVALID_CREDENTIALS", "message": "Invalid username or password",
  "path": "/api/auth/login", "validationErrors": [] }
```

Endpoints available now:

| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/login` | public |
| GET | `/api/auth/me` · POST `/api/auth/change-password` | logged in |
| GET | `/api/stations?includeInactive=false` | logged in |
| POST / PUT / DELETE | `/api/stations`, `/api/stations/{id}` | ADMIN |
| GET | `/api/pricing`, `/api/pricing/{id}`, `/api/pricing/current` | logged in |
| GET | `/api/pricing/quote?minutes=95&players=3[&creditMinutes=15][&planId=]` | logged in |
| POST / PUT / DELETE | `/api/pricing`, `/api/pricing/{id}` | ADMIN |
| POST | `/api/sessions` (start) | logged in |
| GET | `/api/sessions/active`, `/api/sessions/{id}` | logged in |
| PATCH | `/api/sessions/{id}` (players, customer, notes) | logged in |
| POST | `/api/sessions/{id}/end` (preview bill, saves nothing) | logged in |
| POST | `/api/sessions/{id}/complete` (+ optional `Idempotency-Key` header) | logged in |
| POST | `/api/sessions/{id}/cancel` | operator within 15 min of start; admin any time |
| POST · PATCH · DELETE | `/api/sessions/{id}/items[/{lineId}]` (food on a running session) | logged in |
| GET | `/api/food-items`, `/api/food-categories` | logged in (cost prices admin only) |
| POST / PUT | `/api/food-items[/{id}]`, `/api/food-categories[/{id}]` | ADMIN |
| GET | `/api/inventory/stock?from=&to=`, `/api/inventory/transactions` | logged in |
| POST | `/api/inventory/transactions` (purchase, wastage; opening & adjustment admin only) | logged in |
| POST | `/api/bills` (walk-in food sale, + optional `Idempotency-Key`) | logged in |
| GET | `/api/bills?q=&from=&to=&status=`, `/api/bills/{id}`, `/api/bills/{id}/invoice` | logged in |
| POST | `/api/bills/{id}/cancel` (full refund, stock returned; not for rental bills) | ADMIN |
| GET · POST · PUT | `/api/customers?q=`, `/api/customers[/{id}]`, `/api/customers/{id}/history` | logged in |
| GET | `/api/games` (purchase cost & ROI admin only) | logged in |
| POST / PUT | `/api/games[/{id}]` | ADMIN |
| POST | `/api/rentals` (+ optional `Idempotency-Key`) | logged in |
| GET | `/api/rentals?status=OUT\|OVERDUE\|RETURNED\|CANCELLED&q=`, `/api/rentals/{id}`, `/api/rentals/{id}/return-preview` | logged in |
| POST | `/api/rentals/{id}/return`, `/api/rentals/{id}/cancel` (operator within 15 min; admin while out) | logged in |
| GET | `/api/daily-closing`, `/api/daily-closing/today`, `/api/daily-closing/{date}?openingCash=` | logged in |
| POST | `/api/daily-closing/{date}/close` | logged in |
| POST | `/api/daily-closing/{date}/reopen` | ADMIN |
| GET | `/api/dashboard` (profit, month and charts admin only) | logged in |
| GET | `/api/reports`, `/api/reports/{type}?from=&to=`, `/api/reports/{type}/export?format=csv\|xlsx` | `daily`: logged in; others ADMIN |
| GET | `/api/discounts`, `/api/discounts/usable` | logged in |
| POST / PUT | `/api/discounts[/{id}]` | ADMIN |
| GET · PUT | `/api/settings/tax`, `/api/settings/tax/{GAMING\|FOOD\|RENTAL}` | ADMIN |
| GET | `/api/users` | ADMIN |
| GET | `/actuator/health` | public |

The full planned API is in `docs/ARCHITECTURE.md` §6.

## Testing

```bash
cd backend && mvn verify     # unit + integration tests against a real MySQL 8.4 (Testcontainers; Docker required)
cd frontend && npm test      # unit tests
cd frontend && npm run lint && npm run build
```

Integration tests start a throwaway MySQL container and run the real Flyway migrations, so they
test the actual schema, constraints and SQL, not an in-memory imitation. Concurrency tests release
several requests at once (simultaneous starts on one PS5, simultaneous bill completions, retried
requests, two rentals of one disc, sales racing the daily closing) and check that exactly one succeeds,
nothing is double-billed and no sale escapes a closed day's totals.

## Production deployment

Checklist before using at the cafe:

1. `SPRING_PROFILES_ACTIVE=prod`; strong unique `MYSQL_*` passwords; `JWT_SECRET` from `openssl rand -base64 48`.
2. Set `APP_BOOTSTRAP_ADMIN_PASSWORD`, log in, change the password, then create named operator accounts (one per person).
3. `API_DOCS_ENABLED=false`.
4. Keep MySQL and backend ports bound to `127.0.0.1` (default). Only the frontend port should be reachable on the LAN.
5. Do not expose the app to the internet without HTTPS (put a TLS reverse proxy such as Caddy or Traefik in front).
6. Set up automated daily backups (below) and test a restore.
7. Use a UPS for the counter PC. `restart: unless-stopped` brings the stack back after a reboot once Docker starts.
8. Review the seed data (prices, GST, cost prices) in the admin panel.

Logs: container stdout (`docker compose logs`) plus, in `prod`, rolling files in the `backend-logs`
volume (30 days, 1 GB cap). Passwords and tokens are never logged.

## Backup & restore

```bash
scripts/backup-db.sh                 # writes backups/console_adda_<timestamp>.sql.gz
scripts/restore-db.sh backups/console_adda_2026-10-02_230000.sql.gz   # asks for confirmation
```

* The backup uses `mysqldump --single-transaction`: a consistent snapshot without locking tables, so it is safe during business hours.
* The script verifies the dump finished (`Dump completed` marker) and fails loudly otherwise.
* Restore stops the backend, replaces the database, and restarts it.

Equivalent raw commands:

```bash
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers --no-tablespaces -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip > backup.sql.gz
gunzip -c backup.sql.gz | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

**Recommended strategy:** run the backup nightly after closing (cron on Linux/macOS:
`30 2 * * * cd /path/to/repo && scripts/backup-db.sh`; Task Scheduler with WSL/Git Bash on Windows).
Keep 30 days locally **and** copy them off the machine (Google Drive, external disk). Test a restore once a month.
Backups contain customer names and mobile numbers, so store them securely.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | Architecture, schema, Docker, Flyway, authentication, app shell | ✅ Done |
| 2 | Stations admin, pricing master, billing & rounding engine, sessions | ✅ Done |
| 3 | Food, inventory, bills, discounts, GST, invoice printing | ✅ Done |
| 4 | Customers, game rentals, reports, dashboard, daily closing | ✅ Done |
| 5 | Admin panel, audit log, security hardening | Next |
| 6 | Full testing, bug fixing, UI polish, production review | Planned |
