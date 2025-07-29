# Skroutz Web Scraper

A Spring Boot application for scraping product information from Skroutz.gr using Selenium WebDriver.

## Features

- **Web Scraping**: Automated product data extraction from Skroutz.gr
- **Multi-page Support**: Scrape multiple pages of search results
- **Database Storage**: Store scraped products in PostgreSQL database
- **REST API**: RESTful endpoints for triggering scraping operations
- **API Documentation**: Swagger/OpenAPI integration for API documentation
- **Docker Support**: PostgreSQL database containerization

## Technology Stack

- **Java 21**: Modern Java development
- **Spring Boot 3.5.4**: Application framework
- **Spring Data JPA**: Database operations and entity management
- **Selenium WebDriver**: Web scraping automation
- **PostgreSQL**: Primary database
- **Flyway**: Database migration management
- **Swagger/OpenAPI**: API documentation
- **Docker Compose**: Database containerization
- **Lombok**: Code generation and boilerplate reduction

## Prerequisites

- Java 21
- Gradle
- Docker and Docker Compose (for database)

## Getting Started

### 1. Start the Database

```bash
docker-compose up -d
```

This will start a PostgreSQL container with the required database configuration.

### 2. Build the Application

```bash
./gradlew build
```

### 3. Run the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## API Endpoints

### Scrape Products

**POST** `/products/scrape?multiple={boolean}`

Scrapes product data from a given Skroutz URL.

**Request Body:**
```json
{
  "url": "https://www.skroutz.gr/c/40/kinhta-tilefwna.html"
}
```

**Parameters:**
- `multiple` (boolean): Whether to scrape multiple pages or just the first page

**Example:**
```bash
curl -X POST "http://localhost:8080/products/scrape?multiple=true" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.skroutz.gr/c/40/kinhta-tilefwna.html"}'
```

## Configuration

### Database Configuration

The application uses PostgreSQL with the following default settings:

- **Host**: localhost
- **Port**: 5432
- **Database**: skroutz_scraper
- **Username**: skroutz_user
- **Password**: skroutz_password

### Scraper Configuration

- **Headless Mode**: Enabled by default
- **Timeout**: 100 seconds

Configuration can be modified in `src/main/resources/application.yml`.

## API Documentation

Once the application is running, you can access the API documentation at:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

## Project Structure

```
src/
├── main/
│   ├── java/org/skroutz/scraper/skroutzwebscraper/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── dto/            # Data Transfer Objects
│   │   ├── entity/         # JPA entities
│   │   ├── repository/     # Data repositories
│   │   ├── scraper/        # Web scraping logic
│   │   └── service/        # Business logic services
│   └── resources/
│       ├── application.yml  # Application configuration
│       └── db/migration/   # Database migrations
└── test/                   # Test classes
```

## Database Schema

The application uses Flyway for database migrations. The initial migration creates a `Product` table to store scraped product information.

## Development

### Running Tests

```bash
./gradlew test
```

### Building for Production

```bash
./gradlew build
```

The JAR file will be created in `build/libs/`.

## Important Notes

⚠️ **Responsible Scraping**: This tool is intended for educational and research purposes. Please ensure you:
- Respect the website's robots.txt file
- Don't overload the target server with requests
- Comply with the website's terms of service
- Use appropriate delays between requests

## License

This project is for educational purposes only.