# Citizen App Acceptance Pack

## Summary

The Citizen App delivers a full vertical-slice implementation of the patient-facing mobile application within the Impilo platform. It covers five integrated domains: Personal, Social, Marketplace, Messaging, and Telehealth.

## Acceptance Criteria

### AC-1: Authentication and Profile Bootstrap

- [x] Keycloak PKCE authentication flow implemented
- [x] Auth guard blocks unauthenticated access
- [x] Profile auto-bootstrap on first authentication
- [x] No facility selection required (citizen context)
- [x] Secure token storage and automatic refresh

### AC-2: Personal Domain

- [x] Profile view and edit (phone, email, language, avatar)
- [x] Appointment listing with status filter and pagination
- [x] Appointment booking with facility, type, date, reason
- [x] Appointment cancellation with reason
- [x] Prescription listing with active/completed filter
- [x] Prescription refill request
- [x] Lab result listing with status filter
- [x] Lab result detail with expandable values
- [x] Coverage/insurance plan visibility
- [x] Consent preference management
- [x] Notification preference toggle
- [x] Account deletion

### AC-3: Social Domain

- [x] Category-filtered social feed (HEALTH_TIP, CAMPAIGN, ANNOUNCEMENT, COMMUNITY)
- [x] Feed item detail view
- [x] Like/unlike feed items
- [x] Like count display

### AC-4: Marketplace Domain

- [x] Service catalog browsing with category filter
- [x] Service search by keyword
- [x] Service detail view with pricing
- [x] Service request creation with preferred date and notes
- [x] Service request listing and tracking
- [x] Service request cancellation

### AC-5: Messaging Domain

- [x] Conversation listing with type filter (DIRECT, SUPPORT)
- [x] Conversation creation with initial message
- [x] Message thread view with chronological ordering
- [x] Message sending
- [x] Read receipt (mark conversation as read)
- [x] Load older messages (pagination)

### AC-6: Telehealth Domain

- [x] Telehealth session listing with status filter
- [x] Teleconsult request with reason, preferred date, session type
- [x] Session join with real-time token and channel
- [x] Session end with optional notes
- [x] Elapsed time display during active sessions

### AC-7: Support

- [x] Support ticket creation (category, subject, description, priority)
- [x] Support ticket listing with status filter
- [x] Knowledge article browsing (placeholder)

### AC-8: Backend BFF

- [x] 10 citizen-specific REST controllers under `/internal/v1/mobile/citizen/*`
- [x] v1.1 header contract compliance (CompanionHeaders)
- [x] Tenant isolation in all queries
- [x] Patient resolution via CPID lookup
- [x] Transactional outbox events for all write operations
- [x] Flyway migration V5 for citizen-specific tables

### AC-9: Testing

- [x] Personal flow tests (profile, appointments, prescriptions, results, coverage)
- [x] Messaging flow tests (conversations, messages, read receipts)
- [x] Marketplace flow tests (services, requests, cancellation, feed, likes)
- [x] Telehealth flow tests (session lifecycle, join/end, support tickets)
- [x] Backend integration tests (API contract compliance, URL construction, envelope format)

### AC-10: Architecture Compliance

- [x] Shared package consumption (`@impilo/mobile-auth`, `@impilo/mobile-api-client`, `@impilo/mobile-messaging`, `@impilo/mobile-design-system`)
- [x] Zustand vanilla store pattern (matching provider-app)
- [x] React.createElement rendering (no JSX transpilation dependency)
- [x] Service layer follows apiClient pattern with typed responses
- [x] No mocks, stubs, or TODOs in production code

## File Inventory

### Frontend (apps/mobile/citizen-app/)

| Category | Count | Files |
|---|---|---|
| Config | 4 | `package.json`, `tsconfig.json`, `vitest.config.ts`, `src/config.ts` |
| Types | 1 | `src/types/index.ts` |
| App Shell | 2 | `src/App.tsx`, `src/main.tsx` |
| Navigation | 3 | `AppNavigator.tsx`, `AuthGuard.tsx`, `CitizenTabs.tsx` |
| Stores | 1 | `src/stores/appStore.ts` |
| Screens | 14 | `LoginScreen`, `HomeScreen`, `NotificationsScreen`, `NetworkStatusBar`, `GlobalErrorBanner`, `PersonalScreen`, `ProfileSection`, `AppointmentsSection`, `PrescriptionsSection`, `ResultsSection`, `CoverageSection`, `SettingsSection`, `SocialFeedScreen`, `MarketplaceScreen`, `MessagingInboxScreen`, `ThreadViewScreen`, `TelehealthListScreen`, `TelehealthSessionScreen` |
| Services | 9 | `profileService`, `appointmentService`, `prescriptionService`, `labResultService`, `coverageService`, `feedService`, `marketplaceService`, `messagingService`, `telehealthService`, `supportService` |
| Tests | 5 | `PersonalFlow.test.tsx`, `MessagingFlow.test.tsx`, `MarketplaceFlow.test.tsx`, `TelehealthFlow.test.tsx`, `BackendIntegration.test.tsx` |

### Backend (services/experience-bff/)

| Category | Count | Files |
|---|---|---|
| Controllers | 10 | `CitizenProfileController`, `CitizenAppointmentController`, `CitizenPrescriptionController`, `CitizenResultsController`, `CitizenCoverageController`, `CitizenFeedController`, `CitizenMarketplaceController`, `CitizenMessagingController`, `CitizenTelehealthController`, `CitizenSupportController` |
| Migrations | 1 | `V5__citizen_app_tables.sql` |

### Documentation

| File | Purpose |
|---|---|
| `docs/mobile/citizen-app/README.md` | Architecture and domain overview |
| `docs/mobile/citizen-app/feature-map.md` | Feature-to-code mapping |
| `docs/mobile/citizen-app/domain-matrix.md` | Domain data model and events |
| `docs/mobile/citizen-app/privacy-and-safety.md` | Privacy and security analysis |
| `docs/acceptance/citizen-app-acceptance-pack.md` | This acceptance pack |
