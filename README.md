# AI Social Media Content Planner

AI Social Media Content Planner is a Spring Boot application for creating, reviewing, scheduling, and publishing social media content from documents and web sources.

The project supports the complete content lifecycle: source ingestion, AI-assisted batch generation, draft editing, media management, scheduling, provider credential management, automatic publishing, and publication confirmation.

The React frontend is maintained as a separate codebase, but it is placed under the backend project's `frontend/` directory in the combined local and deployment layout. The directory remains ignored by the backend repository so the frontend keeps its own Git history. The Maven/Spring Boot backend stays at the project root.

## Main Capabilities

- Generate multiple content items from links, PDF, DOCX, and TXT sources.
- Select text, image, and video models independently for each generation batch.
- Use OpenAI, Anthropic Claude, Google Gemini, DeepSeek, and Qwen integrations.
- Run AI generation and social publishing independently in `mock` or `real` mode.
- Choose between source-based and combined generation strategies.
- Generate and edit platform-relevant hashtags.
- Store generated content as drafts and edit text, hashtags, images, and videos.
- Schedule, reschedule, cancel, retry, and automatically publish content.
- Publish through LinkedIn, Instagram, and X/Twitter clients.
- Connect social accounts through OAuth and manage encrypted provider credentials.
- Synchronize available AI models from models.dev.
- Filter and paginate content, generation batches, and their states.
- Observe requests and background jobs through structured, correlated logs.

## Supported Platforms and Formats

| Platform | Supported content types | Generation rules |
| --- | --- | --- |
| LinkedIn | Post | Professional and informative tone with a more detailed text structure. One image or one video is optional; image and video cannot be selected together. |
| Instagram | Post, Reel | A Post requires an image and rejects video; use Reel for video. A Reel requires video and may also contain an optional cover image. |
| X/Twitter | Tweet | Concise text with a maximum weighted publication length of 280 characters, including hashtags. One image or one video is optional; image and video cannot be selected together. |

Invalid platform and content-type combinations are rejected by both domain validation and database constraints. For example, `TWITTER + REEL` or `LINKEDIN + TWEET` cannot be created.

Invalid media combinations are rejected when the generation request is validated, before sources are stored or an AI provider is called. The `GenerationBatch` aggregate applies the same invariant so the rule cannot be bypassed by constructing a batch outside the REST flow.

AI output must include at least one topic- and platform-relevant hashtag. For X/Twitter, the final text and formatted hashtags are validated with the weighted-length algorithm before the content is accepted or scheduled. If correction output is still too long, low-priority hashtags are removed from the end as a deterministic fallback instead of cutting the text in the middle of a word.

When image or video generation is selected, every generated content item must contain the requested media type. Media prompts receive the platform, content type, generated text, and hashtags so the provider can produce media aligned with the content topic. This semantic alignment is an AI instruction; the presence of the requested media is enforced by application validation.

## Content Generation Flow

1. The user selects a platform, content type, requested count, source files or links, and AI models.
2. A `GenerationBatch` is created in `IN_PROGRESS` state.
3. Uploaded PDF, DOCX, and TXT files are stored locally and their text is extracted.
4. Link sources are downloaded by the backend and converted from HTML into plain text.
5. The selected generation strategy determines how sources are assigned to each content item.
6. The AI provider returns structured JSON containing `text` and `hashtags`.
7. Domain and application rules validate the platform, format, hashtags, X/Twitter length, and requested media.
8. Valid results are stored as `DRAFT` content. Invalid AI output receives one correction attempt before deterministic fallback or batch failure.

Generation attempts are persisted separately. A successful result can be reused by generation index, capability, and prompt hash during recovery, preventing unnecessary duplicate provider calls after partial failures.

### Generation Strategies

`SOURCE_BASED` assigns one primary source to each content item in round-robin order. Supporting sources may provide additional context, but the primary source determines the central message. If there are fewer sources than requested content items, the API exposes a warning because sources will be reused.

`COMBINED` treats all extracted sources as a shared information pool. Content angles such as benefit-focused, question-led, CTA-focused, problem-solution, and fact-led are rotated to reduce repetitive output.

If the user does not select a strategy, source-based generation is chosen when source count equals requested content count; otherwise combined generation is used. The selected strategy, reason, and warning are persisted with the batch.

### Generation Limits

Configurable limits prevent unexpectedly large or expensive requests:

- Maximum content, image, and video counts per batch.
- Estimated input and output token usage.
- Estimated text, image, and video cost.
- Maximum estimated cost per batch.

These controls are estimates and operational safeguards, not provider billing records.

## Source Extraction

### Documents

- PDF text is extracted with Apache PDFBox.
- DOCX paragraphs and tables are extracted with Apache POI.
- TXT files are read as UTF-8.
- Uploaded files are stored under the configured local upload directory for the current development phase.

### Links

The AI provider does not browse the supplied URL directly. The backend downloads the page, removes basic non-content elements such as scripts, styles, navigation, and footers, then sends the extracted plain text to the generation prompt.

Link extraction currently supports server-rendered HTML. Pages that require login, browser cookies, JavaScript rendering, or bypassing bot protection may fail and are recorded as failed sources. If other sources are available, generation continues with them; if no source can be extracted, the batch fails.

## Content Lifecycle

Content state is controlled through entity behavior methods rather than public status setters:

```text
DRAFT -> SCHEDULED -> PUBLISHING -> PUBLISHED
                    |          |
                    |          -> REVIEW_REQUIRED
                    -> FAILED
```

- Draft text, hashtags, and media can be edited only while the content is `DRAFT`.
- Drafts can be scheduled or deleted.
- Scheduled content can be rescheduled or returned to `DRAFT`.
- Failed publication can be scheduled for retry.
- `PUBLISHED` is assigned only after the platform confirms that the external post exists.
- An uncertain publication is not sent again automatically, avoiding accidental duplicate posts.

## Reliable Automatic Publishing

The publishing scheduler polls due content at a configurable interval. Database row locking with `FOR UPDATE SKIP LOCKED` ensures that concurrent job executions cannot claim the same content simultaneously.

External HTTP calls are performed outside database transactions. The system first claims the content, commits the `PUBLISHING` state and operation identifier, and then calls the platform client.

After a platform accepts a publication, its external identifier is stored internally. A separate confirmation step checks the post through the platform API before marking the content `PUBLISHED`. External post identifiers are retained for backend verification and audit purposes but are not exposed through normal frontend responses.

Definitive provider rejections are recorded as `FAILED` with a safe user-facing error. Network errors, timeouts, and ambiguous provider responses remain in a confirmation flow because resending them could create duplicate posts. If confirmation does not complete within the configured timeout, the content becomes `REVIEW_REQUIRED` instead of being sent again.

Provider-specific technical details remain in logs, while REST responses return stable and readable error codes/messages for the user interface.

## AI Models and Provider Modes

AI and publishing modes are configured independently:

```properties
AI_PROVIDER_MODE=mock
PUBLISHING_MODE=mock
```

- `AI_PROVIDER_MODE` controls whether text/image/video generation uses the mock client or real AI providers.
- `PUBLISHING_MODE` controls whether publication uses the mock client or real social platform APIs.

This allows real AI generation with mock publishing, or mock AI generation with real publishing, without code changes.

The models.dev catalog is synchronized once during startup and then daily at the configured time. Supported providers and capabilities are filtered before models are saved to `ai_model_cache`. A model can appear under multiple capabilities such as `TEXT` and `IMAGE`. If synchronization fails, the existing cache is preserved and its previous synchronization timestamp remains unchanged.

## Credential and OAuth Management

- AI API keys and social platform tokens are managed through backend APIs.
- X/Twitter, LinkedIn, and Instagram accounts can be connected through OAuth.
- Access and refresh tokens are encrypted with AES-256-GCM before database persistence.
- Plaintext tokens are not returned by REST responses and must not be logged.
- Credential validation records whether a provider credential is valid without exposing the secret.
- Social token refresh runs on a configurable schedule where the provider supports refresh.
- Provider name and credential type remain stable while tokens can be rotated.

## Security Measures

### Link SSRF Protection

Link ingestion accepts untrusted network locations, so SSRF protection is required even for a single-user deployment. A trusted user could still receive and paste a malicious URL designed to make the server access PostgreSQL, an internal service, localhost, or a cloud metadata endpoint.

Implemented protections include:

- Only HTTP and HTTPS schemes are accepted.
- DNS resolution occurs inside the HTTP client's connection flow through a custom OkHttp DNS resolver.
- The exact validated address list is used for the connection, closing the separate validation/request DNS-rebinding window.
- Private, loopback, link-local, multicast, CGNAT, IPv4-mapped IPv6, documentation, and other special-use ranges are rejected.
- HTTP proxies are disabled for source extraction so the proxy cannot bypass connection-level DNS validation.
- Redirects are handled manually, limited to five hops, and every destination is validated again.
- URL user information such as `user@host` is rejected.
- Only `text/html` and `application/xhtml+xml` responses are accepted.
- Response size is enforced while streaming, even when `Content-Length` is absent or incorrect.
- Request timeout and maximum body size are configurable.

### Additional Safeguards

- Environment files, uploaded documents, generated media, build output, and local credential notes are excluded from version control.
- Credential encryption keys are provided through environment configuration rather than source code.
- API responses do not expose plaintext credentials or internal external-post identifiers.
- Logs use parameterized messages and must not contain passwords, API keys, access tokens, or refresh tokens.
- OAuth state values have a configurable expiration time.
- Flyway owns schema changes while Hibernate runs with `ddl-auto=validate`.

## Logging and Observability

- Incoming HTTP requests receive or reuse an `X-Correlation-Id`, which is also returned in the response.
- Background jobs create their own correlation ID and add `jobName`, `batchId`, and `provider` MDC fields where applicable.
- Local profile logs use a human-readable console pattern.
- Non-local profiles emit asynchronous JSON logs suitable for centralized collection.
- Routine successful GET requests and empty scheduler runs use DEBUG-level logging to avoid noisy production logs.
- Actuator exposes health, readiness, liveness, metrics, and Prometheus endpoints.

## Architecture

The backend follows a layered DDD structure with behavior-rich JPA entities:

```text
.
├── frontend/                                   # Separately versioned React UI placed in the combined project
├── src/main/java/com/globalcodelabs/socialmediaplanner
│   ├── domain/                                 # Entities, value objects, policies, enums, repositories
│   ├── application/                            # Commands and application services
│   ├── infrastructure/                         # AI/social clients, encryption, extraction, storage, jobs
│   ├── interfaces/                             # REST controllers and request/response models
│   ├── common/                                 # Logging and shared exception handling
│   └── config/                                 # Application configuration
├── src/main/resources
│   ├── db/migration/                           # Flyway migrations
│   ├── application.yml                         # Shared configuration
│   ├── application-local.yml                   # Local profile
│   ├── application-prod.yml                    # Production profile
│   └── logback-spring.xml                      # Local and JSON logging configuration
└── pom.xml
```

Entities expose business operations such as `schedule`, `markPublished`, `markFailed`, and credential rotation. Status changes are not performed through public setters. JPA annotations are placed directly on the behavior-rich entities to avoid maintaining a duplicate persistence model and mapper layer.

## Technology Stack

- Java 21
- Spring Boot 3.5
- Spring Web and Jakarta Validation
- Spring Data JPA and Hibernate
- PostgreSQL 16
- Flyway
- Maven
- Springdoc OpenAPI / Swagger UI
- PDFBox, Apache POI, and Jsoup
- OkHttp
- Logback and Logstash Encoder
- Micrometer and Prometheus
- React and Tailwind CSS under `frontend/` in the combined layout

## Local Setup

### Requirements

- JDK 21
- PostgreSQL 16

Create the application environment file:

```bash
cp .env.example .env
```

Set the PostgreSQL connection values, then generate a 256-bit credential encryption key:

```bash
openssl rand -base64 32
```

Place the generated value in `CREDENTIAL_ENCRYPTION_KEY`. Application variables belong to the backend `.env`; PostgreSQL container variables belong to the separate `.env` next to `docker-compose.yml`.

Keep both provider modes set to `mock` for local development without external publication or AI requests:

```properties
AI_PROVIDER_MODE=mock
PUBLISHING_MODE=mock
```

Start the backend:

```bash
./mvnw spring-boot:run
```

Flyway applies pending migrations during startup. Hibernate validates that entity mappings match the migrated schema.

## Local URLs

- Backend: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI specification: `http://localhost:8080/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

## Production Readiness Checklist

Already implemented:

- [x] Database migrations and schema validation.
- [x] Encrypted credential persistence.
- [x] Mock/real provider separation.
- [x] Connection-level DNS-pinned SSRF protection for link extraction.
- [x] HTTPS, redirect, IP-range, content-type, and size checks for remote-media loading.
- [x] Correlated JSON logging and health probes.
- [x] Duplicate-resistant publishing and external confirmation flow.

Required before public deployment:

- [ ] Add API authentication and authorization.
- [ ] Run the application behind HTTPS.
- [ ] Store `CREDENTIAL_ENCRYPTION_KEY` in a secret manager rather than a regular environment file.
- [ ] Move local document/media storage to managed server or object storage.
- [ ] Apply the same connection-level DNS pinning used by link extraction to remote-media downloads.
- [ ] Apply network-level egress restrictions in addition to application-level SSRF protection.
- [ ] Validate provider permissions, rate limits, and publication edge cases with production accounts.
