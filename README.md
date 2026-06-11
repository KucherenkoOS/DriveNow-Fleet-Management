# 🚗 Car Sharing Service

## 📌 Introduction
Car Sharing Service is a RESTful API for managing a car rental platform.  
It allows users to browse available cars, create rentals, and complete payments online.

The project demonstrates backend development skills using the Spring Boot ecosystem, clean architecture principles, and integration with external services.

---

## 🛠️ Technologies

- Java **17**
- Spring Boot **3.4.2**
- Spring Security
- Spring Data JPA (Hibernate)
- Liquibase
- MySQL
- MapStruct **1.5.5.Final**
- JJWT **0.13.0**
- Stripe API **32.0.0**
- Springdoc OpenAPI **2.8.5**
- Docker & Docker Compose
- Log4j2 **2.17.2**
- JUnit & Mockito (Spring Boot Test)

---

## ⚙️ Features

### 🔐 Authentication
- Register new user
- Login (JWT authentication)

### 👤 Users
- View own profile
- Update profile
- Assign roles (MANAGER only)

### 🚘 Cars
- Browse cars (pagination, sorting)
- View car details
- Create/update/delete cars (MANAGER only)

### 📅 Rentals
- Create rental
- Return rental
- View rentals (own / all for MANAGER)

### 💳 Payments
- Create Stripe payment session
- Handle success/cancel callbacks
- Renew expired payments
- Support fine payments (late returns)

### 🔔 Notifications
- Telegram notifications for:
    - New rentals
    - Overdue rentals
    - Payment events

### 🔔 Notifications
- Telegram notifications for:
    - New rentals
    - Overdue rentals
    - Payment events

### ⏳ Background Tasks (Schedulers)
The application runs automated scheduled tasks to maintain data consistency and provide timely alerts:
- **Overdue Rentals Checker:** Runs daily at 09:00 AM. Scans the database for active rentals that have passed their planned return date and broadcasts an alert via Telegram.
- **Payment Status Synchronizer:** Runs every minute. Automatically fetches the latest status of `PENDING` payment sessions from the Stripe API and updates the local database accordingly (e.g., marking them as `PAID`, `EXPIRED`, or `FAILED`).

---

## 🔐 Security

- JWT-based authentication
- Password hashing using BCrypt
- Role-based access control (RBAC)
- Protected endpoints for MANAGER operations

---

## 🔐 Environment Variables

The project uses environment variables for configuration.

Create a `.env` file in the root directory:

```env
#Database
MYSQLDB_DATASOURCE_URL=
MYSQLDB_ROOT_USER=
MYSQLDB_ROOT_PASSWORD=
MYSQLDB_DATABASE=
MYSQLDB_USER=
MYSQLDB_PASSWORD=
MYSQLDB_LOCAL_PORT=3307
MYSQLDB_DOCKER_PORT=3306
SPRING_LOCAL_PORT=8080
SPRING_DOCKER_PORT=8080
#JWT
JWT_SECRET_KEY=
JWT_EXPIRATION=
#Telegram
TELEGRAM_BOT_TOKEN=
TELEGRAM_ADMIN_CHAT_ID=
#Stripe
STRIPE_SECRET_KEY=sk_test_...
STRIPE_SUCCESS_URL=
STRIPE_CANCEL_URL=
```

## ⚠️ Error Handling

### 📝 Logging

The application uses **Log4j2** for logging.

Logging is integrated with the global exception handling to ensure all errors are properly recorded.

#### 🔍 Logging Strategy

- All exceptions are logged in `GlobalExceptionHandler`
- Error-level logs (`ERROR`) are used for unexpected failures
- Warning-level logs (`WARN`) are used for business-related exceptions

#### 📌 Examples

- Failed payment session creation
- Telegram notification errors
- Invalid user requests
- Database access issues

#### ⚠️ Notes

- Logs help with debugging and monitoring application behavior
- Sensitive data (passwords, tokens) is not logged

---

The application uses a centralized error handling mechanism based on `@ControllerAdvice`.

### 🔧 Global Exception Handler

All exceptions are handled in a single place using a custom `GlobalExceptionHandler`, which extends `ResponseEntityExceptionHandler`.

---

### 📌 Handled Exceptions

| Exception                   | HTTP Status | Description                          |
|-----------------------------|------------|--------------------------------------|
| DataProcessingException     | 500        | Internal server/database errors      |
| EntityNotFoundException     | 404        | Entity not found                     |
| RegistrationException       | 409        | User already exists / conflict       |
| StripeSessionException      | 400        | Invalid order request                |
| Exception (global fallback) | 500        | Unexpected server errors             |

---

### 🧾 Validation Errors

Validation errors (e.g. invalid request body) are handled automatically.

---
## ▶️ How to Run

### 1. Clone repo
```bash
git clone https://github.com/KucherenkoOS/Car-Sharing-Service.git
cd Car-Sharing-Service
```
### 2. Run with Docker
Make sure you have Docker and Docker Compose installed.

Build and start containers:

```bash
docker-compose up --build
```
This will start:

- MySQL database
- Spring Boot application

To stop:
```bash
docker-compose down
```

### 3. Application will be available at:
http://localhost:8080

## 📬 API Endpoints

### 🔐 Authentication
#### Register
POST /auth/register
- Creates a new user account
- Available without authorization

#### Login
POST /auth/login
- Returns JWT token
- Available without authorization

---

### Cars
GET /cars
- Page with all cars (pagination and sorting)

GET /cars/{id}
- Details about specific car

POST /cars
- Creating new car entity (Manager operation)

PATCH /cars/{id}
- Updating existing car (Manager operation)

DELETE /cars/{id}
- Delete car (Manager operation, soft delete)
---
### Users
GET /users/me
- Get user profile info

PATCH /users/me
- Update user profile info

PUT /users/{id}/role
- Update role for user (Manager operation)
---
### Rentals
POST /rentals
- Create a new rental

GET /rentals
- List of all rentals with pagination and sorting (Manager can browse all rentals, sort active)

GET /rentals/{id}
- Watch details about specific rental

POST /rentals/{id}/return
- Return rental
---
### Payments
POST /payments
- Creating payment session

GET /payments
- Get user payments (Manager may choose specific user)

GET /payments/success
- Proceed success payment

GET /payments/cancel
- Cancel payment session

POST /payments/{id}/renew
- Renew expired payment session

---

## 🔑 Authorization

Most endpoints require JWT token:

Authorization: Bearer <your_token>

---

## 🔐 Access Control Summary

The application uses role-based access control (RBAC) with two roles:

- **USER** – regular customer
- **MANAGER** – admin-level access

---

### 🌐 Public Endpoints (No Authentication Required)

| Endpoint          | Method | Description              |
|-------------------|--------|--------------------------|
| /auth/registration | POST   | Register new user        |
| /auth/login       | POST   | Authenticate user (JWT)  |
| /payments/success | GET    | Handle successful payment |
| /payments/cancel  | GET    | Handle cancelled payment |

---

### 👤 USER Permissions

| Resource  | Endpoint           | Method | Description                  |
|----------|--------------------|--------|------------------------------|
| Cars     | /cars              | GET    | View all cars                |
| Cars     | /cars/{id}         | GET    | View car details             |
| Users    | /users/me          | GET    | View own profile             |
| Users    | /users/me          | PATCH  | Update own profile           |
| Rentals  | /rentals           | POST   | Create rental                |
| Rentals  | /rentals           | GET    | View own rentals             |
| Rentals  | /rentals/{id}      | GET    | View rental details (own)    |
| Rentals  | /rentals/{id}/return | POST   | Return rental                |
| Payments | /payments          | POST   | Create payment session       |
| Payments | /payments          | GET    | View own payments            |
| Payments | /payments/{id}/renew | POST   | Renew payment session        |

---

### 🛠️ MANAGER Permissions

Managers have full access to all USER operations plus administrative capabilities:

| Resource  | Endpoint                  | Method | Description                  |
|----------|---------------------------|--------|------------------------------|
| Cars     | /cars                     | POST   | Create car                   |
| Cars     | /cars/{id}                | PATCH  | Update car                   |
| Cars     | /cars/{id}                | DELETE | Soft delete car              |
| Users    | /users/{id}/role          | PUT    | Update user role             |
| Rentals  | /rentals                  | GET    | View all rentals             |
| Payments | /payments                 | GET    | View payments (any user)     |

---

### 🔒 Notes

- All protected endpoints require a valid JWT token.
- Access is enforced via Spring Security with role-based authorization.
- Users can only access their own resources unless they have MANAGER role.
---
## 📄 API Documentation

- Swagger UI:
  http://localhost:8080/swagger-ui/index.html
- Postman collection available in the repository
---
## 🧪 Testing

#### Testing: The project includes Unit tests (JUnit 5 + Mockito), Repository tests (@DataJpaTest), and Service layer tests. Run them using:

```bash
mvn clean test
```
## 📬 Postman Collection

You can test API endpoints using Postman.

1. Import collection from:
   `postman/Car_Sharing_API.postman_collection.json`

2. Run login request to obtain JWT token

3. Token will be automatically saved and used for authorized requests

## 🗄️ Database
- MySQL is the primary database. 
- Database schema and migrations are managed automatically via Liquibase changeSets on startup. 
- Soft-deletion logic is implemented to maintain historical integrity.

## 🧩 Database Diagram

![Database Diagram](docs/Car-Sharing-Service-DB.png)

## ⚡ Challenges & Learnings
- Designing a clean, layered architecture (Controller → Service → Repository).
- Implementing secure, stateless JWT authentication and role-based authorization. 
- Managing complex entity relationships and enforcing constraints. 
- Integrating a real-world payment gateway (Stripe). 
- Building a reliable, event-driven Telegram notification system using standard REST templates. 
- Writing robust, isolated tests for components handling external APIs and static methods.
