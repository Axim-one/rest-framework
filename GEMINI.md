# GEMINI.md

## Project Overview

This is a Java-based REST framework built on top of Spring Boot. It provides a set of annotations and classes to simplify the creation of RESTful web services. The framework is divided into three modules: `core`, `rest-api`, and `mybatis`.

*   **`core` module:** Contains basic data structures for pagination (`XPage`, `XPagination`) and sorting (`XOrder`, `XDirection`), and a utility for naming conversion (`NamingConvert`).
*   **`rest-api` module:** This is a comprehensive REST framework that includes:
    *   **Annotations:** for defining REST APIs, services, and query parameters.
    *   **Configuration:** for setting up the REST client, message sources, and web MVC.
    *   **Controllers:** for handling session management and abstracting common controller logic.
    *   **Exceptions:** for handling various API errors.
    *   **Filters:** for request filtering.
    *   **Handlers:** for request processing, access token parsing, and exception handling.
    *   **Models:** for error responses and session data.
    *   **Proxy:** for creating REST client proxies.
    *   **Resolvers:** for resolving page nation and session data from requests.
    *   **Service:** for encryption.
    *   **Type:** for defining REST client types.
*   **`mybatis` module:** Provides integration with the MyBatis persistence framework.

## Building and Running

The project uses Gradle for building.

**To build the project:**

```bash
./gradlew build
```

**To run the application:**

This is a library project, so there is no main application to run. To use this framework, you would include it as a dependency in your Spring Boot application.

## Development Conventions

*   **Java 17:** The project is built with Java 17.
*   **Spring Boot:** The framework is built on top of Spring Boot.
*   **Lombok:** The project uses Lombok to reduce boilerplate code.
*   **SLF4J:** The project uses SLF4J for logging.
*   **Annotations:** The framework uses custom annotations (`@XRestAPI`, `@XRestService`, etc.) to simplify the creation of REST endpoints.
*   **REST Client:** The framework includes a REST client with a proxy implementation for making requests to other services.
*   **Exception Handling:** The framework has a centralized exception handling mechanism.
*   **Pagination and Sorting:** The `core` module provides data structures for pagination and sorting.
