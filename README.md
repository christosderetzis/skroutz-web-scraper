# Skroutz Web Scraper

A Spring Boot application for scraping product information from Skroutz.gr.

## Features

- Product, specifications, reviews, and price history scraping from Skroutz.gr
- AI-powered review summarization via Ollama + LangChain4j
- Elasticsearch-powered search, autocomplete, and similar product recommendations
- PostgreSQL storage with JSONB specifications
- Swagger/OpenAPI docs, access logging, Docker support
- JaCoCo coverage enforcement (80% line/method, 70% branch)

## Tech Stack

Java 25, Spring Boot 4.0.1, PostgreSQL 15, Elasticsearch 9.3, Kibana 9.3, Ollama, Jsoup, LangChain4j, MapStruct, Flyway, Lombok, SpringDoc OpenAPI.

Tests: Spock (Groovy 5), MockWebServer, WireMock, JaCoCo.

## Prerequisites

- Java 25 (or `.sdkmanrc` via SDKMAN)
- Docker and Docker Compose

## Getting Started

### Option A: Full Docker Stack (Recommended)

```bash
docker-compose up -d
```

Starts the app (port 8082), PostgreSQL (5432), Elasticsearch (9200), Kibana (5601), and Ollama (11434). App waits for all services to be healthy.

### Option B: Local Dev

```bash
docker-compose -f docker-compose-local.yml up -d
./gradlew bootRun
```

Starts infrastructure only, then runs the app locally on port 8082.

## API

- **Swagger UI**: http://localhost:8082/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8082/api-docs
- **Kibana**: http://localhost:5601

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/scraper/products` | Scrape products from a URL |
| POST | `/scraper/specifications` | Parse specs for pending products |
| POST | `/scraper/reviews` | Scrape reviews for pending products |
| POST | `/scraper/price-history` | Fetch price history for pending products |
| POST | `/reviews/{id}/summarize` | Summarize reviews for a product via LLM |
| GET | `/products/autocomplete?q=...` | Autocomplete suggestions |
| POST | `/products/search` | Full-text search with filters |
| GET | `/products/{id}/similar` | Find similar products |

## Project Structure

```
src/main/java/org/skroutz/scraper/skroutzwebscraper/
├── category/      # Category schema module
├── common/        # Shared config, exceptions, utils
├── priceHistory/  # Price history module
├── product/       # Product module
├── review/        # Review module + AI summarizer
├── scraping/      # Scraping module (controllers, services, scrapers)
└── search/        # Search module (Elasticsearch indexing, query, autocomplete)
```

Each module follows hexagonal architecture: application → domain → infrastructure layers.

## Development

```bash
./gradlew test              # Unit tests (Spock)
./gradlew functionalTest    # Functional tests (Docker-backed: Postgres, ES, WireMock, Nginx mock)
./gradlew functionalTestNoDeps  # Functional tests without Docker
./gradlew test functionalTest jacocoTestReport  # All tests + coverage report
./gradlew build             # Full build with coverage checks
```

Coverage report: `build/reports/jacoco/test/html/index.html`

## Category Schema

Category schemas map raw scraped specification JSON to normalized keys and types. Used during specs parsing to populate product `specifications` and `elasticSearchSpecifications`.

- **Direct field mappings**: single values with type coercion (`STRING`, `INTEGER`, `NUMERIC`)
- **Feature field mappings**: array extraction (`VALUE`, `COMMA_SPLIT`, `YES_GROUP`, `YES_KEY`)

The `SpecsNormalizerService.normalize(rawJsonNode, schema)` method applies a schema to produce normalized JSON. Schemas are persisted and loaded at runtime via `CategorySchema` entity + REST endpoints.

## Notes

> **Responsible Scraping**: Educational and research use only. Respect robots.txt, avoid overloading servers, and comply with Skroutz.gr's terms of service.

## License

Educational purposes only.
