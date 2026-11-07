# 💰 ExpenseTracker — Full-Stack Financial Management Platform

A modern full-stack expense tracking and financial analytics platform engineered with a **Java 21 / Spring Boot 3.4 REST API**, **PostgreSQL 16+**, and a **React 18 / TypeScript / Vite 6** frontend. Designed with architectural patterns including stateless JWT authentication, HttpOnly refresh token rotation, Google OAuth2 / OpenID Connect (OIDC) with single-use exchange codes, JPA auditing, and Flyway database migrations.

---

## 📑 Table of Contents

- [Key Features](#-key-features)
- [Technology Stack](#-technology-stack)
- [Architecture Overview](#-architecture-overview)
- [Project Directory Structure](#-project-directory-structure)
- [Prerequisites](#-prerequisites)
- [Local Development Setup](#-local-development-setup)
- [Environment Variables Configuration](#-environment-variables-configuration)
- [Database Setup & Migrations](#-database-setup--migrations)
- [Docker Compose Local Development Stack](#-docker-compose-local-development-stack)
- [API Documentation & Observability](#-api-documentation--observability)
- [Testing & Build](#-testing--build)
- [Security Architecture & Troubleshooting](#-security-architecture--troubleshooting)
- [Known Limitations](#-known-limitations)
- [Future Improvements (Roadmap)](#-future-improvements-roadmap)
- [License](#-license)

---

## ✨ Key Features

### 🔐 Authentication & Identity Management
- **Dual Authentication**: Support for both traditional email/password registration with BCrypt hashing and **Google OAuth2 / OpenID Connect (OIDC)** authentication.
- **In-Memory Access Tokens**: Short-lived JWT access tokens (15-minute lifespan) stored strictly in client memory to eliminate XSS token theft.
- **HttpOnly Refresh Token Rotation**: Long-lived refresh tokens (7-day lifespan) stored in secure, HttpOnly, SameSite cookies with database persistence and rotation on every renewal.
- **One-Time OAuth2 Exchange Code**: Single-use, cryptographically random authorization code exchange preventing token exposure in browser URLs.
- **Account Conflict Prevention**: Explicit detection and handling when authenticating via OAuth: verified local accounts and accounts with existing expense records refuse Google sign-in; unverified local accounts with zero expenses are safely replaced.
- **Email Verification**: Token-based email verification using Spring Mail with configurable token expiration and resend cooldowns.
- **Role-Based Access Control**: Granular endpoint authorization supporting `ROLE_USER` and `ROLE_ADMIN` using method-level `@PreAuthorize` security.

### 📊 Expense Management & Analytics
- **Full Expense Lifecycle**: Create, view, update, and soft-delete expenses scoped strictly to the authenticated user.
- **Structured Categorization**: Predefined financial categories (`FOOD`, `TRAVEL`, `SHOPPING`, `EDUCATION`, `ENTERTAINMENT`, `HEALTH`, `BILLS`, `OTHER`).
- **Pagination & Filtering**: Server-side pagination with rich metadata (`PagedResponse<T>`), category filtering, and ISO date-range parameters.
- **Aggregated Analytics**: Dedicated summary endpoint (`/api/expenses/summary`) providing total expenditure, category breakdowns, and monthly spending trends.
- **JPA Auditing & Soft Deletion**: Automated entity auditing (`createdAt`, `updatedAt`, `version`) and soft deletion (`@SQLDelete`, `@SQLRestriction`) to ensure data integrity.

### 💻 Modern Frontend Experience
- **Interactive UI**: Responsive, accessible interface built with Tailwind CSS, Lucide icons, and Framer Motion micro-interactions.
- **Visual Analytics**: Dynamic financial trend charts and distribution breakdowns powered by Recharts.
- **Data Synchronization**: Efficient server-state management, automated background refetching, and caching via TanStack React Query.
- **3D Visual Elements**: WebGL background visuals powered by Three.js and React Three Fiber.
- **Single-Origin Proxy**: Vite development proxy seamlessly routing `/api`, `/oauth2/authorization`, and `/login/oauth2` to bypass cross-origin cookie restrictions.

---

## 🛠️ Technology Stack

### Frontend
| Component | Technology | Version / Notes |
| :--- | :--- | :--- |
| **Framework** | React | 18.3 |
| **Language** | TypeScript | 5.6 |
| **Build Tool & Dev Server** | Vite | 6.0 |
| **Styling** | Tailwind CSS | 3.4 |
| **State Management** | Zustand | 5.0 (In-memory token & user state) |
| **Server State & Caching** | TanStack React Query | 5.62 |
| **HTTP Client** | Axios | 1.7 (Configured with `withCredentials: true`) |
| **Routing** | React Router | 6.28 |
| **Data Visualization** | Recharts | 2.15 |
| **Animation & 3D** | Framer Motion, Three.js, R3F | 11.15 / 0.186 |
| **Form Handling & Validation** | React Hook Form, Zod | 7.54 / 3.24 |
| **Icons** | Lucide React | 0.468 |

### Backend
| Component | Technology | Version / Notes |
| :--- | :--- | :--- |
| **Runtime** | Java Development Kit (JDK) | 21 (LTS) |
| **Framework** | Spring Boot | 3.4.3 |
| **Security** | Spring Security | 6.4 (Stateless JWT + OAuth2 Client + OIDC) |
| **JWT Library** | JJWT (io.jsonwebtoken) | 0.12.7 (HMAC-SHA signing via `Keys.hmacShaKeyFor`) |
| **Persistence & ORM** | Spring Data JPA / Hibernate | 6.6 |
| **Connection Pooling** | HikariCP | Default HikariCP connection pool |
| **API Documentation** | SpringDoc OpenAPI / Swagger UI | 2.8.5 |
| **Observability** | Spring Boot Actuator, Micrometer | Prometheus metrics scraping |
| **Email Service** | Spring Boot Starter Mail | JavaMailSender with TLS support |
| **Boilerplate Reduction** | Project Lombok | Compile-time bytecode generation |

### Database & DevOps
| Component | Technology | Notes |
| :--- | :--- | :--- |
| **Primary Database** | PostgreSQL | 16+ (Local service or Docker container) |
| **Database Migrations** | Flyway | Versioned migrations (`V1` through `V5`) |
| **In-Memory Test DB** | H2 Database | Fast, in-memory PostgreSQL compatibility mode |
| **Integration Test DB** | Testcontainers PostgreSQL | Dedicated PostgreSQL container for concurrency tests |
| **Containerization** | Docker, Multi-stage Dockerfile | Minimal Eclipse Temurin 21 JRE runtime image |
| **Local Orchestration** | Docker Compose | Local development stack (API, PostgreSQL, Adminer) |
| **CI Automation** | GitHub Actions | Automated build, test, and artifact packaging |

---

## 🏛️ Architecture Overview

The application follows a decoupled client-server architecture with strict single-origin proxying during development to satisfy browser cookie security standards (`HttpOnly; SameSite=Lax`).

```
+-----------------------------------------------------------------------------------+
|                                Browser / Client                                   |
|                          http://localhost:3000 (React SPA)                        |
+-----------------------------------------------------------------------------------+
       |                                      |                          ^
       | /oauth2/authorization/google         | /api/** (Axios)          | /oauth2/callback?code=...
       v                                      v                          |
+-----------------------------------------------------------------------------------+
|                        Vite Dev Server (Port 3000)                                |
|   Proxy Rules:                                                                    |
|     - /api                   -> http://localhost:8080/api                         |
|     - /oauth2/authorization -> http://localhost:8080/oauth2/authorization        |
|     - /login/oauth2          -> http://localhost:8080/login/oauth2                |
|     - /oauth2/callback       -> Local React SPA Route (index.html fallback)       |
+-----------------------------------------------------------------------------------+
                                      |
                                      v (Internal Proxied Traffic)
+-----------------------------------------------------------------------------------+
|                       Spring Boot 3.4 API (Port 8080)                             |
|   - SecurityFilterChain: Stateless JWT filter, CORS, Custom OAuth2/OIDC Handlers  |
|   - Controllers: /api/users, /api/expenses                                        |
|   - Services & Repositories: JPA Auditing, Soft Deletes                           |
+-----------------------------------------------------------------------------------+
                                      |
                                      v
+-----------------------------------------------------------------------------------+
|                       PostgreSQL 16 Database (Port 5432)                          |
|   - Database: expense_tracker (Managed via Flyway DDL migrations)                 |
+-----------------------------------------------------------------------------------+
```

### Google OAuth2 / OIDC One-Time Exchange Flow

To ensure JWT access tokens and refresh tokens are never exposed in browser address bars or URL history:

```mermaid
sequenceDiagram
    autonumber
    actor User as User Browser
    participant Frontend as React Frontend (:3000)
    participant Backend as Spring Boot API (:8080)
    participant Google as Google Identity Provider
    participant DB as PostgreSQL Database

    User->>Frontend: Click "Continue with Google"
    Frontend->>Backend: GET /oauth2/authorization/google (proxied via Vite)
    Note over Backend: CustomOAuth2AuthorizationRequestResolver generates 32-byte oauth_binder cookie.<br/>HttpCookieOAuth2AuthorizationRequestRepository signs oauth2_auth_request cookie (HMAC-SHA256, stateless).
    Backend-->>User: 302 Redirect to Google Consent Screen + Set-Cookie: oauth_binder & oauth2_auth_request
    User->>Google: Authenticate & Grant Consent
    Google->>Backend: Redirect /login/oauth2/code/google with authorization code
    Backend->>Backend: Restore & verify oauth2_auth_request cookie; clear cookie
    Backend->>Google: Exchange authorization code for ID & Access Tokens
    Google-->>Backend: Return OIDC ID Token & profile claims
    Note over Backend: CustomOidcUserService validates email_verified is true.<br/>CustomOAuth2UserService enforces account conflict rules.
    Backend->>DB: Query user by provider/sub or email
    alt Existing Google user
        Backend->>DB: Resolve existing user
    else Local verified account OR local unverified account owning expenses
        Backend-->>User: Refuse login (account_conflict), redirect to failure URI
    else Local unverified account with zero expenses
        Backend->>DB: Delete unverified account & tokens, provision new Google user
    else No existing account
        Backend->>DB: Auto-provision new Google user (ROLE_USER, emailVerified=true)
    end
    Note over Backend: OAuth2AuthenticationSuccessHandler verifies oauth_binder cookie,<br/>hashes raw code and binder with SHA-256.
    Backend->>DB: Save hashed code row to oauth_exchange_codes (code_hash, binder_hash, 300s TTL)
    Backend-->>User: 302 Redirect http://localhost:3000/oauth2/callback?code={rawCode}
    User->>Frontend: Load /oauth2/callback route; sanitize URL (history.replaceState)
    Frontend->>Backend: POST /api/users/oauth2/exchange { code: rawCode } (sends oauth_binder cookie)
    Backend->>DB: Atomically consume code via DELETE ... RETURNING where code_hash = sha256(rawCode)
    Backend->>Backend: Verify SHA-256(oauth_binder) matches stored binder_hash (constant-time)
    Backend->>DB: Rotate & persist RefreshToken entity
    Backend-->>Frontend: HTTP 200 OK with JWT in JSON body + Set-Cookie: refreshToken (HttpOnly) & clear oauth_binder
    Frontend->>Frontend: Store JWT in memory, navigate to /dashboard
```

#### Account Conflict Resolution Rules
During Google OAuth sign-in, the application enforces strict account-conflict policies:
1. **Existing Google User**: If an account exists with `authProvider = GOOGLE` matching the Google `sub` identifier, authentication succeeds immediately.
2. **Verified Local Account**: If an account with the same email already exists and was registered with local credentials (`authProvider = LOCAL`) and is verified (`emailVerified = true`), Google login is **refused** with an `account_conflict` exception. The user must sign in using their email and password.
3. **Unverified Local Account with Expenses**: If a local account is unverified but owns any expense records (including soft-deleted records), Google sign-in is **refused** (`account_conflict`) to prevent data loss or unauthorized account takeover.
4. **Unverified Local Account with Zero Expenses**: If a local account is unverified, owns zero expenses, and Google reports `email_verified = true`, the stale unverified account and its pending tokens are deleted, and a new Google OAuth account is provisioned.
5. **New User**: If no account exists with that email address, a new user is automatically provisioned with `ROLE_USER` and `emailVerified = true`.

---

## 📁 Project Directory Structure

```text
expensetracker/
├── .github/
│   └── workflows/
│       └── ci.yml                     # GitHub Actions CI workflow
├── docs/
│   └── db-checks/
│       └── unverified-users-check.sql # Diagnostic SQL for unverified users
├── expensetracker/                    # Spring Boot 3.4 Backend
│   ├── .mvn/wrapper/                  # Maven wrapper binaries & properties
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/expensetracker/
│   │   │   │   ├── config/            # Security, OpenAPI, Auditing, Properties
│   │   │   │   ├── controller/        # REST Controllers (ExpenseController, UserController)
│   │   │   │   ├── dto/               # Request, Response, and Envelope DTOs
│   │   │   │   ├── entity/            # JPA Entities (User, Expense, RefreshToken, OAuthExchangeCode)
│   │   │   │   ├── exception/         # Custom exceptions & GlobalExceptionHandler
│   │   │   │   ├── mapper/            # Entity-to-DTO mappers
│   │   │   │   ├── repository/        # Spring Data JPA repositories & custom queries
│   │   │   │   ├── security/          # JwtAuthenticationFilter & OAuth2/OIDC handlers
│   │   │   │   ├── service/           # Business logic services
│   │   │   │   └── util/              # MaskUtils and shared helper utilities
│   │   │   └── resources/
│   │   │       ├── db/migration/      # Flyway SQL migrations (V1 to V5)
│   │   │       ├── templates/         # Verification email HTML template
│   │   │       ├── application.properties
│   │   │       └── application.yml    # Main configuration with environment placeholders
│   │   └── test/
│   │       ├── java/com/example/expensetracker/
│   │       │   ├── config/            # Security, CORS, and secret validation tests
│   │       │   ├── controller/        # Controller unit tests (MockMvc)
│   │       │   ├── logging/           # PII log sanitization verification tests
│   │       │   ├── security/oauth2/   # OAuth2 unit tests & PostgresExchangeCodeConcurrencyIT
│   │       │   ├── service/           # Service tests & cleanup database tests
│   │       │   ├── util/              # Utility unit tests (MaskUtilsTest)
│   │       │   └── ExpensetrackerApplicationTests.java
│   │       └── resources/
│   │           └── application-test.yml # Isolated test configuration
│   ├── mvnw                           # Linux/macOS Maven wrapper script
│   ├── mvnw.cmd                       # Windows PowerShell/CMD Maven wrapper script
│   └── pom.xml                        # Maven project descriptor & dependencies
├── frontend/                          # React 18 + Vite 6 Frontend
│   ├── src/
│   │   ├── components/                # UI components, modals, layout shell
│   │   ├── hooks/                     # Custom TanStack React Query hooks
│   │   ├── pages/                     # SPA Pages (Dashboard, Expenses, Analytics, Login, etc.)
│   │   ├── services/api/              # Axios client, interceptors, and API contracts
│   │   ├── store/                     # Zustand authentication state store
│   │   ├── types/                     # TypeScript domain models and API contracts
│   │   ├── utils/                     # Error handling and formatting utilities
│   │   ├── App.tsx                    # React Router configuration & provider setup
│   │   └── main.tsx                   # React DOM entry point
│   ├── package.json                   # NPM package manifest
│   ├── tailwind.config.js             # Tailwind CSS design system configuration
│   ├── tsconfig.json                  # TypeScript compiler configuration
│   └── vite.config.ts                 # Vite dev server configuration & reverse proxy
├── .env.example                       # Local environment configuration template
├── .gitignore                         # Git exclusion rules
├── AGENTS.md                          # Development environment documentation
├── Dockerfile                         # Multi-stage container build
├── docker-compose.yml                 # Local development stack (API, PostgreSQL, Adminer)
├── README.md                          # Project documentation
└── render.yaml                        # Infrastructure deployment blueprint
```

---

## 📋 Prerequisites

Ensure the following tools are installed on your machine before running locally:

- **Java Development Kit (JDK)**: Version **21** (Eclipse Temurin 21 recommended)
- **Node.js**: Version **18.x** or higher (tested on Node.js 22 LTS)
- **Package Manager**: **npm** (v10+)
- **Database**: **PostgreSQL 16+** running locally on port `5432` OR **Docker**
- **Docker & Docker Compose** *(Optional)*: Version 24+ for containerized execution

---

## 🚀 Local Development Setup

Follow these steps to run the application natively on Windows using PowerShell.

### 1. Clone Repository & Setup Environment

```powershell
git clone https://github.com/tanishqmaheshwari07/Expentra.git
cd Expentra
```

Configuration differs depending on how you run the application:
- **Docker Compose**: Docker Compose automatically reads a `.env` file located in the project root directory. Run `Copy-Item .env.example .env` and populate your secrets before running `docker compose up`.
- **Native Local Development (`mvnw spring-boot:run`)**: Spring Boot does not automatically read `.env` files from disk. Instead, you define environment variables in your active PowerShell session (shown in Step 3).

> [!IMPORTANT]
> The `.env` file contains sensitive values and is explicitly excluded by `.gitignore`. Never commit `.env` or plain-text secrets to version control.

### 2. Configure Local PostgreSQL Database

Ensure PostgreSQL is running locally on port `5432`. Create the target database using `psql` or pgAdmin:

```sql
CREATE DATABASE expense_tracker;
```

### 3. Start the Backend API (Spring Boot)

Open a PowerShell terminal and set your environment variables for the current session:

```powershell
cd expensetracker

# Required Secrets (minimum 32 characters; no defaults)
$env:DB_PASSWORD="your_postgres_password"
$env:JWT_SECRET="your_secure_jwt_signing_secret_at_least_32_chars"
$env:OAUTH2_COOKIE_SECRET="your_secure_oauth2_cookie_secret_32_chars"

# Database Configuration (Optional defaults shown)
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="expense_tracker"
$env:DB_USERNAME="postgres"

# Optional: Google OAuth2 Credentials (required for Google Login button)
$env:GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET="your-client-secret"

# Launch Spring Boot via Maven wrapper
.\mvnw.cmd spring-boot:run
```

The backend starts on `http://localhost:8080`. Flyway automatically executes database migrations on startup.

### 4. Start the Frontend Application (React / Vite)

Open a second PowerShell terminal:

```powershell
cd frontend

# Install dependencies
npm install

# Start Vite development server
npm run dev
```

The frontend dev server starts on `http://localhost:3000`.

Open your browser and navigate to **[http://localhost:3000](http://localhost:3000)**.

---

## ⚙️ Environment Variables Configuration

The application reads its configuration from system environment variables with defaults defined in `application.yml` and `vite.config.ts`.

### Required Environment Variables

The following environment variables have **no default values** and are strictly required. The backend application will fail fast at startup if any are missing or too short:

- `DB_PASSWORD`: PostgreSQL database user password.
- `JWT_SECRET`: Secret key for HMAC signing of JWT tokens (minimum 32 characters).
- `OAUTH2_COOKIE_SECRET`: Secret key for HMAC-SHA256 signing of OAuth authorization request cookies (minimum 32 characters).

#### PowerShell Example (Local Development)

```powershell
$env:DB_PASSWORD="your_postgres_password"
$env:JWT_SECRET="your_secure_jwt_signing_secret_at_least_32_chars"
$env:OAUTH2_COOKIE_SECRET="your_secure_oauth2_cookie_secret_32_chars"
```

### Complete Environment Variables Reference

| Variable | Description | Default / Example | Required |
| :--- | :--- | :--- | :---: |
| `DB_HOST` | Hostname of the PostgreSQL database | `localhost` | No |
| `DB_PORT` | Port of the PostgreSQL database | `5432` | No |
| `DB_NAME` | Name of the database | `expense_tracker` | No |
| `DB_USERNAME` | PostgreSQL username | `postgres` | No |
| `DB_PASSWORD` | PostgreSQL password | *None* | **Yes** |
| `DB_URL` | Complete JDBC URL override | `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` | No |
| `PORT` | Spring Boot HTTP server port | `8080` | No |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated allowed CORS origins | `http://localhost:[*],http://127.0.0.1:[*],https://localhost:[*],https://127.0.0.1:[*]` | No |
| `JWT_SECRET` | Secret key for JWT HMAC signing (min 32 chars) | *None* | **Yes** |
| `JWT_ACCESS_EXPIRATION_MS` | Access token lifespan in milliseconds | `900000` (15 minutes) | No |
| `JWT_REFRESH_EXPIRATION_MS`| Refresh token lifespan in milliseconds | `604800000` (7 days) | No |
| `COOKIE_SECURE` | Set `true` in production to enforce HTTPS cookie delivery | `false` | No |
| `COOKIE_SAME_SITE` | Cookie SameSite policy (`Lax`, `Strict`, `None`) | `Lax` | No |
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID from Google Cloud Console | `google-client-id-placeholder` | For OAuth |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 Client Secret | `google-client-secret-placeholder` | For OAuth |
| `OAUTH2_COOKIE_SECRET` | Secret for HMAC-SHA256 signing OAuth cookies (min 32 chars) | *None* | **Yes** |
| `APP_FRONTEND_URL` | Frontend URL for email verification links | `http://localhost:3000` | No |
| `OAUTH2_REDIRECT_URI` | Frontend callback redirect URL after Google sign-in | `http://localhost:3000/oauth2/callback` | No |
| `OAUTH2_FAILURE_REDIRECT_URI` | Frontend redirect URL on OAuth failure | `http://localhost:3000/login?error=oauth_failed` | No |
| `OAUTH2_EXCHANGE_EXPIRATION_SECONDS` | Lifespan of single-use OAuth exchange code | `300` (5 minutes) | No |
| `MAIL_HOST` | SMTP server host | `localhost` | For Email |
| `MAIL_PORT` | SMTP server port | `587` | For Email |
| `MAIL_USERNAME` | SMTP authentication username | *None* (empty string) | For Email |
| `MAIL_PASSWORD` | SMTP authentication password / app token | *None* (empty string) | For Email |
| `MAIL_AUTH` | SMTP authentication required | `true` | No |
| `MAIL_STARTTLS_ENABLE` | SMTP STARTTLS enabled | `true` | No |
| `MAIL_STARTTLS_REQUIRED` | SMTP STARTTLS required | `false` | No |
| `MAIL_FROM` | Sender address for outgoing emails | `noreply@expensetracker.com` | No |
| `EMAIL_VERIFICATION_EXPIRATION_MINUTES` | Email verification token lifespan | `30` | No |
| `EMAIL_VERIFICATION_RESEND_COOLDOWN_SECONDS` | Cooldown period between verification resends | `60` | No |
| `UNVERIFIED_USERS_CLEANUP_ENABLED` | Enable scheduled cleanup job for unverified users | `false` | No |
| `UNVERIFIED_USERS_CLEANUP_DRY_RUN` | Dry-run mode for cleanup job (logs without deleting) | `true` | No |
| `UNVERIFIED_USERS_CLEANUP_DAYS` | Threshold in days before unverified users are cleaned | `7` | No |
| `VITE_API_BASE_URL` | Frontend API base path (proxied) | `/api` | No |
| `BACKEND_URL` | Target backend URL for Vite reverse proxy | `http://localhost:8080` | No |

### Google Cloud OAuth Configuration

To enable Google sign-in, configure an OAuth 2.0 Client ID in **Google Cloud Console** under **APIs & Services > Credentials**:

- **Application Type**: Web application
- **Authorized JavaScript origins**:
  - `http://localhost:3000`
  - `http://localhost:8080`
- **Authorized redirect URIs**:
  - `http://localhost:8080/login/oauth2/code/google`

> [!NOTE]
> `http://localhost:8080/login/oauth2/code/google` is the backend authorization callback endpoint registered with Google. Spring Security handles this callback and subsequently redirects the browser to the internal React frontend route (`http://localhost:3000/oauth2/callback?code=...`). The frontend route is not a Google redirect URI and should not be configured in Google Cloud Console.

---

## 🗄️ Database Setup & Migrations

Database schema management is handled automatically by **Flyway**. Manual DDL execution is not required.

### Migration History
- **`V1__init_schema.sql`**: Initial DDL creating `users`, `expenses`, `refresh_tokens`, indexes, and auditing columns.
- **`V2__upgrade_existing_schema.sql`**: Schema hardening, optimistic locking `version` columns, and soft deletion flags.
- **`V3__add_oauth_support.sql`**: Adds `auth_provider` and `provider_id` columns; relaxes null constraints on passwords for OAuth users.
- **`V4__add_email_verification.sql`**: Creates `email_verification_tokens` table and adds `email_verified` flag to `users`.
- **`V5__add_oauth_exchange_codes.sql`**: Creates `oauth_exchange_codes` table with SHA-256 code hash, user reference, browser binder hash, and expiration index for atomic single-use OAuth2 code exchange.

Flyway runs automatically upon Spring Boot application startup with:
```yaml
spring.flyway.baseline-on-migrate: true
spring.flyway.validate-on-migrate: false
```

---

## 🐳 Docker Compose Local Development Stack

The repository includes a `docker-compose.yml` file designed as a local multi-container development stack comprising the Spring Boot API, PostgreSQL 16 database, and Adminer web management interface.

> [!WARNING]
> This Compose file is intended solely for local development and testing. It publishes the PostgreSQL database (`5432:5432`) and Adminer (`8081:8080`) directly on all host network interfaces (`0.0.0.0`), which is not hardened for production environments.

### Running with Docker Compose

Docker Compose automatically loads variables from a `.env` file in the project directory, or you can provide them as environment variables in your active shell:

1. Set the required variables (enforced by `:?` in `docker-compose.yml`):

```powershell
$env:DB_PASSWORD="your_secure_postgres_password"
$env:JWT_SECRET="your_strong_jwt_secret_key_at_least_32_chars"
$env:OAUTH2_COOKIE_SECRET="your_secure_oauth2_cookie_secret_32_chars"

# Optional: Google OAuth2 Credentials
$env:GOOGLE_CLIENT_ID="your_google_client_id"
$env:GOOGLE_CLIENT_SECRET="your_google_client_secret"
```

2. Build and launch the container stack:

```powershell
docker compose up --build -d
```

### Container Endpoints

| Service | Local Address | Description |
| :--- | :--- | :--- |
| **Spring Boot API** | [http://localhost:8080](http://localhost:8080) | Containerized REST API |
| **PostgreSQL 16** | `localhost:5432` | Containerized database (`expense_tracker`, published on `0.0.0.0:5432`) |
| **Adminer Web GUI** | [http://localhost:8081](http://localhost:8081) | Database administration web interface (published on `0.0.0.0:8081`) |

To stop the containers without destroying volume data:
```powershell
docker compose down
```

---

## 📖 API Documentation & Observability

### Interactive Documentation & Observability Endpoints

When the backend is running, the following endpoints are available:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 JSON Specification**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Spring Boot Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Prometheus Metrics**: [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

### Summary of REST API Endpoints

#### Authentication & User Management (`/api/users`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/users/register` | Register new local user account | No |
| `POST` | `/api/users/login` | Authenticate with email & password | No |
| `POST` | `/api/users/oauth2/exchange` | Exchange single-use OAuth code for JWT | No |
| `POST` | `/api/users/refresh-token` | Rotate refresh token cookie and issue new JWT | No |
| `POST` | `/api/users/logout` | Revoke refresh token and invalidate cookie | No |
| `GET` | `/api/users/verify-email` | Validate email verification token | No |
| `POST` | `/api/users/resend-verification` | Resend email verification link | No |
| `GET` | `/api/users/me` | Retrieve authenticated user profile | **Yes** |
| `PUT` | `/api/users/{id}` | Update profile name and email | **Yes** |
| `PUT` | `/api/users/{id}/password` | Update account password | **Yes** |
| `DELETE` | `/api/users/{id}` | Delete account and associated data | **Yes** |
| `GET` | `/api/users` | List all registered users | **Admin** |
| `PATCH` | `/api/users/{id}/role` | Update user authorization role | **Admin** |

#### Expense Management (`/api/expenses`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/expenses` | Create a new expense | **Yes** |
| `GET` | `/api/expenses` | Paginated expenses with category & date filters | **Yes** |
| `GET` | `/api/expenses/{id}` | Get single expense details by ID | **Yes** |
| `PUT` | `/api/expenses/{id}` | Update an existing expense | **Yes** |
| `DELETE` | `/api/expenses/{id}` | Soft-delete an expense record | **Yes** |
| `GET` | `/api/expenses/my-expenses` | Retrieve complete expense list | **Yes** |
| `GET` | `/api/expenses/summary` | Aggregated analytics & category breakdown | **Yes** |

#### Google OAuth2 & OpenID Connect
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `GET` | `/oauth2/authorization/google` | Initiate Google OAuth2 authorization flow | No |
| `GET` | `/login/oauth2/code/google` | Google callback handled by Spring Security | No |
| `GET` | `/oauth2/callback` | Frontend SPA route on port 3000 | No |

---

## 🧪 Testing & Build

### Backend Testing & Build (Maven)

The backend test suite uses **JUnit 5**, **Mockito**, and an in-memory **H2 database** (in PostgreSQL compatibility mode). The standard test suite runs 118 tests independently of any external PostgreSQL instance.

```powershell
cd expensetracker

# Run full backend test suite (118 tests across security, services, controllers)
.\mvnw.cmd test

# Compile all source files
.\mvnw.cmd compile

# Package executable JAR (skipping test execution)
.\mvnw.cmd clean package -DskipTests
```

### Containerized PostgreSQL Concurrency Integration Test

The test suite includes `PostgresExchangeCodeConcurrencyIT`, which verifies Flyway migrations (`V1` to `V5`), PostgreSQL-specific atomic `DELETE ... RETURNING` code consumption, and concurrent exchange handling using Testcontainers.

Because it launches an ephemeral PostgreSQL container, it **requires a running Docker daemon**. By default, it is conditionally executed (`@EnabledIf("isDockerAvailable")`) and skips gracefully when Docker is offline.

To run this integration test explicitly:

```powershell
cd expensetracker
.\mvnw.cmd test -Dtest=PostgresExchangeCodeConcurrencyIT
```

### Frontend Testing & Build (Vite)

```powershell
cd frontend

# Run TypeScript type check and build production distribution bundle
npm run build

# Preview local production build
npm run preview
```

---

## 🔒 Security Architecture & Troubleshooting

### Defense-in-Depth Token Security
1. **No Tokens in LocalStorage**: JWT access tokens are stored strictly in memory via Zustand and never written to `localStorage` or `sessionStorage`.
2. **HttpOnly Cookie Protection**: Refresh tokens cannot be read or exfiltrated by client-side JavaScript, neutralizing Cross-Site Scripting (XSS) attack vectors.
3. **No Tokens in URLs**: OAuth authorization redirects use an ephemeral, single-use exchange code that expires after 5 minutes and is immediately invalidated upon first consumption.
4. **Dynamic HMAC-SHA Signing**: JWT tokens are signed using JJWT's `Keys.hmacShaKeyFor()`, which selects the appropriate HMAC-SHA algorithm based on the bit strength of the configured key (minimum 256 bits / 32 characters enforced at startup).
5. **Account Conflict Prevention**: If an account was registered with email/password, a Google OAuth login using the same email is refused (`account_conflict`) if the account is verified or owns existing expenses.

### Common Troubleshooting Scenarios

#### 1. `NoResourceFoundException: No static resource oauth2/callback`
- **Cause**: The Vite proxy rule in `frontend/vite.config.ts` was matching `/oauth2` broadly and forwarding the frontend SPA callback route to Spring Boot.
- **Solution**: Verify `vite.config.ts` proxies `/oauth2/authorization` specifically, allowing `/oauth2/callback` to be handled by React Router.

#### 2. `Invalid or expired OAuth2 exchange code`
- **Cause**: Exchange codes are single-use with a 300-second TTL. Reloading `/oauth2/callback` or React StrictMode triggering duplicate calls will cause a 401.
- **Solution**: The frontend callback page automatically sanitizes the browser URL via `window.history.replaceState` upon receipt, preventing re-submission on reload.

#### 3. `Connection to localhost:5432 refused`
- **Cause**: PostgreSQL service is stopped or running on a non-standard port.
- **Solution**: Run `Get-Service *postgres*` in PowerShell to ensure the Windows service is running, or verify your `DB_PORT` setting.

#### 4. Google OAuth `redirect_uri_mismatch`
- **Cause**: The redirect URI registered in Google Cloud Console does not match the URI sent by Spring Security.
- **Solution**: Ensure `http://localhost:8080/login/oauth2/code/google` is added to **Authorized redirect URIs** in your Google Cloud credentials.

---

## ⚠️ Known Limitations

The following items reflect known limitations confirmed in the current codebase:

1. **No Authentication Rate Limiting**: The `/api/users/login` and `/api/users/register` endpoints do not implement rate limiting or brute-force throttling mechanisms (such as Bucket4j or Redis token buckets).
2. **Public Actuator and Swagger Endpoints**: Spring Boot Actuator endpoints (`/actuator/**`) and Swagger/OpenAPI documentation (`/swagger-ui/**`, `/v3/api-docs/**`) are configured with `permitAll()` in Spring Security and are publicly accessible without authentication.
3. **Unhashed Refresh Tokens without Reuse Detection**: Refresh tokens are stored in the database as plaintext UUID strings and do not implement cryptographic hashing or token family reuse detection (e.g., revoking all sessions if a consumed token is replayed).
4. **Single Active Refresh Session per User**: Creating or rotating a refresh token deletes existing refresh tokens for that user (`refreshTokenRepository.deleteByUser(user)`), limiting each user account to a single active session or device.
5. **No Password Reset Flow**: There is no self-service "forgot password" or email-based password reset token flow; password updates require an existing authenticated session and verification of the current password.
6. **Single Currency UI Formatting**: The frontend hardcodes currency formatting to Indian Rupee (`INR`, `en-IN`) in `frontend/src/utils/formatters.ts` and does not support multi-currency selection or conversion.
7. **Unverified Cloud Deployment Blueprint**: The `render.yaml` configuration is provided as an infrastructure blueprint and has not been tested or verified on an active Render production deployment.

---

## 🗺️ Future Improvements (Roadmap)

The following features are planned for future iterations of ExpenseTracker:

- [ ] **Multi-Currency Support**: Real-time currency conversion and multi-currency expense logging.
- [ ] **Receipt OCR Scanning**: Automatic extraction of merchant name, date, and amount from receipt images.
- [ ] **Recurring Subscriptions**: Automated expense generation for periodic subscription services.
- [ ] **Export to CSV & PDF**: Downloadable expense statements and tax reports.
- [ ] **Budget Threshold Alerts**: Real-time email notifications when category budgets exceed preset limits.

---

## 📄 License

Licensing terms have not yet been specified for this project. Please contact the repository owner regarding distribution, modification, and commercial usage rights.
