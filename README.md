# Skroutz Web Scraper

A Spring Boot application for scraping product information from Skroutz.gr using Jsoup for HTML parsing.

## Features


- **Product Scraping**: Automated product data extraction from Skroutz.gr (single or multi-page) with category classification
- **Specifications Parsing**: Structured specification extraction based on the category schema
- **Reviews Scraping**: Paginated review fetching via Skroutz's JSON API
- **Price History Scraping**: Historical price data extraction
- **AI Review Summarization**: LLM-powered summarization of product reviews using a local Ollama model via LangChain4j; supports chunk-based processing for large review sets and caches results per product
- **Product Search**: Elasticsearch-powered autocomplete and search functionality
- **Database Storage**: PostgreSQL with JSONB support for specifications
- **Search Infrastructure**: Elasticsearch 9.3.0 with Kibana for data visualization
- **REST API**: Endpoints for triggering scraping operations and querying products
- **Access Logging**: HTTP request/response logging filter with method, path, status, and duration
- **API Documentation**: Swagger/OpenAPI integration
- **Docker Support**: PostgreSQL, Elasticsearch, Kibana, and Ollama containerization
- **Code Coverage**: JaCoCo with 80% line/method and 70% branch coverage thresholds

## Technology Stack

- **Java 25**
- **Spring Boot 4.0.1**
- **Spring Data JPA** — database operations
- **Spring Data Elasticsearch** — search and autocomplete functionality
- **Spring WebFlux** — reactive HTTP client (reviews API)
- **Jsoup** — HTML parsing and web scraping
- **LangChain4j** — AI service integration and LLM orchestration
- **Ollama** — local LLM inference (default model: `qwen2.5:3b`)
- **PostgreSQL 15** — primary database with JSONB support
- **Elasticsearch 9.3.0** — search engine for product indexing and autocomplete
- **Kibana 9.3.0** — data visualization and Elasticsearch management
- **Flyway** — database migrations
- **MapStruct** — object mapping
- **Lombok** — boilerplate reduction
- **Swagger/OpenAPI** — API documentation
- **Docker Compose** — PostgreSQL, Elasticsearch, Kibana, and Ollama containerization

### Testing

- **Spock Framework (Groovy 5)** — unit and functional tests
- **MockWebServer** — HTTP mock server for integration tests
- **WireMock** — HTTP stubbing for functional tests
- **JaCoCo** — code coverage reporting

## Prerequisites

- Java 25
- Gradle
- Docker and Docker Compose

## Getting Started

### Option A: Full Stack via Docker (Recommended)

Run the entire application — including the app itself — in Docker:

```bash
docker-compose up -d
```

This builds and starts:
- **skroutz-web-scraper** (port 8082) — the application (built from source via `Dockerfile`)
- **PostgreSQL** (port 5432) — product database
- **Elasticsearch** (port 9200) — search engine
- **Kibana** (port 5601) — Elasticsearch UI and visualization
- **Ollama** (port 11434) — local LLM inference server

On first run, Ollama executes `model_files/run_ollama.sh` to pull and load the configured model. The application waits for all services to be healthy before starting.

The application will be available at `http://localhost:8082`.

### Option B: Local Development (Infrastructure Only)

Start only the infrastructure services and run the app locally with Gradle:

**1. Start infrastructure:**

```bash
docker-compose -f docker-compose-local.yml up -d
```

This starts:
- **PostgreSQL** (port 5432)
- **Elasticsearch** (port 9200)
- **Kibana** (port 5601)

**2. Build and run the application:**

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8082`.


## API Documentation

Once the application is running:

- **Swagger UI**: http://localhost:8082/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8082/api-docs
- **Kibana Dashboard**: http://localhost:5601 (Elasticsearch data visualization)

## Project Structure

```
src/
├── main/
│   ├── java/org/skroutz/scraper/skroutzwebscraper/
│   │   ├── agent/             # LangChain4j AI agents (ReviewSummarizer)
│   │   ├── config/            # Configuration (OpenAPI, WebClient, Elasticsearch, AI, AccessLogFilter)
│   │   ├── controller/        # REST controllers (ScraperController, ProductsController, ReviewsController)
│   │   ├── exception/         # Global exception handling
│   │   ├── document/          # Elasticsearch documents (ProductDocument)
│   │   ├── dto/               # Data Transfer Objects
│   │   ├── entity/            # JPA entities (Product, Review, PriceHistory, ReviewSummary)
│   │   ├── mapper/            # MapStruct mappers
│   │   ├── repository/        # Spring Data repositories (JPA + Elasticsearch)
│   │   ├── scraper/           # Web scraping logic (Products, Specifications, Reviews, PriceHistory)
│   │   ├── service/           # Business logic (ProductsService, ReviewsSummarizationService, …)
│   │   └── utils/             # Utility classes (ReviewChunker, DateTimeUtils, …)
│   └── resources/
│       ├── application.yml         # Application configuration
│       ├── db/migration/           # Flyway database migrations (V1–V4)
│       └── elasticsearch/          # Elasticsearch index settings and mappings
├── test/                           # Spock unit tests (Groovy)
└── functionalTest/                 # Spock functional tests with Docker Compose
```

### Category Schema

Category schemas define how raw product specifications (JSON nodes) are mapped and normalized into the structured specification document stored in the database and indexed for search.

Location
- `src/main/java/org/skroutz/scraper/skroutzwebscraper/schema`
- Main classes: `CategoryMappingSchema`, `DirectFieldMapping`, `FeatureExtraction`, `FeatureFieldMapping`, `FieldType`

Purpose
- Map raw JSON paths from scraped specification blobs to normalized keys and types.
- Support direct field mappings (single values with optional types like `INTEGER` or `NUMERIC`) and array/feature mappings (multiple extraction modes).

Extraction modes (via `FeatureExtraction`)
- `VALUE`: take the raw text value.
- `COMMA_SPLIT`: split comma-separated values into an array.
- `YES_GROUP` / `YES_KEY`: extract flags represented as "Yes" values into arrays.

How it is used
- `SpecsNormalizerService.normalize(JsonNode rawSpecs, CategoryMappingSchema schema)` applies the schema to a raw JSON node and returns a normalized JSON object as a string.
- Category schemas are loaded and applied during the specifications parsing flow so that product `specifications` and `elasticSearchSpecifications` fields are populated consistently.

Adding or modifying a schema
- Update or create mapping definitions in the `schema` package classes.
- Persist the schema (the application reads category schemas at runtime) so the normalizer can apply them when parsing specifications.

Example

    // java
    import com.fasterxml.jackson.databind.JsonNode;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import org.skroutz.scraper.skroutzwebscraper.schema.CategoryMappingSchema;
    import org.skroutz.scraper.skroutzwebscraper.schema.DirectFieldMapping;
    import org.skroutz.scraper.skroutzwebscraper.schema.FeatureFieldMapping;
    import org.skroutz.scraper.skroutzwebscraper.schema.FeatureExtraction;
    import org.skroutz.scraper.skroutzwebscraper.schema.FieldType;
    import org.skroutz.scraper.skroutzwebscraper.scraping.utils.SpecificationsNormalizerUtils;

    import java.util.List;

    public class SpecsNormalizerExample {
        public static void main(String[] args) throws Exception {
            ObjectMapper mapper = new ObjectMapper();

            CategoryMappingSchema schema = CategoryMappingSchema.builder()
                    .directFields(List.of(
                            DirectFieldMapping.builder().path("Main Specifications.Colour").target("colour").type(FieldType.STRING).build(),
                            DirectFieldMapping.builder().path("Main Specifications.Release Year").target("release_year").type(FieldType.INTEGER).build(),
                            DirectFieldMapping.builder().path("Processor & Memory.RAM").target("ram_gb").type(FieldType.NUMERIC).build()
                    ))
                    .arrayFields(List.of(
                            FeatureFieldMapping.builder().path("Main Specifications.SIM").target("features").type(FeatureExtraction.VALUE).build(),
                            FeatureFieldMapping.builder().path("Network & Connectivity.Connectivity").target("features").type(FeatureExtraction.COMMA_SPLIT).build(),
                            FeatureFieldMapping.builder().path("AI Features").target("ai_features").type(FeatureExtraction.YES_GROUP).build()
                    ))
                    .build();

            String rawSpecs = """
                {
                  "Main Specifications": {
                    "SIM": "eSIM",
                    "Colour": "Black",
                    "Release Year": "2024"
                  },
                  "Processor & Memory": {
                    "RAM": "8 GB"
                  },
                  "Network & Connectivity": {
                    "Connectivity": "Bluetooth, Wi-Fi, USB-C"
                  },
                  "AI Features": {
                    "AI Photo Editing": "Yes",
                    "AI Call/Speech Translation": "No",
                    "AI Text Generation": "Yes"
                  }
                }
                """;

            JsonNode inputNode = mapper.readTree(rawSpecs);

            SpecsNormalizerService normalizer = new SpecsNormalizerService(mapper);
            String normalized = normalizer.normalize(inputNode, schema);

            System.out.println("Normalized JSON:");
            System.out.println(normalized);
        }
    }

    // Expected (formatted) result example:
    // {
    //   "colour":"Black",
    //   "release_year":2024,
    //   "ram_gb":8,
    //   "features":["eSIM","Bluetooth","Wi-Fi","USB-C"],
    //   "ai_features":["AI Photo Editing","AI Text Generation"]
    // }

## Development

### Running Unit Tests

```bash
./gradlew test
```

### Running Functional Tests

Functional tests use Docker Compose to spin up PostgreSQL, Elasticsearch, and mock HTTP servers:

```bash
./gradlew functionalTest
```

### Running All Tests with Coverage

```bash
./gradlew test functionalTest jacocoTestReport
```

Coverage report is generated at `build/reports/jacoco/test/html/index.html`.

### Building for Production

```bash
./gradlew build
```

The JAR file will be created in `build/libs/`.

## Important Notes

> **Responsible Scraping**: This tool is intended for educational and research purposes. Please ensure you:
> - Respect the website's robots.txt file
> - Don't overload the target server with requests
> - Comply with the website's terms of service
> - Use appropriate delays between requests

## License

This project is for educational purposes only.
