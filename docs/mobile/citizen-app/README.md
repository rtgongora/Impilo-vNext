# Citizen App

## Overview

The Citizen App is a patient-facing mobile application that gives citizens direct access to their health data, appointments, prescriptions, lab results, insurance coverage, and care team communications. It supports five integrated domains — **Personal**, **Social**, **Marketplace**, **Messaging**, and **Telehealth** — providing a comprehensive patient engagement layer within the Impilo platform.

**Entry point:** `apps/mobile/citizen-app/src/App.tsx`

## Architecture

The Citizen App shares the same foundational architecture as the Provider App. It is built with React and Zustand for state management, consuming five shared packages:

| Package | Responsibility |
|---|---|
| `@impilo/mobile-auth` | Keycloak PKCE authentication flow, token lifecycle |
| `@impilo/mobile-api-client` | Typed HTTP client for the Experience BFF |
| `@impilo/mobile-messaging` | In-app messaging, notifications, and real-time channels |
| `@impilo/mobile-design-system` | Shared UI primitives, theme tokens, accessibility helpers |
| `@impilo/mobile-timeline` | Patient timeline rendering |

## Trust Model

Every HTTP request carries the v1.1 trust headers via the API client. Authentication is handled via Keycloak using Authorization Code flow with PKCE. The citizen identity is resolved server-side from the `X-Actor-ID` header, which maps to a CPID in the patient registry.

Unlike the Provider App, the Citizen App does **not** require facility selection. The auth guard bootstraps the citizen profile automatically upon authentication.

## Domains

### Personal

The core health management domain. Provides access to:

- **Profile** — view and edit personal information (phone, email, language, avatar)
- **Appointments** — view, request, and cancel facility appointments
- **Prescriptions** — view active prescriptions and request refills
- **Lab Results** — view lab test results with expandable value details
- **Coverage** — view insurance and coverage plan information
- **Settings** — consent management, notification preferences, account deletion

### Social

Community health content and engagement:

- **Feed** — category-filtered health content, campaigns, and announcements
- **Likes** — like/unlike feed items
- **Content Types** — HEALTH_TIP, CAMPAIGN, ANNOUNCEMENT, COMMUNITY

### Marketplace

Health service discovery and booking:

- **Service Catalog** — browse available services by category with search
- **Service Requests** — book services, track request status
- **Order Tracking** — view tracking numbers, cancellation
- **Pricing** — transparent service pricing and facility information

### Messaging

Secure provider-to-citizen communication:

- **Conversations** — list, create, and manage conversations
- **Messages** — send and receive messages within conversation threads
- **Read Receipts** — mark conversations as read
- **Conversation Types** — DIRECT (provider-citizen), SUPPORT (help desk)

### Telehealth

Virtual consultation lifecycle:

- **Session List** — view scheduled, in-progress, and completed sessions
- **Request Teleconsult** — request video or audio consultations
- **Join Session** — join a session with real-time token and channel
- **End Session** — end an active session with optional notes

## Navigation

The app uses a bottom tab navigation with five tabs:

| Tab | Icon | Domain | Screen |
|---|---|---|---|
| Home | home | Dashboard | `HomeScreen` |
| Health | heart | Personal | `PersonalScreen` (tabbed hub) |
| Feed | newspaper | Social | `SocialFeedScreen` |
| Services | grid | Marketplace | `MarketplaceScreen` |
| Messages | message-circle | Messaging | `MessagingInboxScreen` |

Telehealth and support are accessible from quick actions on the Home screen.

## Backend Integration

All BFF routes are prefixed with `/internal/v1/mobile/citizen/` and enforce v1.1 header contract compliance. Each domain maps to a dedicated citizen BFF controller:

| Domain | BFF Controller | Base Route |
|---|---|---|
| Personal | `CitizenProfileController` | `/internal/v1/mobile/citizen/profile` |
| Appointments | `CitizenAppointmentController` | `/internal/v1/mobile/citizen/appointments` |
| Prescriptions | `CitizenPrescriptionController` | `/internal/v1/mobile/citizen/prescriptions` |
| Lab Results | `CitizenResultsController` | `/internal/v1/mobile/citizen/results` |
| Coverage | `CitizenCoverageController` | `/internal/v1/mobile/citizen/coverage` |
| Feed | `CitizenFeedController` | `/internal/v1/mobile/citizen/feed` |
| Marketplace | `CitizenMarketplaceController` | `/internal/v1/mobile/citizen/marketplace` |
| Messaging | `CitizenMessagingController` | `/internal/v1/mobile/citizen/messaging` |
| Telehealth | `CitizenTelehealthController` | `/internal/v1/mobile/citizen/telehealth` |
| Support | `CitizenSupportController` | `/internal/v1/mobile/citizen/support` |

## Database Schema

Citizen-specific tables are created in Flyway migration `V5__citizen_app_tables.sql`:

- `appointments`, `lab_results`, `coverage_plans`
- `feed_items`, `feed_likes`
- `marketplace_services`, `service_requests`
- `citizen_telehealth_sessions`
- `consent_preferences`, `citizen_support_tickets`

All tables enforce tenant isolation via `tenant_id` column.
