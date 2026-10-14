# DevOps roadmap (not implemented yet)

The repository is laid out so each step below can be added without restructuring. **None of it exists yet** - this
is a plan. No AWS resources are created by this project; nothing below should be applied without cost approval.

Target pipeline: `GitHub -> Jenkins -> Maven test/build -> Docker build -> Amazon ECR -> Kubernetes -> Helm -> app`.

## What the app already provides

| Need | Where |
|---|---|
| Container images | `backend/Dockerfile`, `frontend/Dockerfile` (multi-stage, non-root) |
| 12-factor config | all settings are environment variables (`.env.example`, `application.yml`) |
| Health probes | `/actuator/health/liveness` and `/actuator/health/readiness` (backend), `/healthz` (frontend) |
| Metrics | `/actuator/prometheus` (JVM, HTTP, Hikari) + custom counters `healthqueue_appointments_booked_total`, `healthqueue_appointments_cancelled_total`, `healthqueue_queue_calls_total` |
| Graceful shutdown | `server.shutdown=graceful` |
| Test commands | `mvn test` (needs Docker for Testcontainers), `npm test`, `npm run build` |
| DB migrations | Flyway runs at backend start-up |

## Suggested phases

1. **Jenkins CI** - `Jenkinsfile`: checkout -> `mvn -B test` (agent needs Docker socket for Testcontainers) ->
   `npm ci && npm test && npm run build` -> `docker build` both images tagged with the git SHA.
   Use Jenkins credentials for registry access; never put secrets in the Jenkinsfile.
2. **Amazon ECR** - push `healthqueue-backend` and `healthqueue-frontend` (enable scan-on-push). Needs an AWS account and
   an IAM role for Jenkins (cost: small storage fee). Ask before creating.
3. **Kubernetes manifests -> Helm chart** (`helm/healthqueue/`): Deployments for backend/frontend, Services, Ingress,
   ConfigMap for non-secret env, Secret (or External Secrets) for `DB_PASSWORD` / `JWT_SECRET`, readiness/liveness probes
   from the table above. Set `BACKEND_URL` on the frontend to the backend Service. For MySQL use a managed database
   (RDS) or a StatefulSet for practice. Test locally first with kind or minikube (free).
   Note: the in-memory SSE hub means >1 backend replica relies on the 15 s polling fallback.
4. **Monitoring** - kube-prometheus-stack via Helm: a `ServiceMonitor` scraping `/actuator/prometheus`, Grafana
   dashboards (JVM, HTTP latency/error rate, bookings per minute, DB pool), alerts on 5xx rate and readiness failures.
   Restrict `/actuator/prometheus` and Swagger to the cluster network.
5. **Terraform (optional, last)** - VPC, EKS, RDS, ECR in a separate `infra/` folder with remote state.
   Review `terraform plan` costs before any `apply`.
