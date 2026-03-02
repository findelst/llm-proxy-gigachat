<!--
  SYNC IMPACT REPORT
  ==================
  Version change: NONE → 1.0.0 (initial constitution creation)

  Modified principles: N/A (initial creation)

  Added sections:
    - I. Domain-Driven Design & Clean Architecture
    - II. Technology Stack Discipline
    - III. Testing Standards
    - IV. Concurrency Model
    - V. Caching Strategy
    - Technology Stack (detailed constraints)
    - Development Workflow
    - Governance

  Removed sections: N/A

  Templates requiring updates:
    - .specify/templates/plan-template.md ✅ (no changes needed - generic)
    - .specify/templates/spec-template.md ✅ (no changes needed - generic)
    - .specify/templates/tasks-template.md ✅ (no changes needed - generic)

  Follow-up TODOs: None
-->

# LLM Proxy Kotlin Constitution

## Core Principles

### I. Domain-Driven Design & Clean Architecture

The project MUST follow Domain-Driven Design (DDD) methodology and Clean Architecture principles:

- **Domain layer independence**: Domain models and business logic MUST NOT depend on infrastructure,
  frameworks, or external services
- **Bounded contexts**: Clear separation between different subdomains (proxy, priority management,
  caching)
- **Repository pattern**: Data access MUST be abstracted through repository interfaces defined in
  the domain layer
- **Dependency inversion**: High-level modules MUST NOT depend on low-level modules; both MUST
  depend on abstractions
- **Ubiquitous language**: Code naming MUST reflect business domain terminology

**Rationale**: These patterns ensure maintainability, testability, and independence from external
frameworks, enabling the proxy service to evolve without coupling to specific implementations.

### II. Technology Stack Discipline

The project MUST strictly adhere to the defined technology stack:

- **Language**: Kotlin (JVM 21)
- **Framework**: Spring Boot for dependency injection, configuration, and web layer
- **LLM Integration**: langchain4j-gigachat library (https://github.com/ai-forever/langchain4j-gigachat)
- **No alternative LLM libraries**: All GigaChat interactions MUST go through langchain4j-gigachat
- **Build tool**: Gradle with Kotlin DSL

**Rationale**: Consistency in technology choices reduces cognitive load, simplifies debugging, and
ensures team expertise is concentrated rather than fragmented.

### III. Testing Standards

All code MUST be tested according to the following standards:

- **Test framework**: JUnit 5 with Spring Test for integration tests
- **Assertions**: Kotest assertions MUST be used for all test verifications
- **Test coverage**: New code MUST include corresponding tests
- **Test categories**:
  - Unit tests for domain logic (no Spring context)
  - Integration tests for repository and service layer (with Spring context)
  - Contract tests for external API boundaries

**Rationale**: Consistent testing tools enable shared patterns, easier code reviews, and reliable
test infrastructure.

### IV. Concurrency Model

The project MUST manage concurrency according to these rules:

- **Coroutines**: Kotlin coroutines MUST be used for asynchronous operations
- **Bounded parallelism**: The number of concurrent threads to GigaChat is fixed and configured
  externally (not dynamically scaled)
- **Structured concurrency**: All coroutines MUST be launched within a defined scope
- **No thread blocking**: Suspend functions MUST be preferred over blocking calls

**Rationale**: GigaChat API has rate limits; bounded parallelism prevents overwhelming the external
service while coroutines provide efficient resource utilization.

### V. Caching Strategy

Request caching MUST follow these guidelines:

- **Cache provider**: Caffeine MUST be used for all in-memory caching
- **Cache keys**: MUST be deterministic and based on request content
- **Cache invalidation**: TTL-based invalidation MUST be configured per cache type
- **Cache transparency**: Caching MUST be implemented as an infrastructure concern, not in domain
  layer

**Rationale**: Caffeine provides high-performance caching with automatic eviction policies, reducing
latency for repeated requests and lowering API costs.

## Technology Stack

### Core Dependencies

| Component | Technology | Version Constraint |
|-----------|------------|-------------------|
| Language | Kotlin | 2.x |
| JVM | OpenJDK | 21 |
| Framework | Spring Boot | 3.x |
| LLM Integration | langchain4j-gigachat | latest |
| Caching | Caffeine | 3.x |
| Testing | JUnit 5 | 5.x |
| Testing | Spring Test | (via Spring Boot) |
| Assertions | Kotest | 5.x |
| Build | Gradle | 8.x |

### Architecture Layers

```
┌─────────────────────────────────────────┐
│           Presentation Layer            │
│     (REST Controllers, DTOs)            │
├─────────────────────────────────────────┤
│          Application Layer              │
│     (Services, Use Cases)               │
├─────────────────────────────────────────┤
│            Domain Layer                 │
│  (Entities, Value Objects, Repositories)│
├─────────────────────────────────────────┤
│         Infrastructure Layer            │
│ (GigaChat client, Cache, DB, Config)    │
└─────────────────────────────────────────┘
```

## Development Workflow

### Code Organization

- Domain classes MUST be placed in `src/main/kotlin/ru/ddd/<context>/domain/`
- Application services MUST be placed in `src/main/kotlin/ru/ddd/<context>/application/`
- Infrastructure implementations MUST be placed in
  `src/main/kotlin/ru/ddd/<context>/infrastructure/`
- Tests MUST mirror source structure in `src/test/kotlin/ru/ddd/<context>/`

### Code Review Requirements

- All changes MUST pass existing tests
- New features MUST include corresponding tests
- Domain logic changes MUST be reviewed for DDD compliance
- Infrastructure changes MUST NOT leak into domain layer

### Quality Gates

- Build MUST succeed with `./gradlew build`
- Tests MUST pass with `./gradlew test`
- No compiler warnings in new code

## Governance

### Amendment Procedure

1. Propose amendment with clear rationale
2. Document impact on existing code
3. Update constitution version (semantic versioning)
4. Communicate changes to all team members

### Versioning Policy

- **MAJOR**: Breaking architectural changes, principle removals
- **MINOR**: New principles, expanded guidelines
- **PATCH**: Clarifications, typo fixes

### Compliance

- All PRs MUST be verified against constitution principles
- Deviations MUST be documented in code with justification comments
- Constitution supersedes conflicting practices

**Version**: 1.0.0 | **Ratified**: 2026-03-02 | **Last Amended**: 2026-03-02
