# HCBS Movie Booking System — Test Code Quick Reference Guide

> **Purpose**: This document helps examiners quickly locate all automated test files, understand their structure, and run them with a single command.

---

## 1. How to Run All Tests

```bash
# Run the full test suite (from project root):
mvn test

# Or using the Maven wrapper:
./mvnw.cmd test
```

**Expected output**: `BUILD SUCCESS` with all tests passed.

**Test environment**: Tests use an independent **in-memory H2 database** (`src/test/resources/application.properties`), completely isolated from the development database — no external setup required.

---

## 2. Run Specific Test Classes

```bash
# Run a single test class:
mvn test -Dtest=StandardPolicyTest

# Run multiple classes:
mvn test -Dtest=StandardPolicyTest,VIPPolicyTest,EmergencyPolicyTest

# Run a specific method:
mvn test -Dtest=LoginServiceTest#register_newUser_returnsTrue

# Run all tests in a package:
mvn test -Dtest="com.eduaccess.service.policy.*"
```

---

## 3. Test Directory Structure

```
src/test/java/com/eduaccess/
├── config/                          ← Seed data & startup tests
│   └── DataInitializerIT.java
├── domain/                          ← Domain model unit tests
│   └── BookingStatusTest.java
├── repository/                      ← Repository layer (slice tests, @DataJpaTest)
│   ├── BookingRepositoryTest.java
│   ├── BookingSeatRepositoryIT.java
│   ├── CancellationRepositoryTest.java
│   ├── FilmListingRepositoryIT.java
│   ├── ScreeningRepositoryTest.java
│   └── UserAccountRepositoryTest.java
└── service/                         ← Service layer (unit + integration)
    ├── AuditLogServiceIT.java
    ├── AuditLogServiceTest.java
    ├── BookingServiceIT.java
    ├── BookingServiceIntegrationTest.java
    ├── CancellationServiceIntegrationTest.java
    ├── CinemaServiceIT.java
    ├── FoodOrderServiceIT.java
    ├── FoodOrderServiceTest.java
    ├── LoginRegistrationIntegrationTest.java
    ├── LoginServiceTest.java
    ├── ManagerDashboardServiceTest.java
    ├── PricingServiceTest.java
    ├── RefundCalculatorTest.java
    ├── SchedulingServiceIT.java
    ├── ScreeningServiceTest.java
    ├── batch/
    │   └── BatchCancellationServiceIT.java
    ├── email/
    │   └── EmailReceiptServiceTest.java
    └── policy/
        ├── CancellationPolicyFactoryTest.java
        ├── EmergencyPolicyTest.java
        ├── RefundContextTest.java
        ├── SameDayScreeningPolicyTest.java
        ├── StandardPolicyTest.java
        └── VIPPolicyTest.java
```

---

## 4. Test Files by Category

### 4.1 Unit Tests (UT) — Pure logic, no Spring context, uses Mockito

| # | Test File | Location | What It Tests | Methods |
|---|-----------|----------|---------------|---------|
| 1 | `StandardPolicyTest` | `service/policy/` | Standard refund policy (50% ticket, 100% food) | `calculate_movieOnly_returnsHalfRefund`, `calculate_movieAndFood_returnsHalfTicketAndFullFood`, `getType_returnsStandard` |
| 2 | `VIPPolicyTest` | `service/policy/` | VIP refund policy (70% ticket, 100% food) | `calculate_movieOnly_returns70Percent`, `calculate_includesFood_returns100PercentFood` |
| 3 | `EmergencyPolicyTest` | `service/policy/` | Emergency policy (full refund) | `calculate_fullScope_returnsFullRefund`, `calculate_vipFullScope_issuesVoucher`, `calculate_partialScope_returnsHalfOnEveryLine` |
| 4 | `SameDayScreeningPolicyTest` | `service/policy/` | Same-day / past-date zero-refund override across all policies | 8 methods covering Standard/VIP/Emergency same-day scenarios |
| 5 | `CancellationPolicyFactoryTest` | `service/policy/` | Factory selects correct policy class | `policyFor_standard_returnsStandardPolicy` |
| 6 | `RefundContextTest` | `service/policy/` | Null-safe amount coercion in RefundContext | `nullAmounts_areCoercedToZero`, `vipPackageAmount_whenNotVip_isZero` |
| 7 | `BookingStatusTest` | `domain/` | State machine transitions (CONFIRMED→CANCELLED→…→REFUNDED) | `canTransitionTo_invalidPath_returnsFalse`, `transitionTo_invalidPath_throws`, `isCancellable_onlyConfirmed_returnsTrue`, `nextInFlow_followsLinearStateMachine` |
| 8 | `RefundCalculatorTest` | `service/` | Refund amount calculation with rules | `calculate_standardCancellation_returnsHalfRefund`, `calculate_sameDay_returnsZeroRefund`, `calculate_pastScreening_returnsZeroRefund`, `calculate_vipBooking_addsTwentyPercentBonus`, `calculate_weekendScreening_appliesPenalty` |
| 9 | `EmailReceiptServiceTest` | `service/email/` | Email log single-entry guarantee | `buildSingleEmail_writesOneLogEntry`, `buildSingleReceipt_replacesPreviousEntry` |
| 10 | `PricingServiceTest` | `service/` | Ticket pricing (3D/IMAX/hall/seat surcharges) | `priceFor_3DScreening_addsSurcharge`, `hallSurcharge_imaxAndPremiumAndRegular`, `seatSurcharge_byType`, `calculateTicketPrice_londonEvening3DPremium_summedCorrectly` |
| 11 | `LoginServiceTest` | `service/` | Authentication & registration logic | `register_newUser_returnsTrue`, `register_duplicateUsername_returnsFalse`, `register_duplicateEmail_returnsFalse`, `register_passwordIsHashed_notStoredPlain`, `login_validCredentials_returnsTrue`, `login_wrongPassword_returnsFalse`, `login_nonExistentUser_returnsFalse`, `hasRole_managerCanAccessAll`, `hasRole_adminCannotAccessManager`, `hasRole_bookingStaffOnlyOwnLevel`, `hasRole_nullRole_returnsFalse` |
| 12 | `ScreeningServiceTest` | `service/` | Screening queries & overlap detection | `existsOverlappingScreening_conflict_returnsTrue`, `existsOverlappingScreening_noConflict_returnsFalse`, `existsOverlappingScreening_excludesSelf_returnsFalse`, + 5 delegation tests |
| 13 | `ManagerDashboardServiceTest` | `service/` | Dashboard metrics & recommendations | `foodAttachRate_calculatesCorrectly`, `recommendations_highDemandFilm_suggestsExtraScreening`, `heatmap_groupsBySlotAndDay` |
| 14 | `FoodOrderServiceTest` | `service/` | Food order validation | `updateStatus_invalidTransition_rejected` |
| 15 | `AuditLogServiceTest` | `service/` | Audit log null handling | `recordAction_nullActor_usesSystemFallback` |

---

### 4.2 Integration Tests (IT) — Uses @SpringBootTest or @DataJpaTest with H2

| # | Test File | Location | What It Tests | Methods |
|---|-----------|----------|---------------|---------|
| 1 | `ScreeningRepositoryTest` | `repository/` | Screening date-range queries, overlap detection | `findByScreeningDateBetween_returnsScreeningsInRange`, `findEarliestUpcomingScreeningDateForFilm_returnsCorrectDate`, `findByScreenCinemaIdAndScreeningDateBetween_filtersByCinema`, `existsByFilmId_*`, `existsOverlappingScreening_*` |
| 2 | `BookingRepositoryTest` | `repository/` | Booking reference lookup, sold seat counting | `findByBookingReference_returnsBookingWithSeats`, `existsBookedSeat_detectsAlreadyBookedSeat`, `existsBookedSeat_returnsFalse_forCancelledBooking`, `countSoldSeatsForScreening_countsOnlyConfirmed`, `totalRevenueForScreening_sumsConfirmedOnly` |
| 3 | `BookingSeatRepositoryIT` | `repository/` | Booked seat queries per screening | `findBookedSeatIdsByScreeningId_returnsConfirmedOnly` |
| 4 | `CancellationRepositoryTest` | `repository/` | Cancellation record persistence & query | `findByBookingReference_returnsCorrectRecord`, `findByBookingReference_notFound_returnsEmpty`, `existsByBookingReference_*`, `findAllByOrderByCancelledAtDesc_*` |
| 5 | `UserAccountRepositoryTest` | `repository/` | User account CRUD & uniqueness | `findByUsername_existingUser_returnsAccount`, `findByUsername_nonExistent_returnsEmpty`, `existsByUsername_*`, `existsByEmail_*`, `findByEmail_*`, `save_newUser_persistsSuccessfully`, `register_sameUsernameSecondTime_violatesUnique` |
| 6 | `FilmListingRepositoryIT` | `repository/` | Film listing with city/genre/date filter | `filterByCityGenreDate_returnsMatchingScreenings` |
| 7 | `BookingServiceIntegrationTest` | `service/` | Full booking flow in Spring container (13 methods) | `createBooking_validInput_persistsBookingWithCorrectTotal`, `createBooking_pastScreening_throwsException`, `createBooking_alreadyBookedSeat_throwsException`, `createBooking_noSeats_throwsException`, `createBooking_invalidEmail_throwsException`, `findAvailableSeats_afterBooking_excludesBookedSeat`, `createBooking_nullSeatList_throwsException`, `createBooking_tooFarInAdvance_throwsException`, `createBooking_nullCustomerName_throwsException`, `createBooking_blankCustomerName_throwsException`, `createBooking_nullEmail_throwsException`, `createBooking_nonExistentScreening_throwsException` |
| 8 | `BookingServiceIT` | `service/` | Booking + food order integration | `createBooking_withFood_createsFoodOrder` |
| 9 | `CancellationServiceIntegrationTest` | `service/` | Full cancellation & refund flow (15 methods) | `cancelBooking_confirmedBooking_transitionsToCancelled`, `cancelBooking_alreadyCancelled_throwsException`, `advanceStatus_fullFlow_CONFIRMED_to_REFUNDED`, `advanceStatus_invalidTransition_throws`, `updateCancellationReason_afterCancellation_persistsReason`, `submitPolicyRefund_standardPartial_advancesToRefundPending`, `findBookingByReference_caseInsensitive_findsBooking`, `calculateRefund_returnsNonNullSummary`, `cancelBooking_unknownReference_throwsException`, `cancelBooking_nullReference_throwsException`, `cancelBooking_blankReference_throwsException`, `submitPolicyRefund_beforeCancellation_doesNotAdvanceToRefundPending`, `advanceStatus_unknownReference_throwsException`, `findBookingByReference_nullOrBlank_returnsEmpty` |
| 10 | `FoodOrderServiceIT` | `service/` | Food order lifecycle & cascade | `cancelPendingFoodOrdersForBooking_updatesStatus`, `cancelPendingFoodOrdersForBooking_leavesDeliveredUntouched`, `createFoodOrder_emptyItems_throwsException`, `createFoodOrder_unknownBooking_throwsException`, `findOrdersForBooking_returnsOrdersInDatabase` |
| 11 | `LoginRegistrationIntegrationTest` | `service/` | End-to-end registration with BCrypt | `register_fullFlow_persistsUserWithHashedPassword`, `register_duplicateUsername_rejectsSecondAttempt`, `register_duplicateEmail_rejectsSecondAttempt`, `register_managerRole_persistsCorrectly`, `register_adminRole_persistsCorrectly`, `register_thenVerifyPassword_matchesWithEncoder` |
| 12 | `DataInitializerIT` | `config/` | Seed data idempotency on restart | `seedData_doesNotDuplicateOnRestart` |
| 13 | `AuditLogServiceIT` | `service/` | Audit trail persistence | `bookingAction_persistsAuditEntry`, `scheduleAction_persistsActorAndEntity` |
| 14 | `CinemaServiceIT` | `service/` | Cinema + screen + seat creation | `createCinema_createsScreensAndSeats` |
| 15 | `SchedulingServiceIT` | `service/` | Screening overlap business rule | `createScreening_overlapRejected` |
| 16 | `BatchCancellationServiceIT` | `service/batch/` | Batch cancellation processing | `executeBatch_createsRecordsAndEmails` |

---

## 5. Test Coverage by Core Function

| Core Function | Unit Tests | Integration Tests |
|---|---|---|
| **Booking Flow** (seat selection, order placement) | `PricingServiceTest`, `BookingStatusTest` | `BookingServiceIntegrationTest` (13 methods), `BookingServiceIT`, `BookingRepositoryTest`, `BookingSeatRepositoryIT` |
| **Cancellation & Refund** (cancel, refund policy, history) | `StandardPolicyTest`, `VIPPolicyTest`, `EmergencyPolicyTest`, `SameDayScreeningPolicyTest`, `RefundCalculatorTest`, `EmailReceiptServiceTest` | `CancellationServiceIntegrationTest` (15 methods), `CancellationRepositoryTest`, `FoodOrderServiceIT` |
| **Manager Dashboard & Scheduling** | `ManagerDashboardServiceTest`, `ScreeningServiceTest` | `SchedulingServiceIT`, `AuditLogServiceIT`, `CinemaServiceIT`, `BatchCancellationServiceIT` |
| **Authentication & Registration** | `LoginServiceTest` (11 methods) | `LoginRegistrationIntegrationTest` (6 methods), `UserAccountRepositoryTest` (7 methods) |

---

## 6. Test Configuration

| File | Purpose |
|------|---------|
| `src/test/resources/application.properties` | Test profile: in-memory H2 (`jdbc:h2:mem:hcbs-test`), `ddl-auto=create-drop`, Vaadin disabled |
| `pom.xml` → `spring-boot-starter-test` | Provides JUnit 5, Mockito, AssertJ, Spring Test |

---

## 7. Quick Verification Checklist for Examiner

1. **Run all tests**:
   ```bash
   mvn test
   ```
   → Look for `BUILD SUCCESS` and `Tests run: XX, Failures: 0, Errors: 0`

2. **Check total test count**: 31 test files, 100+ test methods across unit and integration layers.

3. **Key files to inspect**:
   - Refund policy logic → `service/policy/StandardPolicyTest.java` (lines 21–75)
   - State machine → `domain/BookingStatusTest.java` (lines 17–69)
   - Full booking flow → `service/BookingServiceIntegrationTest.java` (lines 80–272)
   - Full cancellation flow → `service/CancellationServiceIntegrationTest.java` (lines 87–258)
   - Authentication → `service/LoginServiceTest.java` (lines 60–215)

4. **Boundary / exception tests** (deliberately incorrect data):
   - Past screening → `BookingServiceIntegrationTest#createBooking_pastScreening_throwsException`
   - Null seat list → `BookingServiceIntegrationTest#createBooking_nullSeatList_throwsException`
   - Duplicate seat → `BookingServiceIntegrationTest#createBooking_alreadyBookedSeat_throwsException`
   - Unknown reference → `CancellationServiceIntegrationTest#cancelBooking_unknownReference_throwsException`
   - Invalid state transition → `CancellationServiceIntegrationTest#advanceStatus_invalidTransition_throws`
   - Empty food items → `FoodOrderServiceIT#createFoodOrder_emptyItems_throwsException`
   - Same-day zero refund → `RefundCalculatorTest#calculate_sameDay_returnsZeroRefund`

---

## 8. Test Naming Convention

All test methods follow the pattern:

```
methodUnderTest_condition_expectedBehavior
```

Examples:
- `createBooking_pastScreening_throwsException`
- `register_duplicateUsername_returnsFalse`
- `existsOverlappingScreening_conflict_returnsTrue`

This makes it easy to understand what each test verifies from its name alone.

---

## 9. Technologies Used in Tests

| Technology | Purpose |
|---|---|
| **JUnit 5** | Test framework |
| **Mockito** | Mocking for unit tests |
| **AssertJ** | Fluent assertions |
| **@DataJpaTest** | Repository slice tests (auto-configured H2) |
| **@SpringBootTest** | Full Spring context integration tests |
| **@Transactional** | Test isolation (auto-rollback after each test) |
| **H2 in-memory** | Test database (create-drop per run) |
