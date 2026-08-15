# Shades World - Sunglass Store Backend

A Spring Boot monolith e-commerce backend for a sunglasses store.

## Tech Stack

- **Java 21** + **Spring Boot 3.3.5**
- Spring Data JPA (Hibernate) with MySQL
- Spring Security with JWT (access + refresh token rotation)
- Bean Validation, Lombok, springdoc-openapi (Swagger)

## Prerequisites

- Java 21+
- MySQL 8.0+
- Maven 3.8+

## Setup

### 1. Create the database

```sql
CREATE DATABASE sunglass_store;
```

Run the full DDL script to create all 36 tables before starting the application. The application uses `spring.jpa.hibernate.ddl-auto=validate` — Hibernate will **not** create or modify the schema.

### 2. Configure environment variables

| Variable | Description | Default |
|---|---|---|
| `DB_HOST` | MySQL host | `localhost` |
| `DB_PORT` | MySQL port | `3306` |
| `DB_NAME` | Database name | `sunglass_store` |
| `DB_USERNAME` | Database user | `root` |
| `DB_PASSWORD` | Database password | `root` |
| `JWT_SECRET` | Base64-encoded HMAC key (min 256-bit) | — |
| `JWT_ACCESS_EXPIRATION` | Access token TTL (ms) | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (ms) | `604800000` (7 days) |
| `APP_CORS_ORIGINS` | Comma-separated allowed origins | `http://localhost:3000` |

### 3. Run

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. API docs

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Architecture

```
com.sunglassstore
├── config/          # CORS, OpenAPI configuration
├── controller/      # REST controllers
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities + enums
├── exception/       # Custom exceptions + global handler
├── repository/      # Spring Data JPA repositories
├── security/        # JWT, UserDetails, SecurityConfig
└── service/         # Service interfaces + impl/
```

## Key Design Decisions

- **Monolith**: Single deployable unit, no microservices complexity.
- **JWT with refresh token rotation**: Refresh tokens are SHA-256 hashed before storage. On refresh, the old token is revoked and a new one issued.
- **Pessimistic locking**: Inventory deduction uses `@Lock(PESSIMISTIC_WRITE)` to prevent overselling under concurrency.
- **Transactional order creation**: 15-step atomic flow — cart validation, price recheck, coupon application, inventory deduction, snapshot creation, cart rotation.
- **Soft deletes**: Products and categories use `isActive` flags instead of hard deletes.
- **Address snapshots**: Shipping address is denormalized onto the order at purchase time.
- **Order item snapshots**: Product name, SKU, and price are copied into `ORDER_ITEMS` at purchase time.
- **Mock payment processor**: `MockPaymentProcessor` always returns success — swap with a real gateway via the `PaymentProcessor` interface.
- **RBAC**: Four roles (CUSTOMER, ADMIN, SUPPORT, INVENTORY_MANAGER) with granular permission-based access control.

## Roles & Access

| Role | Access |
|---|---|
| CUSTOMER | Own profile, addresses, cart, wishlist, orders, reviews, returns |
| ADMIN | Everything |
| SUPPORT | View/update orders, manage returns/refunds/shipments, moderate reviews |
| INVENTORY_MANAGER | Create/update products, manage inventory |

## API Endpoints

### Public
- `POST /api/auth/register` — Register
- `POST /api/auth/login` — Login
- `POST /api/auth/refresh` — Refresh tokens
- `GET /api/products/**` — Browse products
- `GET /api/categories` — List categories
- `GET /api/reviews/products/{id}` — Product reviews

### Authenticated (Customer)
- `GET/POST/PUT/DELETE /api/addresses` — Manage addresses
- `GET/POST/PUT/DELETE /api/cart` — Cart operations
- `GET/POST/DELETE /api/wishlists` — Wishlist
- `POST /api/orders` — Place order
- `POST /api/orders/{id}/cancel` — Cancel order
- `POST /api/payments/orders/{id}` — Pay for order
- `POST /api/reviews` — Write review
- `POST /api/returns` — Request return
- `POST /api/coupons/validate` — Validate coupon

### Admin/Staff
- `GET /api/orders/admin/all` — All orders
- `PATCH /api/orders/admin/{id}/status` — Update order status
- `POST /api/shipments/orders/{id}` — Create shipment
- `POST /api/refunds/payments/{id}` — Process refund
- `POST/PUT/DELETE /api/products` — Manage products
- `POST/PUT/DELETE /api/coupons` — Manage coupons
- `POST /api/inventory/variants/{id}/adjust` — Adjust inventory
