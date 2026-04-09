#!/usr/bin/env bash
# =============================================================================
# create_github_issues.sh
# Creates GitHub Issues for all Digital ID API tasks, assigns them evenly
# across 4 team members (round-robin), adds every issue to Project #4,
# and sets the project Status field accordingly.
#
# Prerequisites:
#   brew install gh && gh auth login
#
# Usage:
#   chmod +x create_github_issues.sh && ./create_github_issues.sh
# =============================================================================

set -eo pipefail

REPO="sivatarun24/digital-id-api"
PROJECT_NUMBER=5
OWNER="sivatarun24"

# Team members — round-robin assignment
USERS=("sivatarun24" "mopidevi18" "sujaynalimela" "Vince-Frazzini")
USER_IDX=0

# Project GraphQL IDs (populated by init_project_fields)
PROJECT_ID=""
STATUS_FIELD_ID=""
STATUS_DONE_ID=""
STATUS_TODO_ID=""

# Colours
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()  { echo -e "${CYAN}[INFO]${RESET}  $*"; }
ok()   { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn() { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
die()  { echo -e "${RED}[ERROR]${RESET} $*" >&2; exit 1; }

# ── Preflight ─────────────────────────────────────────────────────────────────
check_deps() {
  command -v gh &>/dev/null  || die "gh CLI not found. Run: brew install gh && gh auth login"
  gh auth status &>/dev/null || die "gh not authenticated. Run: gh auth login"
  command -v python3 &>/dev/null || die "python3 not found."
  ok "gh CLI ready"
}

# ── Fetch project node ID and Status field/option IDs via gh CLI ──────────────
init_project_fields() {
  log "Fetching project #${PROJECT_NUMBER} metadata ..."

  PROJECT_ID=$(gh project list --owner "$OWNER" --format json 2>/dev/null \
    | python3 -c "
import sys, json
data = json.load(sys.stdin)
for p in data.get('projects', []):
    if str(p['number']) == '${PROJECT_NUMBER}':
        print(p['id'])
        break
")
  [[ -z "$PROJECT_ID" ]] && die "Project #${PROJECT_NUMBER} not found for ${OWNER}."
  ok "Project #${PROJECT_NUMBER} confirmed (ID: ${PROJECT_ID})"

  log "Fetching Status field metadata ..."
  local fields_json
  fields_json=$(gh project field-list "$PROJECT_NUMBER" --owner "$OWNER" --format json 2>/dev/null)

  STATUS_FIELD_ID=$(echo "$fields_json" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for f in data.get('fields', []):
    if f.get('name', '').lower() == 'status':
        print(f['id']); break
")

  STATUS_DONE_ID=$(echo "$fields_json" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for f in data.get('fields', []):
    if f.get('name','').lower() == 'status':
        for opt in f.get('options', []):
            if opt['name'].lower() in ('done', 'completed', 'closed'):
                print(opt['id']); break
        break
")

  STATUS_TODO_ID=$(echo "$fields_json" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for f in data.get('fields', []):
    if f.get('name','').lower() == 'status':
        for opt in f.get('options', []):
            if opt['name'].lower() in ('todo', 'to do', 'backlog', 'open', 'new'):
                print(opt['id']); break
        break
")

  if [[ -n "$STATUS_FIELD_ID" ]]; then
    ok "Status field ready (Done: ${STATUS_DONE_ID:-not found} | Todo: ${STATUS_TODO_ID:-not found})"
  else
    warn "Status field not found — status will not be set on project items"
  fi
}

# ── Ensure labels exist ───────────────────────────────────────────────────────
ensure_labels() {
  log "Creating labels ..."
  local label_defs=(
    "backend:0052cc"           "digital-id-api:1d76db"
    "spring-boot:6db33f"       "java:b07219"
    "auth:0075ca"              "identity:e4e669"
    "documents:d93f0b"         "credentials:0e8a16"
    "admin:b60205"             "inst-admin:fbca04"
    "messaging:c5def5"         "notifications:bfd4f2"
    "activity:fef2c0"          "home:d4c5f9"
    "services:006b75"          "storage:1d76db"
    "security:ee0701"          "infra:5319e7"
    "testing:0052cc"           "ci/cd:e99695"
    "documentation:0075ca"
    "feature:a2eeef"           "bug:d73a4a"
    "enhancement:84b6eb"       "infrastructure:5319e7"
    "marketing:ff7f0e"
    "priority: high:b60205"    "priority: medium:fbca04"
    "priority: low:0e8a16"
    "status: completed:0e8a16" "status: pending:e4e669"
  )
  for entry in "${label_defs[@]}"; do
    local name="${entry%:*}"
    local color="${entry##*:}"
    gh label create "$name" --repo "$REPO" --color "$color" --force &>/dev/null || true
  done
  ok "Labels ready"
}

# ── Create issue, assign, add to project, set Status field ───────────────────
# Usage: add_item "Title" "area" "type" "priority" "status" "stack" "description"
add_item() {
  local title="$1"
  local area="$2"
  local type="$3"
  local priority="$4"
  local status="$5"
  # $6 (stack) and $7 (description) intentionally unused — body is empty

  local assignee="${USERS[$USER_IDX]}"
  USER_IDX=$(( (USER_IDX + 1) % ${#USERS[@]} ))

  local labels="backend,digital-id-api,spring-boot,java,${area},${type},priority: ${priority},status: ${status}"

  # 1. Create the issue
  local issue_url
  issue_url=$(gh issue create \
    --repo "$REPO" \
    --title "${title}" \
    --body "" \
    --label "$labels" \
    --assignee "$assignee" \
    2>/dev/null) || { warn "Failed to create: ${title}"; return; }

  local issue_num
  issue_num=$(basename "$issue_url")

  # 2. Close if completed
  if [[ "$status" == "completed" ]]; then
    gh issue close "$issue_num" --repo "$REPO" &>/dev/null || true
  fi

  # 3. Get issue node ID (needed for GraphQL project mutation)
  local issue_node_id
  issue_node_id=$(gh issue view "$issue_num" --repo "$REPO" --json id --jq '.id' 2>/dev/null) || true

  # 4. Add issue to project via GraphQL and capture item node ID
  local item_id=""
  if [[ -n "$PROJECT_ID" && -n "$issue_node_id" ]]; then
    item_id=$(gh api graphql \
      -f projectId="$PROJECT_ID" \
      -f contentId="$issue_node_id" \
      -f query='mutation($projectId: ID!, $contentId: ID!) { addProjectV2ItemById(input: {projectId: $projectId, contentId: $contentId}) { item { id } } }' \
      --jq '.data.addProjectV2ItemById.item.id' 2>/dev/null) || true
  fi

  # 5. Set the project Status field on the item
  if [[ -n "$item_id" && -n "$STATUS_FIELD_ID" && -n "$PROJECT_ID" ]]; then
    local option_id=""
    if [[ "$status" == "completed" ]]; then
      option_id="${STATUS_DONE_ID}"
    else
      option_id="${STATUS_TODO_ID}"
    fi
    if [[ -n "$option_id" ]]; then
      gh api graphql \
        -f itemId="$item_id" \
        -f fieldId="$STATUS_FIELD_ID" \
        -f projectId="$PROJECT_ID" \
        -f optionId="$option_id" \
        -f query='mutation($itemId: ID!, $fieldId: ID!, $projectId: ID!, $optionId: String!) { updateProjectV2ItemFieldValue(input: { projectId: $projectId, itemId: $itemId, fieldId: $fieldId, value: { singleSelectOptionId: $optionId } }) { projectV2Item { id } } }' \
        &>/dev/null || true
    fi
  fi

  if [[ -n "$item_id" ]]; then
    ok "#${issue_num} → @${assignee} | ${status} [${area}] ${title}"
  else
    warn "#${issue_num} created but could not add to project: ${title}"
  fi
}

# =============================================================================
# MAIN
# =============================================================================
main() {
  echo -e "\n${BOLD}Digital ID API — GitHub Issue Creator${RESET}"
  echo "========================================"
  echo -e "  Repo    : ${CYAN}${REPO}${RESET}"
  echo -e "  Project : ${CYAN}#${PROJECT_NUMBER}${RESET}"
  echo -e "  Team    : ${CYAN}${USERS[*]}${RESET}"
  echo -e "  Total   : ${CYAN}65 tasks (49 completed · 16 pending)${RESET}\n"

  check_deps
  ensure_labels
  init_project_fields

  # ── COMPLETED (49) ──────────────────────────────────────────────────────────
  echo ""
  log "Creating COMPLETED issues (will be closed) ..."
  echo "─────────────────────────────────────────────────────────────"

  add_item \
    "User registration with email verification flow" \
    "auth" "feature" "high" "completed" \
    "\`Spring Boot\` \`JavaMailSender\` \`MySQL\` \`JWT\`" \
    "Register a new user account and send an email verification link before activating the account."

  add_item \
    "JWT token generation and refresh token system" \
    "auth" "feature" "high" "completed" \
    "\`Spring Security\` \`JJWT\` \`Redis\`" \
    "Issue signed JWTs on login and support refresh-token rotation to maintain sessions."

  add_item \
    "Login with multiple identifiers (username / email / phone)" \
    "auth" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Allow users to authenticate using their username, email address, or phone number."

  add_item \
    "Logout endpoint" \
    "auth" "feature" "medium" "completed" \
    "\`Spring Security\` \`JWT\` \`Redis\`" \
    "Invalidate the active JWT / refresh token on logout."

  add_item \
    "Change password with OTP verification" \
    "auth" "feature" "high" "completed" \
    "\`Spring Boot\` \`JavaMailSender\` \`Redis\`" \
    "Allow authenticated users to change their password after verifying a one-time passcode sent to their email."

  add_item \
    "Forgot password / reset password flow" \
    "auth" "feature" "high" "completed" \
    "\`Spring Boot\` \`JavaMailSender\` \`MySQL\`" \
    "Send a time-limited password-reset link to the user's registered email."

  add_item \
    "Two-factor authentication (TOTP) — setup, enable, disable" \
    "auth" "feature" "high" "completed" \
    "\`Spring Boot\` \`TOTP\` \`Google Authenticator compatible\`" \
    "Let users scan a QR code in an authenticator app and require TOTP codes at login."

  add_item \
    "Update user profile endpoint" \
    "auth" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Allow users to update their display name, phone number, and other profile fields."

  add_item \
    "Account deletion with password confirmation" \
    "auth" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Permanently delete a user account after re-confirming the password."

  add_item \
    "Check username / email / phone availability endpoint" \
    "auth" "feature" "low" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Real-time availability check during registration to prevent duplicate identifiers."

  add_item \
    "Email verification resend endpoint" \
    "auth" "feature" "low" "completed" \
    "\`Spring Boot\` \`JavaMailSender\`" \
    "Let unverified users request a new verification email if the original expired."

  add_item \
    "Identity verification submission (front / back / selfie upload)" \
    "identity" "feature" "high" "completed" \
    "\`Spring Boot\` \`Multipart\` \`GCS\` \`MySQL\`" \
    "Accept government-issued ID images and a selfie photo to begin the identity verification process."

  add_item \
    "Verification status tracking (PENDING / APPROVED / REJECTED / EXPIRED)" \
    "identity" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Track and expose the review state of each identity verification submission."

  add_item \
    "Support multiple ID types (Drivers License, Passport, State ID, Military ID)" \
    "identity" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Allow users to submit different government-issued ID types during identity verification."

  add_item \
    "General document upload and management" \
    "documents" "feature" "high" "completed" \
    "\`Spring Boot\` \`Multipart\` \`GCS\` \`MySQL\`" \
    "Upload, list, and manage user documents with status tracking."

  add_item \
    "Document file replacement endpoint" \
    "documents" "feature" "medium" "completed" \
    "\`Spring Boot\` \`GCS\` \`Multipart\`" \
    "Replace an existing document file without losing its metadata or review history."

  add_item \
    "Document deletion by user" \
    "documents" "feature" "medium" "completed" \
    "\`Spring Boot\` \`GCS\` \`JPA\`" \
    "Allow users to delete their own documents and remove the associated file from storage."

  add_item \
    "Multi-type credential verification system (8 credential types)" \
    "credentials" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Support Government, Healthcare, Military, First Responder, Teacher, Student, Senior, and Nonprofit credential types."

  add_item \
    "Credential start and submit workflow" \
    "credentials" "feature" "high" "completed" \
    "\`Spring Boot\` \`Multipart\` \`GCS\` \`JPA\`" \
    "Two-step credential flow: start (capture details) then submit (upload supporting document)."

  add_item \
    "Admin dashboard with platform statistics" \
    "admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Overview endpoint returning total users, pending verifications, active institutions, and recent activity."

  add_item \
    "User management — list, create, update status/role, delete" \
    "admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Full CRUD for user accounts including role assignment and account status toggling."

  add_item \
    "Identity verification review and approval / rejection" \
    "admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`GCS\`" \
    "Admin workflow to review submitted ID documents and approve or reject with a reason."

  add_item \
    "Credential review and approval / rejection" \
    "admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`GCS\`" \
    "Admin workflow to review credential submissions and approve or reject with notes."

  add_item \
    "Document review and approval / rejection" \
    "admin" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`GCS\`" \
    "Admin workflow to review uploaded documents and update their status."

  add_item \
    "Institution creation, update, and deletion" \
    "admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Manage institutional accounts including creation, profile updates, and removal."

  add_item \
    "Institution permission management (fine-grained access control)" \
    "admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Grant or revoke granular permissions per institution (view identities, review credentials, etc.)."

  add_item \
    "Assign admin to institution" \
    "admin" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Promote a user to INST_ADMIN role and link them to a specific institution."

  add_item \
    "Marketing email template management with 10 default templates" \
    "admin" "marketing" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\` \`JavaMailSender\`" \
    "CRUD for HTML email templates; 10 defaults are auto-seeded on first run."

  add_item \
    "Marketing campaign creation, update, cancel, and send" \
    "admin" "marketing" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`JavaMailSender\` \`MySQL\`" \
    "Create targeted email campaigns using saved templates and send to opted-in users."

  add_item \
    "Institution admin portal with scoped access" \
    "inst-admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`Spring Security\` \`JPA\`" \
    "Dedicated portal for institution admins with data scoped to their institution only."

  add_item \
    "Institution-scoped verification / credential / document review" \
    "inst-admin" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`GCS\`" \
    "Institution admins can review submissions from their own members, subject to granted permissions."

  add_item \
    "Institution member listing and user detail view" \
    "inst-admin" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "List all users belonging to an institution and view their individual profile and submission status."

  add_item \
    "Institution stats dashboard" \
    "inst-admin" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Institution-scoped metrics: member count, pending/approved verifications and credentials."

  add_item \
    "User-to-admin support message system" \
    "messaging" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Users can send messages to admin or institution admin; admins have an inbox with read/delete actions."

  add_item \
    "Admin info requests with file upload response" \
    "messaging" "feature" "medium" "completed" \
    "\`Spring Boot\` \`Multipart\` \`GCS\` \`JPA\`" \
    "Admins can request additional documents from users; users respond by uploading files."

  add_item \
    "Notification creation, read/unread toggle, mark-all-read" \
    "notifications" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "In-app notification system with unread badge count, individual toggle, and bulk mark-as-read."

  add_item \
    "Audit log for all user actions with IP and user-agent tracking" \
    "activity" "feature" "high" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Immutable audit trail recording every significant user action with IP address and user-agent string."

  add_item \
    "User dashboard with credential wallet" \
    "home" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Home endpoint returning summary counts, recent activity, unread notifications, and the credential wallet."

  add_item \
    "External service connect / disconnect framework" \
    "services" "feature" "medium" "completed" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Framework for connecting and disconnecting third-party services per user, with connection tracking."

  add_item \
    "Google Cloud Storage integration for production" \
    "storage" "infrastructure" "high" "completed" \
    "\`Google Cloud Storage\` \`Spring Boot\` \`ADC\`" \
    "Production file storage backend using GCS buckets with Application Default Credentials auth."

  add_item \
    "Local filesystem storage for dev / test" \
    "storage" "infrastructure" "medium" "completed" \
    "\`Spring Boot\` \`Spring Profiles\`" \
    "Dev-profile storage backend writing files to local disk, avoiding GCS dependency in development."

  add_item \
    "Role-based access control (USER, ADMIN, INST_ADMIN)" \
    "security" "security" "high" "completed" \
    "\`Spring Security\` \`JWT\` \`Spring Boot\`" \
    "Three-tier RBAC enforced at the HTTP security filter chain level on all API endpoints."

  add_item \
    "Management endpoints protected via secret header filter" \
    "security" "security" "high" "completed" \
    "\`Spring Security\` \`Actuator\` \`Prometheus\`" \
    "Actuator and Prometheus endpoints require a shared X-Management-Secret header to prevent public exposure."

  add_item \
    "Redis caching integration" \
    "infra" "infrastructure" "medium" "completed" \
    "\`Redis\` \`Spring Data Redis\` \`Spring Boot\`" \
    "Redis wired as the caching layer; configurable via environment variables for dev and prod."

  add_item \
    "Apache Kafka event publishing infrastructure" \
    "infra" "infrastructure" "medium" "completed" \
    "\`Apache Kafka\` \`Spring Kafka\` \`Spring Boot\`" \
    "Kafka producer infrastructure set up with topic configuration; can be toggled off via config."

  add_item \
    "Prometheus metrics and Actuator health endpoints" \
    "infra" "infrastructure" "medium" "completed" \
    "\`Spring Actuator\` \`Micrometer\` \`Prometheus\`" \
    "Expose /actuator/health, /actuator/metrics, and /actuator/prometheus for monitoring."

  add_item \
    "Environment-aware config — dev / prod profiles" \
    "infra" "infrastructure" "high" "completed" \
    "\`Spring Profiles\` \`Spring Boot\` \`Cloud SQL\`" \
    "Separate application-dev.properties and application-prod.properties with env-variable injection in prod."

  add_item \
    "Unit tests — AuthService, JwtService, EmailService, DocumentService" \
    "testing" "testing" "high" "completed" \
    "\`JUnit 5\` \`Mockito\` \`Spring Test\`" \
    "Unit-level test coverage for core service classes using mocked dependencies."

  add_item \
    "Controller tests for AuthApiController" \
    "testing" "testing" "medium" "completed" \
    "\`Spring MockMvc\` \`JUnit 5\` \`Mockito\`" \
    "MockMvc-based tests covering login, register, and 2FA endpoints with valid and invalid payloads."

  # ── PENDING (16) ─────────────────────────────────────────────────────────────
  echo ""
  log "Creating PENDING issues ..."
  echo "─────────────────────────────────────────────────────────────"

  add_item \
    "Fix typo in package name: repositroy → repository" \
    "bug" "bug" "high" "pending" \
    "\`Java\` \`Spring Boot\` \`Refactor\`" \
    "Package com.digitalid.api.repositroy is misspelled; rename to repository and update all imports."

  add_item \
    "Fix typo in class name: SecutityConfig → SecurityConfig" \
    "bug" "bug" "high" "pending" \
    "\`Java\` \`Spring Security\`" \
    "SecutityConfig class name is misspelled; rename to SecurityConfig for clarity and convention."

  add_item \
    "Add rate limiting on auth endpoints (login, OTP, password reset)" \
    "security" "security" "high" "pending" \
    "\`Spring Boot\` \`Bucket4j\` \`Redis\`" \
    "Prevent brute-force attacks by throttling repeated requests to login, OTP, and password-reset endpoints."

  add_item \
    "Remove hardcoded credentials from application-dev.properties" \
    "security" "security" "high" "pending" \
    "\`Spring Boot\` \`Spring Profiles\` \`.env\`" \
    "Gmail SMTP password and other secrets are hardcoded in dev config; move to environment variables or .env file."

  add_item \
    "Implement 2FA recovery / backup codes" \
    "auth" "feature" "medium" "pending" \
    "\`Spring Boot\` \`TOTP\` \`MySQL\`" \
    "Generate one-time backup codes during 2FA setup so users can recover access if they lose their authenticator app."

  add_item \
    "Complete scheduled campaign execution with cron job" \
    "admin" "marketing" "medium" "pending" \
    "\`Spring Boot\` \`Spring Scheduler\` \`JavaMailSender\` \`MySQL\`" \
    "The sendCampaign() method exists but the scheduled/cron-based campaign dispatch is not fully implemented."

  add_item \
    "Implement Kafka event producers and consumers" \
    "infra" "infrastructure" "medium" "pending" \
    "\`Apache Kafka\` \`Spring Kafka\` \`Spring Boot\`" \
    "Kafka infrastructure is wired but no event producers or consumers are implemented; add domain event publishing."

  add_item \
    "Add Swagger / OpenAPI documentation" \
    "documentation" "documentation" "medium" "pending" \
    "\`SpringDoc OpenAPI\` \`Swagger UI\` \`Spring Boot\`" \
    "Annotate all controllers with OpenAPI spec so the API is self-documented and explorable via Swagger UI."

  add_item \
    "Create project README" \
    "documentation" "documentation" "medium" "pending" \
    "\`Markdown\` \`GitHub\`" \
    "Write a README covering project setup, environment variables, running locally, and deployment instructions."

  add_item \
    "Add controller tests for Admin, InstAdmin, Credential, Identity endpoints" \
    "testing" "testing" "high" "pending" \
    "\`Spring MockMvc\` \`JUnit 5\` \`Mockito\`" \
    "Extend test coverage to AdminController, InstAdminController, CredentialController, and IdentityVerificationController."

  add_item \
    "Add integration tests with real database" \
    "testing" "testing" "medium" "pending" \
    "\`Spring Boot Test\` \`Testcontainers\` \`MySQL\`" \
    "Spin up a real MySQL instance via Testcontainers for integration tests to catch issues that mocks miss."

  add_item \
    "WebSocket support for real-time notifications" \
    "feature" "feature" "low" "pending" \
    "\`Spring WebSocket\` \`STOMP\` \`Spring Boot\`" \
    "Push notification updates to the frontend in real time instead of requiring the client to poll."

  add_item \
    "Full-text search across users / documents / verifications" \
    "feature" "feature" "low" "pending" \
    "\`Spring Boot\` \`JPA\` \`MySQL FULLTEXT\`" \
    "Enable keyword search across user profiles, document names, and verification records in admin views."

  add_item \
    "Batch admin operations — bulk approve / bulk delete" \
    "feature" "feature" "low" "pending" \
    "\`Spring Boot\` \`JPA\` \`MySQL\`" \
    "Allow admins to approve or delete multiple verifications/credentials/documents in a single request."

  add_item \
    "Credential auto-expiry background job" \
    "feature" "feature" "medium" "pending" \
    "\`Spring Scheduler\` \`JPA\` \`MySQL\`" \
    "Scheduled job to mark credentials as EXPIRED when their validity period lapses and notify the user."

  add_item \
    "Commit and integrate new Marketing feature files" \
    "ci/cd" "enhancement" "high" "pending" \
    "\`Git\` \`GitHub Actions\` \`Spring Boot\`" \
    "MarketingCampaign, MarketingTemplate, MarketingCampaignRepository, MarketingTemplateRepository, and MarketingService are untracked — commit and wire them up."

  # ── Summary ──────────────────────────────────────────────────────────────────
  echo ""
  echo -e "${BOLD}════════════════════════════════════════${RESET}"
  ok "65 issues created and added to Project #${PROJECT_NUMBER} with Status set"
  echo ""
  echo -e "  Assignment breakdown (round-robin):"
  echo -e "    ${CYAN}sivatarun24${RESET}   → 17 tasks"
  echo -e "    ${CYAN}mopidevi18${RESET}    → 16 tasks"
  echo -e "    ${CYAN}sujaynalimela${RESET} → 16 tasks"
  echo -e "    ${CYAN}Vince-Frazzini${RESET}→ 16 tasks"
  echo ""
  echo -e "  Issues  : ${CYAN}https://github.com/${REPO}/issues${RESET}"
  echo -e "  Project : ${CYAN}https://github.com/users/${OWNER}/projects/${PROJECT_NUMBER}${RESET}"
}

main
