# Skroutz Web Scraper

A Spring Boot application for scraping product information from Skroutz.gr using Jsoup for HTML parsing.

## Features


- **Product Scraping**: Automated product data extraction from Skroutz.gr (single or multi-page) with category classification
- **Specifications Parsing**: Structured specification extraction with automatic numeric/unit detection (e.g. `174 gr` → `{value: 174, unit: "gr"}`)
- **Reviews Scraping**: Paginated review fetching via Skroutz's JSON API
- **Price History Scraping**: Historical price data extraction
- **Product Search**: Elasticsearch-powered autocomplete and search functionality
- **Database Storage**: PostgreSQL with JSONB support for specifications
- **Search Infrastructure**: Elasticsearch 9.3.0 with Kibana for data visualization
- **REST API**: Endpoints for triggering scraping operations and querying products
- **API Documentation**: Swagger/OpenAPI integration
- **Docker Support**: PostgreSQL, Elasticsearch, and Kibana containerization
- **Code Coverage**: JaCoCo with 80% line/method and 70% branch coverage thresholds

## Technology Stack

- **Java 25**
- **Spring Boot 4.0.1**
- **Spring Data JPA** — database operations
- **Spring Data Elasticsearch** — search and autocomplete functionality
- **Spring WebFlux** — reactive HTTP client (reviews API)
- **Jsoup** — HTML parsing and web scraping
- **PostgreSQL 15** — primary database with JSONB support
- **Elasticsearch 9.3.0** — search engine for product indexing and autocomplete
- **Kibana 9.3.0** — data visualization and Elasticsearch management
- **Flyway** — database migrations
- **MapStruct** — object mapping
- **Lombok** — boilerplate reduction
- **Swagger/OpenAPI** — API documentation
- **Docker Compose** — PostgreSQL, Elasticsearch, and Kibana containerization

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

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** (port 5432) — product database
- **Elasticsearch** (port 9200) — search engine
- **Kibana** (port 5601) — Elasticsearch UI and visualization

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

## Specifications Format

Specifications are stored as structured JSONB. Each category contains an array of key/value/unit objects:

```json
{
  "Dimensions": [
    {"key": "Length", "value": 146.7, "unit": "mm"},
    {"key": "Width", "value": 71.5, "unit": "mm"}
  ],
  "Main Specifications": [
    {"key": "Colour", "value": "Beige"},
    {"key": "Weight", "value": 174, "unit": "gr"},
    {"key": "Model", "value": "iPhone 13"}
  ]
}
```

- Numeric values with units: `174 gr` → `{value: 174, unit: "gr"}`
- Decimal values: `3.22 GHz` → `{value: 3.22, unit: "GHz"}`
- Comma decimals: `72,8 cfm` → `{value: 72.8, unit: "cfm"}`
- Greek units: `3 τμχ` → `{value: 3, unit: "τμχ"}`
- Plain strings: `iOS` → `{value: "iOS"}`
- Complex values: `1920x1080`, `16:9` → kept as strings

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

| Setting          | Default                  |
|------------------|--------------------------|
| Timeout          | 30 seconds               |
| Base URL         | https://www.skroutz.gr   |

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
│   │   ├── config/            # Configuration (OpenAPI, WebClient, Elasticsearch)
│   │   ├── controller/        # REST controllers (ScraperController, ProductsController)
│   │   ├── controllerAdvice/  # Global exception handling
│   │   ├── document/          # Elasticsearch documents (ProductDocument)
│   │   ├── dto/               # Data Transfer Objects
│   │   ├── entity/            # JPA entities (Product, Review, PriceHistory)
│   │   ├── mapper/            # MapStruct mappers
│   │   ├── repository/        # Spring Data repositories (JPA + Elasticsearch)
│   │   ├── scraper/           # Web scraping logic (Products, Specifications, Reviews, PriceHistory)
│   │   ├── service/           # Business logic services (ProductsService, ProductSearchService)
│   │   └── utils/             # Utility classes
│   └── resources/
│       ├── application.yml         # Application configuration
│       ├── db/migration/           # Flyway database migrations
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
