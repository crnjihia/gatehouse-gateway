# Contributing to Gatehouse Gateway

First off, thank you for considering contributing to Gatehouse Gateway! We welcome contributions, bug reports, feature requests, and pull requests from developers of all backgrounds.

---

## Code of Conduct

We expect all contributors to adhere to standard respectful, professional, and welcoming open-source community standards. Please treat others with respect and constructive collaboration.

---

## Development Environment Setup

### Prerequisites
- **JDK 17 or 21+** (`java -version`)
- **Maven 3.9+** or use included `./mvnw`
- **Docker & Docker Compose** (for running PostgreSQL, Redis, Prometheus, Grafana, and WireMock)

### Build and Test
Clone the repository and verify the test suite:
```bash
git clone https://github.com/crnjihia/gatehouse-gateway.git
cd gatehouse-gateway
./mvnw clean test
```

### Running Local Infrastructure
Start required services before launching the gateway in dev mode:
```bash
docker compose up -d postgres redis wiremock
```
Then run the Spring Boot application:
```bash
./mvnw spring-boot:run
```

---

## Branching & Commit Guidelines

1. Fork the repo and create your branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Adhere to **Conventional Commits**:
   - `feat:` New features or capabilities
   - `fix:` Bug fixes
   - `perf:` Performance optimizations
   - `docs:` Documentation improvements
   - `test:` Adding or improving tests
   - `refactor:` Code reorganization without functional changes
   - `ci:` Continuous integration and automation workflows
3. Ensure all automated tests pass:
   ```bash
   ./mvnw test
   ```
4. Push your branch and open a Pull Request against `main`.

---

## Coding Standards

- **Reactive Core**: Gatehouse is built on Spring Cloud Gateway Reactive and Project Reactor. Avoid blocking I/O calls (`block()`, `blockFirst()`, thread sleeps) within filters and Netty worker threads.
- **Security**: Never log sensitive tokens or secrets. Use `RequestResponseTransformationFilterFactory` for PII/PCI masking.
- **Documentation**: Keep the interactive documentation portal (`docs/`) updated if adding new filters or configuration properties.
