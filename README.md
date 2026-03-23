# Nulldown - Uptime Monitoring Service

A comprehensive uptime monitoring service built with Spring Boot that tracks website and application availability with real-time alerts and detailed statistics.

## 📋 Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [How It Works](#how-it-works)
- [Database Schema](#database-schema)
- [Docker Support](#docker-support)
- [Troubleshooting](#troubleshooting)

## ✨ Features

- **User Authentication**: Secure JWT-based authentication with registration and login
- **Monitor Management**: Create and manage multiple URL monitors
- **Automated Ping Monitoring**: Scheduled background tasks check service availability every 60 seconds
- **Uptime Statistics**: Track ping history and generate comprehensive uptime reports
- **Alert Notifications**: Email notifications for service status changes (up/down events)
- **Smart Deduplication**: Redis-based alert deduplication prevents email spam
- **User Profiles**: Manage user account information
- **Response Time Tracking**: Monitor latency metrics for each ping
- **Event-Driven Architecture**: Clean separation of concerns using Spring Application Events

## 🚀 Tech Stack

- **Framework**: Spring Boot 4.0.3
- **Language**: Java 25
- **Database**: PostgreSQL
- **Cache**: Redis (for deduplication and performance)
- **Authentication**: JWT (JSON Web Tokens) with JJWT 0.12.6
- **Build Tool**: Maven
- **ORM**: Spring Data JPA with Hibernate
- **Email**: Spring Mail (Gmail SMTP)
- **HTTP Client**: Spring RestClient

## 🏗️ Architecture

Let me walk you through it conversationally, like a story.

### How Nulldown Works Internally

Think of it like a **watchman** sitting in a room with a list of websites. Every 60 seconds he wakes up, visits every website on his list, writes down whether it was up or down, and if something is down — he sends you an alert.

That's the whole app. Now let's go layer by layer.

#### 1. You add a website (the CRUD part)

You call `POST /api/monitor/create` with a URL like `https://google.com`.

Spring saves it to the `Monitor` table in PostgreSQL. That's it. Nothing happens yet — it's just a row in the database.

#### 2. The scheduler wakes up every 60 seconds

There's a background job running on a timer. Every 60 seconds (`MonitorPinger`) it runs this logic:

- Go to the `Monitor` table
- Fetch every row where `active = true`
- For each URL, make an HTTP request to it
- Measure how long it took (response time in milliseconds)
- Check the status code — if it's 5xx or the connection failed, that's DOWN

This is the **heart of the app**. Everything else is built around this one loop.

#### 3. The result gets saved (ping logs)

After each ping, it saves a row to the `PingLog` table:

```
monitor_id | status_code | response_time | is_success | timestamp
abc-123    | 200         | 234           | true       | 2025-03-14 10:00:00
abc-123    | 503         | -1            | false      | 2025-03-14 10:01:00
```

This is what powers the dashboard later — all your uptime history is just rows in this table.

#### 4. If something is DOWN — fire an event

The pinger doesn't send the email itself. It just announces: *"hey, monitor X just went down."*

It does this via a **Spring Application Event** (`MonitorDownEvent` / `MonitorUpEvent`) — basically an internal message inside the app. The `AlertService` is listening for that message.

Why separate it? Because the pinger's job is only to ping. Sending emails is someone else's responsibility. This is called **separation of concerns** and it's a real pattern seniors use.

#### 5. The AlertService handles the alert — but checks Redis first

Before sending the email, it checks Redis:

- Is there already a key `alert:down:abc-123` in Redis?
- If YES → someone already sent an alert for this. Do nothing.
- If NO → set that key with a 10-minute expiry, then send the email.

Why Redis? Because the pinger runs every 60 seconds. Without this check, if your site is down for 2 hours, you'd get 120 emails. Redis prevents that — one alert per incident.

#### 6. Site comes back UP — recovery alert

When the pinger sees a URL returning 200 again, it checks: does the Redis key `alert:down:abc-123` still exist? If yes, that means the site was previously reported as down. So it:

- Deletes the Redis key (incident is over)
- Fires a recovery event
- You get a "your site is back up" email

#### 7. Dashboard stats

The stats endpoint just queries `PingLog` and does simple math:

- Total pings in a time range
- How many were successful (is_success = true)
- `(success count / total) * 100` = uptime percentage
- Average response time

No magic. Just a database query and arithmetic.

#### The full flow as one picture

```
User creates monitor (URL)
      ↓
Saved in Monitor table
      ↓
Scheduler (MonitorPinger) fires every 60s
      ↓
HTTP request sent to each URL
      ↓
Result saved to PingLog table
      ↓
      ├── if UP and was previously DOWN → recovery event, clear Redis
      └── if DOWN → check Redis
                      ├── already alerted? → do nothing
                      └── not alerted yet? → set Redis key, fire event
                      
Events caught by AlertService → Email sent
```

---

## 📁 Project Structure

```
src/main/java/com/project/nulldown/
├── controller/          # REST API endpoints
│   ├── AuthController   # /api/auth - Authentication
│   └── MonitorController # /api/monitor - Monitor management
├── dto/                 # Data Transfer Objects
│   ├── AuthResponse
│   ├── LoginRequest
│   ├── MonitorRequest
│   ├── MonitorResponse
│   ├── MonitorStats
│   ├── ProfileResponse
│   └── RegisterRequest
├── model/               # JPA Entity models
│   ├── User             # User accounts
│   ├── Monitor          # URL monitors
│   └── PingLog          # Ping history
├── repository/          # Data access layer (Spring Data JPA)
│   ├── UserRepository
│   ├── MonitorRepository
│   └── PingLogRepository
├── services/            # Business logic
│   ├── AuthService      # User registration & login
│   ├── MonitorService   # Monitor CRUD & stats
│   └── AlertService     # Alert/email handling
├── schedule/            # Scheduled tasks
│   └── MonitorPinger    # Periodic uptime checks (runs every 60s)
├── security/            # JWT and security
│   ├── JwtUtil          # JWT token generation/validation
│   ├── JwtFilter        # JWT authentication filter
│   └── SecurityConfig   # Spring Security configuration
├── event/               # Application events
│   ├── MonitorDownEvent # Fired when monitor goes down
│   └── MonitorUpEvent   # Fired when monitor comes back up
└── NulldownApplication  # Main Spring Boot entry point
```

## 📋 Prerequisites

- Java 25 or higher
- Maven 3.6+
- PostgreSQL 12+ (running on port 5433)
- Redis (optional but recommended)
- Gmail account with app-specific password (for email alerts)

## ⚙️ Configuration

### Database Setup

Create a PostgreSQL database and user:

```sql
CREATE DATABASE nulldown;
CREATE USER nulldown_user WITH PASSWORD 'nulldown_pass';
GRANT ALL PRIVILEGES ON DATABASE nulldown TO nulldown_user;
```

### Email Configuration

For Gmail:
1. Enable 2-Step Verification on your Google Account
2. Generate an App Password: https://myaccount.google.com/apppasswords
3. Use this in your configuration

### Application Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: nulldown
  
  datasource:
    url: jdbc:postgresql://localhost:5433/nulldown
    username: nulldown_user
    password: nulldown_pass
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: update  # Auto-create tables on startup
    show-sql: true
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  
  data:
    redis:
      host: localhost
      port: 6379
  
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password  # NOT your Google password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

app:
  jwt:
    secret: your-secret-key-at-least-32-characters-long
    expiration: 86400000  # 24 hours in milliseconds
```

## 🔧 Installation & Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd nulldown
   ```

2. **Build the project**
   ```bash
   mvn clean package
   ```

3. **Run with Docker Compose** (easiest - recommended)
   ```bash
   docker-compose up
   ```
   This will start PostgreSQL, Redis, and the Spring Boot application.

4. **Or run standalone**
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

## 📡 API Endpoints

### Authentication Endpoints

#### Register User
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

Response (201 Created):
```json
{
  "email": "user@example.com",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

Response (200 OK):
```json
{
  "email": "user@example.com",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

### Monitor Endpoints (requires JWT token in `Authorization: Bearer <token>` header)

#### Create Monitor
```http
POST /api/monitor/create
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "url": "https://example.com",
  "name": "Example Website"
}
```

Response (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "url": "https://example.com",
  "name": "Example Website",
  "isActive": true,
  "createdAt": "2025-03-23T10:30:00Z"
}
```

#### Get All Monitors
```http
GET /api/monitor
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Response (200 OK):
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "url": "https://example.com",
    "name": "Example Website",
    "isActive": true,
    "createdAt": "2025-03-23T10:30:00Z"
  }
]
```

#### Get Monitor Statistics
```http
GET /api/monitor/{id}/stats
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Response (200 OK):
```json
{
  "monitorId": "550e8400-e29b-41d4-a716-446655440000",
  "totalPings": 1440,
  "successfulPings": 1440,
  "uptimePercentage": 100.0,
  "averageResponseTime": 234,
  "lastPingAt": "2025-03-23T15:45:00Z"
}
```

#### Get User Profile
```http
GET /api/monitor/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Response (200 OK):
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "email": "user@example.com"
}
```

---

## 🗄️ Database Schema

### users
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### monitors
```sql
CREATE TABLE monitors (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    url VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### ping_logs
```sql
CREATE TABLE ping_logs (
    id UUID PRIMARY KEY,
    monitor_id UUID NOT NULL REFERENCES monitors(id),
    status_code INTEGER,
    response_time INTEGER,
    is_success BOOLEAN NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 🐳 Docker Support

### docker-compose.yml

The project includes a `docker-compose.yml` that starts:
- Spring Boot application (port 8080)
- PostgreSQL database (port 5433)
- Redis cache (port 6379)

```bash
# Start all services
docker-compose up

# Stop all services
docker-compose down

# View logs
docker-compose logs -f nulldown
```

## 🆘 Troubleshooting

### Database Connection Failed
- Ensure PostgreSQL is running and listening on port 5433
- Verify credentials in `application.yml`
- Check if the `nulldown` database exists: `psql -U postgres -c "LIST;"`

### Redis Connection Issues
- Ensure Redis is running on port 6379
- Test connectivity: `redis-cli ping`
- If offline, the app will still work but deduplication won't function

### JWT Token Expired
- Default expiration is 24 hours
- Users need to log in again after token expiration
- Check `app.jwt.expiration` in configuration

### Emails Not Sending
- Verify Gmail app-specific password is correct
- Ensure Gmail account has 2-Step Verification enabled
- Check email settings in `application.yml`
- Review application logs for SMTP errors

### Scheduler Not Running
- Ensure `@EnableScheduling` is present in `NulldownApplication.java`
- Check Spring logs for scheduled task registration
- Verify no scheduled task errors in logs

## 📊 Monitoring

Monitor key metrics:
- `/api/monitor` - Number of active monitors
- `/api/monitor/{id}/stats` - Uptime percentages
- Scheduled task execution (check logs for `MonitorPinger` execution)
- Redis key count for deduplication
- Database ping_logs table size

## 🔐 Security

- ✅ JWT-based authentication for all protected endpoints
- ✅ Password validation and secure storage via Spring Security
- ✅ SQL injection prevention via parameterized queries (JPA)
- ✅ CORS protection (configured in SecurityConfig)
- ✅ HTTPS recommended for production
- ⚠️ Ensure JWT secret is at least 32 characters and cryptographically strong

## 📝 Environment Variables

Optional environment overrides:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://your-host:5432/nulldown
SPRING_DATASOURCE_USERNAME=your_user
SPRING_DATASOURCE_PASSWORD=your_password
SPRING_REDIS_HOST=your-redis-host
SPRING_MAIL_USERNAME=your-email@gmail.com
SPRING_MAIL_PASSWORD=your-app-password
APP_JWT_SECRET=your-jwt-secret
```

## 🧪 Testing

Run all tests:
```bash
mvn test
```

Run specific test class:
```bash
mvn test -Dtest=NulldownApplicationTests
```

## 📄 License

This project is licensed under the MIT License.

## 🤝 Contributing

1. Create a feature branch (`git checkout -b feature/AmazingFeature`)
2. Commit changes (`git commit -m 'Add AmazingFeature'`)
3. Push to branch (`git push origin feature/AmazingFeature`)
4. Open a Pull Request

---

**That's the entire internal working.** The app is essentially **one loop + one event system + one deduplication check**. Everything else (JWT auth, REST endpoints, database) is just layers on top of this core.
