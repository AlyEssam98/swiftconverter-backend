# AI Agent Guidelines for MtSaas Backend

## Architecture Overview
This is a Spring Boot 3.2.3 application providing SWIFT MT-MX message conversion SaaS. Follows clean architecture with layered separation:

- **API Layer** (`/api`): REST controllers handling HTTP requests
- **Application Layer** (`/application/service`): Business logic services
- **Domain Layer** (`/domain`): Entities, value objects, and domain-specific logic
- **Infrastructure Layer** (`/infrastructure`): External integrations (database, email, security)

## Key Components
- **ConversionService**: Orchestrates MT↔MX conversions using parsers/generators
- **CreditService**: Manages user credits and usage tracking
- **AuthService**: Handles JWT authentication and OAuth2 (Google)
- **StripeService**: Processes payments and webhooks
- **EmailService**: Sends verification and transactional emails
- **HealthController**: Provides /api/v1/health endpoint with DB connectivity check

## SWIFT Message Handling
- Supports MT types: 103, 202, 940 (extendable via generator pattern)
- MX types: pacs.008, pacs.009 (validated against XSD schemas in `/resources/xsd`)
- Parsers/generators in `/domain/swift/{mt,mx}` follow strategy pattern
- Conversions logged in `Conversion` entity with status tracking

## Credit & Authentication System
- Anonymous users: 1 free conversion per IP address
- Authenticated users: Pay-per-conversion with Stripe integration
- Credits expire based on purchase date (`creditsExpiryDate`)
- Authentication: JWT tokens + OAuth2 Google login
- User roles: USER, ADMIN (enum-based)

## External Integrations
- **Database**: PostgreSQL with JPA/Hibernate (ddl-auto=update in dev)
- **Cache/Session**: Redis for distributed sessions
- **Payments**: Stripe webhooks for credit purchases
- **Email**: Gmail SMTP for notifications
- **Background Jobs**: JobRunr for async tasks (dashboard disabled)

## Development Workflow
- **Local Run**: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`)
- **Build**: `./mvnw clean package -DskipTests`
- **Test DB**: PostgreSQL on localhost:5432 (configure via `application.properties`)
- **Profiles**: `dev` (default), `prod` (for deployment)
- **Health Check**: GET `/api/v1/health` (used by Render cron)

## Code Patterns & Conventions
- **Entities**: Lombok `@Data @Builder` with explicit getters/setters for JPA compatibility
- **Services**: `@RequiredArgsConstructor` for dependency injection
- **Controllers**: Map-based request/response bodies (no DTOs for simplicity)
- **Error Handling**: RuntimeExceptions with specific message codes (e.g., "INSUFFICIENT_CREDITS")
- **Logging**: SLF4J with structured logs for conversions and auth
- **Validation**: Bean validation on entities, manual checks in services
- **Mapping**: MapStruct for complex object transformations (configured in pom.xml)

## Deployment
- **Docker**: Multi-stage build (Maven build → JRE runtime)
- **Render**: Free tier with auto-deploy, health checks every 10min
- **Environment**: All config via env vars (DATABASE_URL, STRIPE_SECRET_KEY, etc.)
- **Scaling**: Stateless design, Redis for shared sessions

## Security Considerations
- CORS configured for frontend (default localhost:3000)
- JWT secrets via env vars (256-bit HS256)
- Password hashing (BCrypt assumed in User entity)
- IP-based rate limiting for anonymous users
- Stripe webhooks verified with secret

## Common Tasks
- **Add MT Type**: Implement `MtGenerator` interface in `/domain/swift/mt/`
- **Add MX Type**: Implement `MxGenerator` interface in `/domain/swift/mx/`
- **Modify Credits**: Update `CreditService.recordCreditUsage()` logic
- **Email Templates**: Customize in `EmailService` (Gmail SMTP)
- **Database Migrations**: Manual schema changes (ddl-auto=update for dev only)
