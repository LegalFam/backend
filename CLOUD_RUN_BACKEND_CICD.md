# Backend Cloud Run Deployment + CI/CD

This guide deploys the LegalFam Spring Boot backend to Google Cloud Run and sets up CI/CD from the backend repository.

Assumptions:

- Database, n8n, and RabbitMQ are already deployed.
- The backend repository root is this directory.
- The backend connects to PostgreSQL by host/port using `DB_HOST` and `DB_PORT`.
- RabbitMQ is reachable through private VPC egress.
- Runtime secrets are stored in Google Secret Manager, not in GitHub.

## 1. Confirm Values

Confirm these before running commands:

```sh
export PROJECT_ID="legalfam-497502"
export PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
export REGION="us-central1"
export ARTIFACT_REPOSITORY="legalfam"
export BACKEND_SERVICE="legalfam-backend"
export BACKEND_IMAGE="$REGION-docker.pkg.dev/$PROJECT_ID/$ARTIFACT_REPOSITORY/backend"

export NETWORK="default"
export SUBNET="default"

export RUNTIME_SA_NAME="legalfam-backend-runtime"
export DEPLOY_SA_NAME="legalfam-backend-deployer"
export RUNTIME_SA="$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
export DEPLOY_SA="$DEPLOY_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
export CLOUDBUILD_SA="$PROJECT_NUMBER-compute@developer.gserviceaccount.com"
export CLOUDBUILD_BUCKET="${PROJECT_ID}_cloudbuild"

export GITHUB_REPO="LegalFam/backend"

gcloud config set project "$PROJECT_ID"
```

Use the real GitHub repository full name in `GITHUB_REPO`, for example `LegalFam/backend`.

## 2. Enable APIs

```sh
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  iamcredentials.googleapis.com \
  secretmanager.googleapis.com \
  cloudresourcemanager.googleapis.com \
  compute.googleapis.com
```

## 3. Create Artifact Registry

```sh
gcloud artifacts repositories create "$ARTIFACT_REPOSITORY" \
  --repository-format=docker \
  --location="$REGION"
```

If the repository already exists, keep using it.

## 4. Create Service Accounts

```sh
gcloud iam service-accounts create "$RUNTIME_SA_NAME" \
  --display-name="LegalFam backend runtime"

gcloud iam service-accounts create "$DEPLOY_SA_NAME" \
  --display-name="LegalFam backend GitHub deployer"
```

Grant deployment permissions:

```sh
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOY_SA" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOY_SA" \
  --role="roles/artifactregistry.writer"

gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$DEPLOY_SA" \
  --role="roles/iam.serviceAccountUser"
```

Grant runtime access to Secret Manager:

```sh
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$RUNTIME_SA" \
  --role="roles/secretmanager.secretAccessor"
```

Grant Cloud Build permissions. `gcloud builds submit` uploads the source archive to the Cloud Build staging bucket, then the Cloud Build service account reads that archive, builds the image, pushes it to Artifact Registry, and writes logs.

```sh
gcloud storage buckets add-iam-policy-binding "gs://$CLOUDBUILD_BUCKET" \
  --member="serviceAccount:$CLOUDBUILD_SA" \
  --role="roles/storage.objectViewer"

gcloud storage buckets add-iam-policy-binding "gs://$CLOUDBUILD_BUCKET" \
  --member="serviceAccount:$CLOUDBUILD_SA" \
  --role="roles/storage.objectAdmin"

gcloud artifacts repositories add-iam-policy-binding "$ARTIFACT_REPOSITORY" \
  --location="$REGION" \
  --member="serviceAccount:$CLOUDBUILD_SA" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$CLOUDBUILD_SA" \
  --role="roles/logging.logWriter"
```

If your project uses the newer dedicated Cloud Build service account instead of the Compute Engine default service account, replace `CLOUDBUILD_SA` with:

```sh
export CLOUDBUILD_SA="$PROJECT_NUMBER@cloudbuild.gserviceaccount.com"
```

## 5. Create Runtime Secrets

Create each secret once. Replace values locally before running these commands.

```sh
printf '11A7EY(EdROnz"J*' \
  | gcloud secrets create backend-db-password --data-file=-

printf '14daba1998f34e9217ef5baad7b1633a7e8e7f35cfe9df2a213b990f40747f66' \
  | gcloud secrets create backend-jwt-secret --data-file=-

printf 'a1210c2a2408da350e8e864c7e7e6e8915716c8aeb0599a522bfb055ee3ce902' \
  | gcloud secrets create backend-n8n-auth-token --data-file=-

printf 'APP_USR-4940200576276051-060322-1bf48ea2c1b318caab7e21da36bbfff0-3021887873' \
  | gcloud secrets create backend-mercado-pago-access-token --data-file=-

printf 'MnJxSWFxcGp3SzRXWXhtTkh6RVpJOEs1NkQ5dUhRSDQ=' \
  | gcloud secrets create backend-rabbitmq-password --data-file=-
```

To update an existing secret later:

```sh
printf 'new-secret-value' \
  | gcloud secrets versions add backend-db-password --data-file=-
```

## 6. Enable Private IP For Cloud SQL

The backend currently builds the PostgreSQL JDBC URL from `DB_HOST`, `DB_PORT`, and `DB_NAME`:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
```

Because of that, `DB_HOST` must be a TCP host or IP address. Do not use the Cloud SQL connection name as `DB_HOST`.

Do not use this value as `DB_HOST`:

```text
legalfam-497502:us-central1:legalfam
```

That value is the Cloud SQL connection name. It is used by Cloud SQL Auth Proxy, Cloud SQL connectors, or Cloud Run's Cloud SQL integration, not by the current plain PostgreSQL JDBC URL.

Check the current Cloud SQL network state:

```sh
gcloud sql instances describe legalfam \
  --format="json(ipAddresses,settings.ipConfiguration.privateNetwork)"
```

If the output only has `PRIMARY` and `OUTGOING`, private IP is not enabled yet:

```json
{
  "ipAddresses": [
    {
      "ipAddress": "34.132.58.211",
      "type": "PRIMARY"
    },
    {
      "ipAddress": "104.197.110.176",
      "type": "OUTGOING"
    }
  ]
}
```

Enable the Service Networking API:

```sh
gcloud services enable servicenetworking.googleapis.com
```

Create an allocated IP range for Private Service Access on the `default` VPC:

```sh
gcloud compute addresses create google-managed-services-default \
  --global \
  --purpose=VPC_PEERING \
  --prefix-length=16 \
  --network=default
```

Create the private services connection:

```sh
gcloud services vpc-peerings connect \
  --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services-default \
  --network=default
```

If the range or peering already exists, keep using the existing one.

Attach the Cloud SQL instance to the `default` VPC:

```sh
gcloud sql instances patch legalfam \
  --network=default
```

This keeps the public IP enabled while adding private IP. After the backend is confirmed working through private IP, you can disable public IP with:

```sh
gcloud sql instances patch legalfam \
  --no-assign-ip
```

Get the private IP:

```sh
gcloud sql instances describe legalfam \
  --format="json(ipAddresses)"
```

Use the `ipAddress` whose `type` is `PRIVATE`:

```json
{
  "ipAddress": "10.x.x.x",
  "type": "PRIVATE"
}
```

That value is the backend database host:

```sh
export DB_HOST="10.x.x.x"
export DB_PORT="5432"
```

Cloud Run must use the same VPC path:

```sh
gcloud run services update "$BACKEND_SERVICE" \
  --region "$REGION" \
  --network default \
  --subnet default \
  --vpc-egress private-ranges-only
```

## 7. Confirm Private Connectivity Inputs

Get the RabbitMQ internal IP:

```sh
gcloud compute instances describe legalfam-rabbitmq \
  --zone=us-central1-a \
  --format="get(networkInterfaces[0].networkIP)"
```

Confirm these backend runtime values:

```sh
export DB_HOST="10.2.0.3"
export DB_PORT="5432"
export DB_NAME="legalfam"
export DB_USER="postgresql"

export N8N_WEBHOOK_URL="https://legalfam-n8n-898116999837.us-central1.run.app/webhook/chat-process"
export RABBITMQ_HOST="10.128.0.44"
export RABBITMQ_USER="legalfam"
```

The database schema is manual. Apply these SQL files before serving traffic:

```sh
database/schema.sql
database/chat_outbox_receipt_migration.sql
```

## 8. Manual First Deploy

From the backend repository root:

```sh
export IMAGE_TAG="$(date +%Y%m%d-%H%M%S)"
export IMAGE="$BACKEND_IMAGE:$IMAGE_TAG"

gcloud builds submit . \
  --tag "$IMAGE"
```

Cloud Build is the default build path. It does not depend on the user's local operating system, local CPU architecture, or Docker Desktop state.

Deploy to Cloud Run:

```sh
gcloud run deploy "$BACKEND_SERVICE" \
  --image "$IMAGE" \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated \
  --service-account "$RUNTIME_SA" \
  --port 8080 \
  --network "$NETWORK" \
  --subnet "$SUBNET" \
  --vpc-egress private-ranges-only \
  --set-env-vars DB_HOST="$DB_HOST" \
  --set-env-vars DB_PORT="$DB_PORT" \
  --set-env-vars DB_NAME="$DB_NAME" \
  --set-env-vars DB_USER="$DB_USER" \
  --set-env-vars DB_POOL_MAX_SIZE="10" \
  --set-env-vars DB_POOL_MIN_IDLE="2" \
  --set-env-vars CORS_ALLOWED_ORIGINS="*" \
  --set-env-vars N8N_WEBHOOK_URL="$N8N_WEBHOOK_URL" \
  --set-env-vars N8N_AUTH_HEADER_NAME="X-N8N-Token" \
  --set-env-vars RABBITMQ_HOST="$RABBITMQ_HOST" \
  --set-env-vars RABBITMQ_PORT="5672" \
  --set-env-vars RABBITMQ_USER="$RABBITMQ_USER" \
  --set-env-vars RABBITMQ_VHOST="/" \
  --set-env-vars CHAT_RABBIT_ENABLED="true" \
  --set-secrets DB_PASSWORD="backend-db-password:latest" \
  --set-secrets JWT_SECRET="backend-jwt-secret:latest" \
  --set-secrets N8N_AUTH_TOKEN="backend-n8n-auth-token:latest" \
  --set-secrets MERCADO_PAGO_ACCESS_TOKEN="backend-mercado-pago-access-token:latest" \
  --set-secrets RABBITMQ_PASSWORD="backend-rabbitmq-password:latest"
```

Get the backend URL:

```sh
gcloud run services describe "$BACKEND_SERVICE" \
  --region "$REGION" \
  --format="value(status.url)"
```

## 9. Verify The Deployment

```sh
export BACKEND_URL="$(gcloud run services describe "$BACKEND_SERVICE" --region "$REGION" --format='value(status.url)')"

curl -i "$BACKEND_URL/v3/api-docs"
curl -i "$BACKEND_URL/swagger-ui.html"
```

Smoke test auth:

```sh
curl -X POST "$BACKEND_URL/api/v1/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Password123!","name":"Test User","phone":"900000000"}'
```

After the backend URL is stable, set the frontend API base URL:

```env
VITE_API_BASE_URL=https://replace-with-backend-url/api/v1
```

## 10. Set Up Workload Identity Federation

This lets GitHub Actions deploy without storing a Google service account key.

```sh
gcloud iam workload-identity-pools create github-pool \
  --location="global" \
  --display-name="GitHub Actions Pool"
```

```sh
gcloud iam workload-identity-pools providers create-oidc github-provider \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub Provider" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="attribute.repository == '$GITHUB_REPO'"
```

Allow that GitHub repo to impersonate the deployer service account:

```sh
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/$GITHUB_REPO"
```

Get the provider resource name:

```sh
gcloud iam workload-identity-pools providers describe github-provider \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --format="value(name)"
```

## 11. Configure GitHub Repository Variables

In the backend GitHub repository, go to:

```text
Settings > Secrets and variables > Actions > Variables
```

Create these repository variables:

```text
GCP_PROJECT_ID=legalfam-497502
GCP_PROJECT_NUMBER=<project-number>
GCP_REGION=us-central1
ARTIFACT_REPOSITORY=legalfam
CLOUD_RUN_SERVICE=legalfam-backend
CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT=legalfam-backend-runtime@legalfam-497502.iam.gserviceaccount.com
GCP_DEPLOY_SERVICE_ACCOUNT=legalfam-backend-deployer@legalfam-497502.iam.gserviceaccount.com
GCP_WORKLOAD_IDENTITY_PROVIDER=<provider-resource-name-from-step-10>

DB_HOST=<db-private-host-or-ip>
DB_PORT=5432
DB_NAME=<backend-db-name>
DB_USER=<backend-db-user>
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
CORS_ALLOWED_ORIGINS=*

N8N_WEBHOOK_URL=https://replace-with-n8n-url/webhook/chat-process
N8N_AUTH_HEADER_NAME=X-N8N-Token

RABBITMQ_HOST=<rabbitmq-internal-ip>
RABBITMQ_PORT=5672
RABBITMQ_USER=legalfam
RABBITMQ_VHOST=/
CHAT_RABBIT_ENABLED=true

VPC_NETWORK=default
VPC_SUBNET=default
VPC_EGRESS=private-ranges-only
```

No GitHub secret is required for Google authentication when Workload Identity Federation is configured correctly.

Keep runtime secrets in Secret Manager:

```text
backend-db-password
backend-jwt-secret
backend-n8n-auth-token
backend-mercado-pago-access-token
backend-rabbitmq-password
```

## 12. Add GitHub Actions Workflow

Create this file in the backend repository:

```text
.github/workflows/deploy-backend.yml
```

```yaml
name: Deploy backend to Cloud Run

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read
  id-token: write

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Authenticate to Google Cloud
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ vars.GCP_WORKLOAD_IDENTITY_PROVIDER }}
          service_account: ${{ vars.GCP_DEPLOY_SERVICE_ACCOUNT }}

      - name: Set up gcloud
        uses: google-github-actions/setup-gcloud@v2

      - name: Build image with Cloud Build
        env:
          IMAGE: ${{ vars.GCP_REGION }}-docker.pkg.dev/${{ vars.GCP_PROJECT_ID }}/${{ vars.ARTIFACT_REPOSITORY }}/backend:${{ github.sha }}
        run: |
          gcloud builds submit . \
            --project "${{ vars.GCP_PROJECT_ID }}" \
            --tag "$IMAGE"

      - name: Deploy to Cloud Run
        env:
          IMAGE: ${{ vars.GCP_REGION }}-docker.pkg.dev/${{ vars.GCP_PROJECT_ID }}/${{ vars.ARTIFACT_REPOSITORY }}/backend:${{ github.sha }}
        run: |
          gcloud run deploy "${{ vars.CLOUD_RUN_SERVICE }}" \
            --project "${{ vars.GCP_PROJECT_ID }}" \
            --region "${{ vars.GCP_REGION }}" \
            --platform managed \
            --image "$IMAGE" \
            --allow-unauthenticated \
            --service-account "${{ vars.CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT }}" \
            --port 8080 \
            --network "${{ vars.VPC_NETWORK }}" \
            --subnet "${{ vars.VPC_SUBNET }}" \
            --vpc-egress "${{ vars.VPC_EGRESS }}" \
            --set-env-vars DB_HOST="${{ vars.DB_HOST }}" \
            --set-env-vars DB_PORT="${{ vars.DB_PORT }}" \
            --set-env-vars DB_NAME="${{ vars.DB_NAME }}" \
            --set-env-vars DB_USER="${{ vars.DB_USER }}" \
            --set-env-vars DB_POOL_MAX_SIZE="${{ vars.DB_POOL_MAX_SIZE }}" \
            --set-env-vars DB_POOL_MIN_IDLE="${{ vars.DB_POOL_MIN_IDLE }}" \
            --set-env-vars CORS_ALLOWED_ORIGINS="${{ vars.CORS_ALLOWED_ORIGINS }}" \
            --set-env-vars N8N_WEBHOOK_URL="${{ vars.N8N_WEBHOOK_URL }}" \
            --set-env-vars N8N_AUTH_HEADER_NAME="${{ vars.N8N_AUTH_HEADER_NAME }}" \
            --set-env-vars RABBITMQ_HOST="${{ vars.RABBITMQ_HOST }}" \
            --set-env-vars RABBITMQ_PORT="${{ vars.RABBITMQ_PORT }}" \
            --set-env-vars RABBITMQ_USER="${{ vars.RABBITMQ_USER }}" \
            --set-env-vars RABBITMQ_VHOST="${{ vars.RABBITMQ_VHOST }}" \
            --set-env-vars CHAT_RABBIT_ENABLED="${{ vars.CHAT_RABBIT_ENABLED }}" \
            --set-secrets DB_PASSWORD="backend-db-password:latest" \
            --set-secrets JWT_SECRET="backend-jwt-secret:latest" \
            --set-secrets N8N_AUTH_TOKEN="backend-n8n-auth-token:latest" \
            --set-secrets MERCADO_PAGO_ACCESS_TOKEN="backend-mercado-pago-access-token:latest" \
            --set-secrets RABBITMQ_PASSWORD="backend-rabbitmq-password:latest"
```

## 13. CI/CD Flow

After the workflow is committed:

1. Push to `main`.
2. GitHub Actions authenticates to GCP through Workload Identity Federation.
3. The workflow asks Cloud Build to build the image from `Dockerfile`.
4. The image is pushed to Artifact Registry.
5. Cloud Run is updated to the new image.
6. Runtime configuration is refreshed from GitHub repository variables and Secret Manager.

For production, prefer using a protected GitHub Environment before deploying from `main`.

## 14. Operations

View backend logs:

```sh
gcloud run services logs read "$BACKEND_SERVICE" \
  --region "$REGION" \
  --limit 100
```

Tail logs:

```sh
gcloud beta run services logs tail "$BACKEND_SERVICE" \
  --region "$REGION"
```

Rollback to a previous revision:

```sh
gcloud run revisions list \
  --service "$BACKEND_SERVICE" \
  --region "$REGION"

gcloud run services update-traffic "$BACKEND_SERVICE" \
  --region "$REGION" \
  --to-revisions "REVISION_NAME=100"
```

## 15. Common Issues

If Cloud Run cannot connect to RabbitMQ:

- Confirm `RABBITMQ_HOST` is the VM internal IP.
- Confirm Cloud Run uses the same VPC and subnet.
- Confirm firewall allows TCP `5672` from Cloud Run VPC egress.
- Confirm `CHAT_RABBIT_ENABLED=true`.

If Cloud Run cannot connect to PostgreSQL:

- Confirm `DB_HOST`, `DB_PORT`, `DB_NAME`, and `DB_USER`.
- Confirm `DB_HOST` is the Cloud SQL `PRIVATE` IP, not the Cloud SQL connection name.
- Confirm Cloud SQL private IP is enabled on the same VPC that Cloud Run uses.
- Confirm Cloud Run has `--network`, `--subnet`, and `--vpc-egress private-ranges-only`.
- Confirm `backend-db-password` has the correct current secret version.
- Confirm `database/schema.sql` and `database/chat_outbox_receipt_migration.sql` were applied.

If Cloud Run rejects the image with `Container manifest type ... must support amd64/linux`:

- Rebuild the image with Cloud Build: `gcloud builds submit . --tag "$IMAGE"`.
- Use a new image tag before redeploying.

If Cloud Build cannot read the uploaded source archive:

- Grant `roles/storage.objectViewer` on `gs://$CLOUDBUILD_BUCKET` to `$CLOUDBUILD_SA`.
- If needed, grant `roles/storage.objectAdmin` on the same bucket.

If Cloud Build cannot push the image and shows `artifactregistry.repositories.uploadArtifacts denied`:

- Grant `roles/artifactregistry.writer` on the Artifact Registry repository to `$CLOUDBUILD_SA`.

If Cloud Build says it cannot write logs:

- Grant `roles/logging.logWriter` to `$CLOUDBUILD_SA`.

If authentication fails:

- Confirm `JWT_SECRET` is stable across deployments.
- Confirm the secret is at least 32 characters.

If n8n does not receive chat requests:

- Confirm `N8N_WEBHOOK_URL` ends with `/webhook/chat-process`.
- Confirm `N8N_AUTH_HEADER_NAME` matches the n8n Header Auth credential.
- Confirm `backend-n8n-auth-token` matches the token expected by n8n.
