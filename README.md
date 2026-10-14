# HealthQueue

Healthcare **appointment booking and live queue management**. Patients book a slot, receive a queue token
and watch their position in line update live. Doctors and receptionists run the queue.

> HealthQueue manages appointments and queues only. It stores no diagnosis, treatment or other medical
> data, and collects only name, e-mail and a password hash.

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, React Router, Axios, plain CSS (responsive), Vitest + Testing Library |
| Backend | Java 21, Spring Boot 3.4, Spring Web / Data JPA / Security, Bean Validation, Maven |
| Database | MySQL 8 with Flyway migrations |
| Real time | Server-Sent Events (SSE) + polling fallback |
| Ops | Docker, Docker Compose, Spring Actuator + Micrometer/Prometheus metrics, OpenAPI/Swagger |

## Quick start (Docker)

Requires Docker with Compose v2. Nothing else (no Java/Node) is needed to run the app.

```bash
cp .env.example .env          # PowerShell: Copy-Item .env.example .env
# edit .env: set MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET (32+ chars), DEMO_PASSWORD
docker compose up -d --build
```

| URL | What |
|---|---|
| http://localhost:3000 | Web app |
| http://localhost:8080/swagger-ui.html | API documentation (Swagger UI) |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/actuator/health | Health (`/liveness`, `/readiness` sub-paths for Kubernetes probes) |
| http://localhost:8080/actuator/prometheus | Prometheus metrics |

Stop: `docker compose down` (data is kept in the `mysql_data` volume). Wipe everything: `docker compose down -v`.
Logs: `docker compose logs -f backend`.

Generate a JWT secret: `openssl rand -base64 48` (or any random 32+ character string).

### Demo accounts (local demo only)

With `SEED_DEMO_DATA=true` the backend creates demo users on start-up. **All demo accounts use the password you
put in `DEMO_PASSWORD` in `.env`** - nothing is hard-coded in the repository. The addresses use the reserved
`example.com` domain; there is no real patient data.

| Role | E-mail |
|---|---|
| Receptionist / admin | `admin@demo.example.com` |
| Doctor (General Physician, tokens `A-###`) | `doctor.general@demo.example.com` |
| Doctor (Pediatrician, tokens `B-###`) | `doctor.peds@demo.example.com` |
| Patient | `patient@demo.example.com` |
| Patient 2 | `patient2@demo.example.com` |

Set `SEED_DEMO_DATA=false` for anything that is not a local demo. Patients can also self-register at `/register`.
Doctors are created by an admin (Reception / Admin -> Doctors).

`BOOKING_ALLOW_PAST_SLOTS_TODAY=true` (in the local `.env`) lets you book "today" slots that have already
passed so you can demo late in the day. Keep it `false` otherwise.

### Try the main flow in two minutes

1. Log in as `patient@demo.example.com`, open **Doctors -> Book appointment**, pick a date and slot. You get a token such as `A-001`.
2. In another browser/private window log in as `patient2@...` and book a later slot with the same doctor (`A-002`).
3. Log in as `doctor.general@...`, open the doctor dashboard, pick the booking date and press **Call next patient**.
4. Watch patient 1's page switch to "It's your turn" and patient 2's **Patients ahead** drop - without refreshing.
5. Press **Start** and **Complete**, then **Call next patient** again to move on.

## Local development without Docker

```bash
# database (or use any MySQL 8): docker compose up -d mysql   (add a ports mapping for 3306 if you need host access)
# backend
cd backend
export DB_URL="jdbc:mysql://localhost:3306/healthqueue?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USERNAME=... DB_PASSWORD=... JWT_SECRET=... SEED_DEMO_DATA=true DEMO_PASSWORD=...
mvn spring-boot:run

# frontend (Node 20+)
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api to localhost:8080
```

## Tests

```bash
cd backend && mvn test           # 47 tests; integration tests start MySQL 8 with Testcontainers -> Docker must be running
cd frontend && npm test          # 32 tests (Vitest + Testing Library, no backend needed)
```

If Docker is not available the `*IT` classes cannot run; `mvn -Dtest='*Test' test` runs only the unit tests.
Tests are not executed inside `docker build` (the integration tests need Docker); run them in CI.

What is covered: booking + token assignment, queue position / patients-ahead, calling next, completing,
skipping, cancellation excluded from counts, independent queues per doctor and per date, ownership checks
(404 for other patients' appointments), role checks (401/403), validation errors, BCrypt hashing, and
**concurrent bookings** (16 parallel requests -> 16 unique consecutive tokens; 8 parallel requests for one slot
-> exactly one winner and seven clean 409s).

## Repository layout

```
healthcare/
  backend/                 Spring Boot app (Maven)
    src/main/java/com/healthqueue/{config,domain,repository,dto,service,security,web}
    src/main/resources/db/migration/   Flyway migrations (V1 schema, V2 reference data)
    src/test/java/...      unit + Testcontainers integration tests
    Dockerfile
  frontend/                React app (Vite)
    src/{api,auth,components,hooks,pages,utils}
    nginx.conf.template, Dockerfile
  docs/                    design notes (queue rules, DevOps roadmap)
  docker-compose.yml  .env.example  .gitignore  README.md
```

Deviation from the suggested layout: **Flyway migrations live in `backend/src/main/resources/db/migration`**
instead of a top-level `database/` folder. They are packaged inside the jar so every environment (compose,
Kubernetes, tests) migrates the same way with no extra volume mounts or build steps.

## How the queue works

Statuses: `BOOKED -> WAITING -> CALLED -> IN_CONSULTATION -> COMPLETED`, plus `CANCELLED` and `SKIPPED`.

| Action | Who | Transition |
|---|---|---|
| Book | patient | creates `BOOKED`, assigns next token |
| "I have arrived" / check in | patient (own) or admin | `BOOKED -> WAITING` |
| Call next | doctor (own queue) or admin | lowest-token `BOOKED`/`WAITING` -> `CALLED` |
| Start | doctor / admin | `CALLED -> IN_CONSULTATION` |
| Complete | doctor / admin | `CALLED`/`IN_CONSULTATION -> COMPLETED` |
| Skip | doctor / admin | `BOOKED`/`WAITING`/`CALLED -> SKIPPED` |
| Cancel | patient (own, `BOOKED`/`WAITING`, not in the past) or admin (also `CALLED`) | `-> CANCELLED` |

Rules:

* **Tokens** are `<doctor letter>-<number>` (`A-012`). The number is a per-doctor, per-date counter, so doctors and
  dates have independent queues. Numbers are never reused, even after a cancellation.
* **Patients ahead** = appointments of the same doctor and date with a *smaller token* whose status is `BOOKED`
  or `WAITING`. Completed, cancelled and skipped appointments never count. The patient being served is shown as
  **Now serving**, not as "ahead" (token 8 serving, token 12 waiting, tokens 9-11 waiting -> 3 ahead).
* **Estimated wait** = patients ahead x `AVG_CONSULTATION_MINUTES` (default 5). It is always labelled as an estimate.
* Only one patient can be `CALLED`/`IN_CONSULTATION` per doctor at a time; call-next is refused with `409` until the doctor completes or skips.
* One live appointment per patient per doctor per day; a slot holds one live appointment (cancelling frees it).
* Slots are generated from the doctor's working days, hours and slot length. Bookings open `BOOKING_MAX_DAYS_AHEAD` (30) days ahead.

### Concurrency control

* Every queue write (book, cancel, call, complete...) first runs one atomic
  `INSERT ... ON DUPLICATE KEY UPDATE` on the `queue_states` row of that doctor/date. That statement creates the row
  on the first booking of the day and holds an exclusive row lock until the transaction ends, so writers of the same
  queue are serialised while other queues run in parallel. Booking increments the counter in the same statement, and
  a rolled-back booking rolls the counter back too (no gaps, no duplicates).
* Those transactions use `READ_COMMITTED`, so reads after taking the lock see what the previous lock holder committed
  (MySQL's default `REPEATABLE READ` snapshot would not).
* The database is the final referee: unique constraints on `(doctor, date, token)`, `(doctor, date, slot)` and
  `(patient, doctor, date)` (the last two ignore cancelled rows via a generated column). A violation is returned as a clean `409`.

### Real-time updates

Chosen: **Server-Sent Events** - one-way server push over plain HTTP, no extra protocol or broker, and the
browser reconnects automatically. (STOMP/WebSocket would add a broker setup and a second auth channel for no
benefit, as clients never push to the queue.)

* `GET /api/queue/stream?doctorId=&date=` emits a `queue-update` event when that doctor/date queue changes
  (published **after** the transaction commits). The event carries only the queue id - **no personal data** - so the
  endpoint needs no login; each client then re-fetches its own data through the authorised REST API.
* **Polling fallback:** every page also refreshes every 15 s, so updates still arrive if SSE is blocked by a proxy.
* nginx has buffering disabled for the stream route; the server sends a keep-alive comment every 25 s.
* Limitation: the SSE hub is in memory, so with several backend replicas an event only reaches clients connected to
  the replica that handled the change (polling covers the rest). A Redis/DB-backed fan-out is the next step if you scale out.

## API overview

Interactive docs: **Swagger UI** at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`. Use *Authorize* with the `token` from login.
Errors are JSON: `{ timestamp, status, error, message, path, fieldErrors? }`. Lists use `{ content, page, size, totalElements, totalPages }`.

| Method & path | Role | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | register a patient (201) |
| `POST /api/auth/login` | public | returns `{ token, user }` |
| `GET /api/auth/me` | any | current user |
| `GET /api/departments` | public | departments |
| `GET /api/doctors?departmentId&page&size` | public | paged doctor list |
| `GET /api/doctors/{id}` | public | one doctor |
| `GET /api/doctors/{id}/slots?date=YYYY-MM-DD` | public | slots with `available` flag |
| `GET /api/doctor/me` | doctor | own doctor profile |
| `POST /api/appointments` | patient | book `{doctorId,date,slotTime}` -> token |
| `GET /api/appointments/my?page&size` | patient | own appointment history |
| `GET /api/appointments/{id}` | owner / assigned doctor / admin | one appointment |
| `GET /api/appointments/{id}/queue` | owner / assigned doctor / admin | token, now serving, ahead, estimate, status |
| `POST /api/appointments/{id}/check-in` | patient (own) / admin | `BOOKED -> WAITING` |
| `POST /api/appointments/{id}/cancel` | patient (own) / admin | cancel |
| `GET /api/queue/doctors/{id}?date=` | doctor (own) / admin | full day queue with patient names |
| `GET /api/queue/doctors/{id}/summary?date=` | any logged in | now serving + waiting count (no names) |
| `POST /api/queue/doctors/{id}/call-next?date=` | doctor (own) / admin | call next patient |
| `POST /api/queue/appointments/{id}/start` `/complete` `/skip` | doctor (own) / admin | transitions |
| `GET /api/queue/stream?doctorId&date` | public | SSE "queue-update" signals |
| `GET/POST/PUT /api/admin/doctors` | admin | list (incl. inactive), create, update/deactivate doctors |
| `GET /api/admin/appointments?date&doctorId&status&page&size` | admin | daily appointments |
| `GET /api/admin/overview?date=` | admin | per-doctor status counts and now serving |
| `GET /actuator/health`, `/actuator/prometheus` | public | health and metrics |

Status codes: `400` validation, `401` not logged in, `403` wrong role / other doctor's queue, `404` not found
(also used for other patients' appointments so ids cannot be probed), `409` conflicts (slot taken, illegal transition).

## Database

MySQL 8, schema owned by Flyway (`ddl-auto=validate`). Tables: `users` (role `PATIENT|DOCTOR|ADMIN`, BCrypt hash),
`departments`, `doctors` (profile, working hours, token letter), `queue_states` (token counter + lock per doctor/date),
`appointments`. Foreign keys, indexes for the queue queries, and the unique constraints described above are in
`V1__init_schema.sql`; `V2` seeds department names only. Demo users are created by the seeder (not by SQL) so no
password hashes are committed. Never edit an applied migration in a shared environment - add `V3__...`.

## Security notes

* BCrypt password hashes; stateless JWT (HS256, `JWT_SECRET` required, 32+ chars, default lifetime 8 h) sent as `Authorization: Bearer`.
* Roles enforced on the backend with `@PreAuthorize` plus ownership checks in services; the UI only mirrors this.
* CORS: explicit origins from `CORS_ALLOWED_ORIGINS`, never `*`. The bundled nginx proxies `/api`, so the browser sees one origin.
* Public self-registration can only create patients. Failed logins return one generic message and cost the same time for unknown e-mails.
* No credentials in the repository: `.env` is git-ignored, `.env.example` has placeholders only; containers run as non-root users.
* The JWT is kept in `localStorage` (simple, but readable by injected scripts); the app renders no raw HTML and nginx sends basic security headers. Consider httpOnly cookies + CSRF protection for a production hardening pass.

## Known limitations

* No login rate limiting / account lockout, e-mail verification, or password reset.
* No refresh tokens (a token simply expires after `JWT_EXPIRATION_MINUTES`) and no server-side logout/revocation.
* SSE hub is per instance (see above). Queue tokens are issued in booking order, not slot order.
* Doctors cannot be deleted, only deactivated; admins cannot reschedule an appointment (cancel and re-book).
* Swagger UI and `/actuator/prometheus` are unauthenticated for local convenience - restrict them at the ingress/network layer in real deployments.
* Frontend has component/unit tests but no browser end-to-end suite.

## DevOps roadmap

See [docs/devops-roadmap.md](docs/devops-roadmap.md) for the suggested next phases (Jenkins, ECR, Kubernetes,
Helm, Prometheus, Grafana, Terraform). Nothing in this repository creates cloud resources.
