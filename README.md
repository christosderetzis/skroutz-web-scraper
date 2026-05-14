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

### 1. Start the Infrastructure

**Without AI summarization:**

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** (port 5432) — product database
- **Elasticsearch** (port 9200) — search engine
- **Kibana** (port 5601) — Elasticsearch UI and visualization

**With AI summarization (includes Ollama):**

```bash
docker-compose -f docker-compose-with-ollama.yml up -d
```

This additionally starts:
- **Ollama** (port 11434) — local LLM inference server

On first run, Ollama executes `model_files/run_ollama.sh` to pull and load the configured model.

### 2. Build the Application

```bash
./gradlew build
```

### 3. Run the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8082`.

## API Endpoints

### Scraper Controller (`/scraper`)

#### Scrape Products

**POST** `/scraper/products?multiple={boolean}`

Scrapes product listings from a Skroutz category URL. Products are automatically categorized and indexed in Elasticsearch.

```bash
curl -X POST "http://localhost:8082/scraper/products?multiple=true" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.skroutz.gr/c/40/kinhta-tilefwna.html", "category": "Mobile Phones"}'
```

- `multiple`: If `true`, scrapes all pages in the category
- `category` (optional): Category name for classification (e.g., "Mobile Phones", "Laptops")

#### Scrape Specifications

**POST** `/scraper/specifications`

Parses specifications for all products that haven't been parsed yet. Values are automatically structured into `{key, value, unit}` objects where applicable.

```bash
curl -X POST "http://localhost:8082/scraper/specifications"
```

#### Scrape Reviews

**POST** `/scraper/reviews`

Fetches reviews for all products that haven't been parsed yet.

```bash
curl -X POST "http://localhost:8082/scraper/reviews"
```

#### Scrape Price History

**POST** `/scraper/price-history`

Fetches price history for all products that haven't been parsed yet.

```bash
curl -X POST "http://localhost:8082/scraper/price-history"
```

### Reviews Controller (`/reviews`)

#### Summarize Reviews

**POST** `/reviews/{id}/summarize`

Generates an AI-powered summary of all reviews for a product. Reviews are split into chunks if they exceed the configured character limit, each chunk is summarized individually, then a final summary is produced by merging the chunk summaries. The result is cached — subsequent calls for the same product return the stored summary immediately.

Returns `400 Bad Request` if reviews have not been scraped yet or if no review text is available. Returns `503 Service Unavailable` if the AI backend is unreachable.

```bash
curl -X POST "http://localhost:8082/reviews/1/summarize"
```

Response:

```json
{
  "summary": "The iPhone 13 is praised for its camera quality and battery life...",
  "pros": ["Excellent camera", "Long battery life", "Smooth performance"],
  "cons": ["No charger in box", "Heavy weight"],
  "sentiment": "Positive"
}
```

### Products Controller (`/products`)

#### Get Product by ID

**GET** `/products/{id}`

Returns product details including specifications, price, category, and rating.

```bash
curl "http://localhost:8082/products/1"
```

#### Autocomplete Search

**GET** `/products/autocomplete?q={query}&limit={limit}`

Provides autocomplete suggestions for product search using Elasticsearch.

```bash
curl "http://localhost:8082/products/autocomplete?q=iphone&limit=5"
```

Parameters:
- `q` (required): Search query string
- `limit` (optional): Maximum number of suggestions to return (default: 5)

Response includes matching products with title, price, rating, and category.

## Configuration

### Database Configuration

| Setting  | Default         |
|----------|-----------------|
| Host     | localhost       |
| Port     | 5432            |
| Database | skroutz_scraper |
| Username | skroutz_user    |
| Password | skroutz_password|

### Elasticsearch Configuration

| Setting  | Default               |
|----------|-----------------------|
| Host     | localhost             |
| Port     | 9200                  |
| URI      | http://localhost:9200 |

### Scraper Configuration

| Setting                              | Default                  |
|--------------------------------------|--------------------------|
| Base URL                             | https://www.skroutz.gr   |
| Timeout                              | 30 seconds               |
| Max Retries                          | 3                        |
| Delay — review page (ms)            | 100                      |
| Delay — specifications (ms)         | 500                      |
| Delay — reviews (ms)                | 2000                     |
| Delay — price history (ms)          | 1000                     |
| Specifications batch size            | 30                       |

### AI / LLM Configuration

| Setting                         | Env var              | Default                          |
|---------------------------------|----------------------|----------------------------------|
| LLM base URL                    | `LLM_BASE_URL`       | `http://localhost:11434/v1`      |
| LLM model name                  | `LLM_MODEL`          | `qwen2.5:3b`                     |
| Max tokens per response         | —                    | 16000                            |
| Log LLM responses               | `LLM_LOG_RESPONSES`  | `false`                          |
| Review chunk size (characters)  | —                    | 10000                            |

The AI layer uses the OpenAI-compatible API, so any Ollama model (or any OpenAI-compatible endpoint) can be substituted by changing `LLM_BASE_URL` and `LLM_MODEL`.

Configuration can be modified in `src/main/resources/application.yml`.

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
