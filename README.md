# Horizon Cinemas Booking System

Horizon Cinemas Booking System is a Spring Boot and Vaadin web application for managing cinema films, screenings, bookings, food orders, cancellations and management analytics. The system was developed for an Object Oriented Development coursework project and demonstrates a complete cinema workflow from film browsing to booking confirmation and manager reporting.

## Main Features

- Film browsing page with poster display, film metadata, search/filter controls, showtime presentation and an automatic promotional carousel.
- Complete booking workflow with showtime selection, interactive seat map, seat availability checks, customer details, dynamic pricing, optional food ordering, payment simulation and receipt generation.
- Admin scheduling module with weekly timetable layout, drag-and-drop scheduling, automatic weekly auto-fill, draft changes, screening types and conflict validation.
- Cancellation and refund workflow with cancellation states, refund calculation, batch cancellation support, email preview/receipt generation and refund history.
- Food order workflow with snack/drink/combo selection during booking, staff order queue, status transitions and operational KPIs.
- Manager dashboard with revenue KPIs, ticket and food revenue, cancellation rate, food attach rate, revenue trend, film ranking, cinema performance, showtime heatmap, feedback and CSV export.
- Manager cinema and film management modules for adding cinemas, screens, seats and film records.
- Audit log module for tracking operational actions such as booking creation, schedule changes, food order updates and refund/cancellation operations.
- Role-based navigation for Booking Staff, Admin and Manager users.
- Dark black-gold cinema UI theme with custom Vaadin styling.

## Technology Stack

- Java 21
- Spring Boot 3.2.5
- Vaadin 24.3
- Spring Data JPA 
- H2 database
- Spring Security Core for BCrypt password encoding
- Maven
- JUnit 5 / Spring Boot Test

## Project Structure

```text
src/main/java/com/eduaccess
├── config          # Application startup data, web config and upload/poster handling
├── domain          # JPA entities and enums
├── exception       # Custom exception classes
├── repository      # Spring Data JPA repositories and queries
├── service         # Business logic and transactional workflows
│   ├── batch       # Batch cancellation support
│   ├── compensation
│   ├── email
│   └── policy      # Refund and cancellation policies
└── ui              # Vaadin views and layout components

src/main/resources
├── application.properties
└── data.sql

frontend/styles
└── hcbs-theme.css  # Main UI theme
```

## Key Pages and Routes

| Page | Route | Main Purpose |
| --- | --- | --- |
| Film Listing | `/` | Browse films, filter/search, view promotional carousel and enter booking flow |
| Booking | `/booking` or `/booking/{filmId}` | Select showtime, seats, food, customer details and confirm booking |
| Food Orders | `/staff/food-orders` | Staff operations queue for food orders |
| Admin Scheduler | `/admin/schedule` | Visual weekly timetable, drag-and-drop and auto-fill scheduling |
| Admin Screenings | `/admin/screenings` | Screening data management |
| Cancellation | `/cancellation` | Single booking cancellation workflow |
| Batch Cancellation | `/cancellation-batch` | Emergency batch cancellation/refund workflow |
| Cancellation Statuses | `/cancellation-statuses` | Track cancellation and refund state changes |
| Email Management | `/email-management` | Preview cancellation emails and refund receipts |
| Audit Logs | `/audit-logs` | Operational audit trail |
| Manager Dashboard | `/manager/dashboard` | KPIs, charts, heatmap, analytics and export |
| Manager Cinemas | `/manager/cinemas` | Cinema, screen and seat management |
| Manager Films | `/manager/films` | Film management and poster upload |
| Login | `/login` | User authentication |
| Register | `/register` | User registration |

## Demo Accounts

The application creates these demo accounts automatically on startup if they do not already exist:

| Role | Username | Password |
| --- | --- | --- |
| Manager | `manager` | `manager123` |
| Admin | `admin` | `admin123` |
| Booking Staff | `staff` | `staff123` |

Role permissions are hierarchical:

- Manager can access manager, admin, cancellation and booking-level functions.
- Admin can access admin, cancellation and booking-level functions.
- Booking Staff can access booking and food-order functions.

## How to Run

### Prerequisites

- Java 21
- Maven 3.9+ recommended

### Start the Application

From the project root:

```bash
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

Vaadin browser launch is enabled in `src/main/resources/application.properties`, so the browser may open automatically during development.

## Database

The project uses an H2 file database:

```properties
spring.datasource.url=jdbc:h2:file:./data/hcbs-db
```

The H2 console is enabled at:

```text
http://localhost:8080/h2-console
```

Use these connection values:

```text
JDBC URL: jdbc:h2:file:./data/hcbs-db
Username: sa
Password: leave blank
```

Schema updates are handled by Hibernate:

```properties
spring.jpa.hibernate.ddl-auto=update
```

Initial demo data is maintained by `DataInitializer`, including cinemas, screens, seats, films, screenings, food items and demo user accounts.

## How to Test

Run all tests:

```bash
mvn test
```

The test profile uses an in-memory H2 database:

```properties
spring.datasource.url=jdbc:h2:mem:hcbs-test
spring.jpa.hibernate.ddl-auto=create-drop
```

The test suite covers repository queries, booking creation, seat availability, cancellation policies, refund calculation, scheduling validation, dashboard aggregation, login/registration, food ordering and audit logging.

## Core Implementation Files

| Feature | Main Files |
| --- | --- |
| Film listing and carousel | `src/main/java/com/eduaccess/ui/FilmListingView.java` |
| Booking UI workflow | `src/main/java/com/eduaccess/ui/BookingView.java` |
| Booking persistence and validation | `src/main/java/com/eduaccess/service/BookingService.java` |
| Seat availability queries | `src/main/java/com/eduaccess/repository/BookingRepository.java`, `src/main/java/com/eduaccess/repository/BookingSeatRepository.java` |
| Pricing | `src/main/java/com/eduaccess/service/PricingService.java` |
| Admin scheduling UI | `src/main/java/com/eduaccess/ui/AdminScheduleView.java` |
| Schedule validation and persistence | `src/main/java/com/eduaccess/service/SchedulingService.java` |
| Cancellation workflow | `src/main/java/com/eduaccess/service/CancellationService.java`, `src/main/java/com/eduaccess/ui/CancellationStatusesView.java` |
| Batch cancellation | `src/main/java/com/eduaccess/service/batch/BatchCancellationService.java`, `src/main/java/com/eduaccess/ui/BatchCancellationDashboardView.java` |
| Refund policies | `src/main/java/com/eduaccess/service/policy` |
| Email receipts | `src/main/java/com/eduaccess/service/email` |
| Food orders | `src/main/java/com/eduaccess/service/FoodOrderService.java`, `src/main/java/com/eduaccess/ui/FoodOrdersView.java` |
| Manager dashboard | `src/main/java/com/eduaccess/service/ManagerDashboardService.java`, `src/main/java/com/eduaccess/ui/ManagerDashboardView.java` |
| Cinema management | `src/main/java/com/eduaccess/service/CinemaService.java`, `src/main/java/com/eduaccess/ui/ManagerCinemaView.java` |
| Film management | `src/main/java/com/eduaccess/service/FilmService.java`, `src/main/java/com/eduaccess/ui/ManagerListingsView.java` |
| Login and roles | `src/main/java/com/eduaccess/service/LoginService.java`, `src/main/java/com/eduaccess/ui/PermissionChecker.java` |
| Audit logging | `src/main/java/com/eduaccess/service/AuditLogService.java`, `src/main/java/com/eduaccess/ui/AuditLogView.java` |
| Global theme | `frontend/styles/hcbs-theme.css` |

## Important Workflows

### Booking Flow

1. The user browses films and selects a film/showtime from the film listing or booking page.
2. `BookingView` renders a step-based workflow: seats, tickets, food, summary and payment.
3. The selected seats are checked visually in the UI and again in `BookingService`.
4. `BookingService.createBooking()` validates the screening date, selected seats, customer details and duplicate seat booking.
5. The service creates a `Booking`, creates one `BookingSeat` per selected seat, calculates ticket prices, saves the booking and records an audit log.
6. Optional food items are saved through `FoodOrderService.createFoodOrder()`.
7. A receipt is generated and the seat map is refreshed.

### Admin Scheduling Flow

1. `AdminScheduleView.renderTimetable()` builds a weekly timetable grid.
2. Admin users can drag film cards into timetable cells manually.
3. The Auto-fill week feature generates draft schedule items for valid future slots.
4. Draft items can be moved, deleted or confirmed.
5. Confirmed changes call `SchedulingService`, which validates fields, dates, daily limits and time overlap.
6. Valid screenings are saved and become available in the Film Listing and Booking pages.

### Manager Dashboard Flow

1. `ManagerDashboardView` accepts a date range and calls `ManagerDashboardService.buildDashboard()`.
2. The service loads bookings and food orders from repositories.
3. Data is filtered by date and status.
4. KPIs and chart rows are calculated, including revenue, ticket volume, cancellation rate, food attach rate, film sales, cinema revenue and heatmap data.
5. The view renders KPI cards, charts, management insights and export controls.

## Useful Code Screenshot Ranges

For presentation or report evidence, these are compact high-value code areas:

- Automatic promotional carousel: `FilmListingView.java` lines `209-244`
- Booking creation core: `BookingService.java` lines `81-117`
- Booking page submit action: `BookingView.java` lines `2381-2416`
- Automatic weekly scheduling: `AdminScheduleView.java` lines `1045-1083`
- Schedule conflict validation: `SchedulingService.java` lines `235-240` and `315-330`
- Manager dashboard statistics: `ManagerDashboardService.java` lines `69-127`
- Food order creation: `FoodOrderService.java` lines `82-117`

## Notes

- Uploaded film poster files are served from `/uploads/**`.
- The application excludes `/h2-console/**` and `/uploads/**` from Vaadin servlet handling so that H2 and uploaded files work correctly.
- The application uses `ddl-auto=update` for development convenience. For production deployment, use a controlled migration tool such as Flyway or Liquibase.
- Payment is simulated in the booking workflow.
