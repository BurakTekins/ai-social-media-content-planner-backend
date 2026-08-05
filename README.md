# AI Social Media Content Planner

A multi-provider AI content generation, scheduling, and publishing platform built with Java 21, Spring Boot, React, PostgreSQL, and Flyway.

The application manages the complete lifecycle of social media content: source ingestion, AI-assisted generation, draft editing, scheduling, and publishing to supported platforms.

## Features

- Generate content in batches from links, PDF, DOCX, and TXT sources
- Support multiple AI providers: OpenAI, Anthropic Claude, Google Gemini, DeepSeek, and Qwen
- Switch between mock and real AI provider modes through configuration
- Discover and cache available AI models from models.dev
- Manage drafts, hashtags, media, scheduling, and publishing states
- Publish to LinkedIn, Instagram, and X/Twitter through mock or real clients
- Store API credentials encrypted with AES-256-GCM
- Run scheduled generation, model synchronization, and publishing jobs
- Track requests and background jobs with correlation IDs and structured logging
- Explore the REST API through Swagger UI

## Technology Stack

### Backend

- Java 21
- Spring Boot 3
- Spring Web and Validation
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Maven
- Springdoc OpenAPI
- Logback and Logstash Encoder

### Frontend

- React 19
- Vite
- Tailwind CSS 4
- pnpm

## Architecture

The backend follows a layered DDD structure:

```text
src/main/java/com/globalcodelabs/socialmediaplanner
├── domain          # Entities, domain rules, events, and repository contracts
├── application     # Commands, services, and outbound ports
├── infrastructure  # Persistence adapters, external clients, storage, and jobs
├── interfaces      # REST controllers and request/response models
└── common          # Shared logging and exceptions
```

Domain entities contain their own state-transition rules. Content status cannot be changed through public setters; operations such as scheduling, publishing, and failure handling are performed through behavior methods.

## Local Setup

### Requirements

- JDK 21
- PostgreSQL 16
- Node.js
- pnpm

### Configuration

Create the application environment file:

```bash
cp .env.example .env
```

Set the PostgreSQL connection values and generate a credential encryption key:

```bash
openssl rand -base64 32
```

Place the generated value in `CREDENTIAL_ENCRYPTION_KEY`. Keep `AI_PROVIDER_MODE=mock` and `PUBLISHING_MODE=mock` to run without calling external providers.

### Backend

```bash
./mvnw spring-boot:run
```

Flyway applies the database migrations automatically during startup.

### Frontend

```bash
cd frontend
pnpm install
pnpm dev
```

The frontend development server proxies `/api` requests to the backend at `http://localhost:8080` by default.

## Local URLs

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI specification: `http://localhost:8080/api-docs`
- Health check: `http://localhost:8080/actuator/health`

## Configuration Modes

```properties
AI_PROVIDER_MODE=mock
PUBLISHING_MODE=mock
```

Use `mock` for local development. Switching either value to `real` enables the corresponding external clients without requiring code changes. Real mode requires valid encrypted credentials to be registered through the credential management API.

## Security Notes

- `.env`, uploaded files, build outputs, and local credential notes are excluded from version control.
- API access and authorization must be added before exposing the application publicly.
- Run production deployments behind HTTPS and keep the credential encryption key separate from the database.
