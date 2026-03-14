# Backend deployment (Cloud Run)

## Overview

- **Backend** runs on Cloud Run with **no public access** (`--no-allow-unauthenticated`).
- Only the **frontend** Cloud Run service (and optionally allowed IPs) might call the backend; this is enforced by **IAM** (see below).
- **Metrics** (`/actuator/prometheus`, `/actuator/metrics`) are not exposed publicly: when `MANAGEMENT_SECRET` is set, these endpoints require the `X-Management-Secret` header (e.g. used by Prometheus/Grafana in your VPC).
- **MySQL**: Cloud SQL, connected via `--add-cloudsql-instances` and JDBC Cloud SQL socket factory.
- **Redis**: e.g. Memorystore; set `REDIS_HOST` (and optionally `REDIS_PORT`, `REDIS_PASSWORD`). Use a **VPC connector** on Cloud Run if Redis is in your VPC.
- **Kafka**: set `KAFKA_BOOTSTRAP_SERVERS` (e.g. Confluent Cloud or Kafka in VPC). Use a **VPC connector** if Kafka is in VPC.

## GitHub Actions: required secrets

In the repo **Settings → Secrets and variables → Actions**, add:

| Secret | Description |
|--------|-------------|
| `GCP_PROJECT_ID` | GCP project ID |
| `GCP_REGION` | e.g. `us-central1` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | WIF provider (e.g. `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/...`) |
| `GCP_SERVICE_ACCOUNT` | SA email used by GitHub Actions (e.g. `github-actions-push@PROJECT.iam.gserviceaccount.com`) |
| `CLOUD_SQL_INSTANCE` | Cloud SQL instance connection name: `project:region:instance` |
| `DATABASE_URL` | JDBC URL. For Cloud SQL: `jdbc:mysql:///DB_NAME?cloudSqlInstance=project:region:instance&socketFactory=com.google.cloud.sql.mysql.SocketFactory` |
| `DATABASE_USERNAME` | DB user |
| `DATABASE_PASSWORD` | DB password |
| `JWT_SECRET` | Secret for JWT signing |
| `FRONTEND_URL` | Frontend origin (e.g. `https://your-frontend-xxx.run.app`) for CORS |
| `FRONTEND_SERVICE_ACCOUNT` | Frontend Cloud Run service account (e.g. `PROJECT_NUMBER-compute@developer.gserviceaccount.com` or custom SA). Only this identity can invoke the backend. |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers (e.g. `pkc-xxx.confluent.cloud:9092` or internal host:port) |
| `REDIS_HOST` | Redis host (e.g. Memorystore IP) |
| `REDIS_PORT` | Redis port (default 6379) |
| `REDIS_PASSWORD` | Redis password (optional; leave empty if none) |
| `MANAGEMENT_SECRET` | Secret for scraping metrics (Prometheus uses header `X-Management-Secret: <this value>`); if unset, metrics endpoints are not protected |

Optional variable (Settings → Variables):

- `CLOUD_RUN_SERVICE_FRONTEND`: frontend Cloud Run service name (default `digital-id-frontend`).

## Backend only callable by frontend

1. Backend is deployed with **no-allow-unauthenticated**, so only identities with `roles/run.invoker` on the backend service can call it.
2. The workflow **grants** the frontend service account that role after deploy:
   - Set `FRONTEND_SERVICE_ACCOUNT` to the **frontend** Cloud Run service’s identity (the one used when the frontend runs).
   - The frontend must call the backend with an **identity token** (e.g. `Authorization: Bearer $(gcloud auth print-identity-token)` or your runtime’s equivalent) so Cloud Run sends requests as that SA.
3. For **extra access from specific IPs** (e.g. admin tools), you would put the backend behind a load balancer and use Cloud Armor to allow those IPs; the repo does not configure that by default.

## Metrics not exposed publicly

- Set **MANAGEMENT_SECRET** in Cloud Run env.
- In production, `/actuator/prometheus` and `/actuator/metrics` require the header: `X-Management-Secret: <MANAGEMENT_SECRET>`.
- Configure Prometheus (or Grafana Agent) to send this header when scraping the backend. Only clients that know the secret can read metrics.

## Grafana and Kibana

- **Local / docker-compose**: `docker-compose up` runs Prometheus, Grafana, Elasticsearch, and Kibana. Grafana is on port 3001, Kibana on 5601. Point Grafana to Prometheus; use Kibana to explore Elasticsearch (add log shipping if you want app logs in Elasticsearch).
- **Production**: Deploy Grafana and Kibana (e.g. on Cloud Run or GCE). For **public access**, use IAP or restrict by IP in the ingress. Send backend metrics to Prometheus (with `X-Management-Secret`), and have Grafana use Prometheus as a data source.

## VPC connector (Redis / Kafka in VPC)

If Redis (e.g. Memorystore) or Kafka is in your VPC:

1. Create a **VPC connector** in the same region as Cloud Run.
2. In the deploy step, add to `gcloud run deploy`:
   ```bash
   --vpc-connector=CONNECTOR_NAME --vpc-egress=private-ranges-only
   ```
3. Ensure the connector’s subnet can reach Redis and Kafka.
