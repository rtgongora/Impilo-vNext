# Provider App

## Overview

The Provider App is a multi-mode mobile application designed for healthcare workers operating within the Impilo platform. It supports four distinct operational modes — **Provider**, **Outreach**, **Supervisor**, and **Offline Edge** — each tailored to a specific workflow within primary healthcare delivery. The app is built to function reliably in low-connectivity environments, with offline-first capabilities powered by CRDT-based synchronization.

**Entry point:** `apps/mobile/provider-app/src/App.tsx`

## Architecture

The Provider App is built with React and Zustand for state management. It consumes seven shared packages that encapsulate cross-cutting concerns:

| Package | Responsibility |
|---|---|
| `@impilo/mobile-trust` | Injects the 14 trust headers into every outbound request |
| `@impilo/mobile-auth` | Keycloak PKCE authentication flow, token lifecycle |
| `@impilo/mobile-api-client` | Typed HTTP client for the Experience BFF |
| `@impilo/mobile-messaging` | In-app messaging, notifications, and real-time channels |
| `@impilo/mobile-timeline` | Patient and encounter timeline rendering |
| `@impilo/mobile-offline` | CRDT-based sync queue, conflict resolution, edge snapshots |
| `@impilo/mobile-design-system` | Shared UI primitives, theme tokens, accessibility helpers |

## Trust Model

Every HTTP request issued by the Provider App carries the 14 trust headers defined in the platform header contract. The `@impilo/mobile-trust` package is responsible for constructing and attaching these headers before each request leaves the device.

Authentication is handled via Keycloak using the Authorization Code flow with PKCE. Access tokens are stored securely on-device and refreshed automatically. The trust header set includes the authenticated identity, facility context, role assertions, and correlation identifiers required by the TSHEPO authorization service.

## Modes

### Provider Mode

The primary clinical mode for facility-based healthcare workers. Provides access to:

- **Worklist** — prioritized patient queue for the current session
- **Encounters** — structured clinical consultations
- **Vitals** — capture and review of vital signs
- **Diagnosis (Dx)** — ICD-coded diagnosis entry
- **Prescriptions (Rx)** — medication ordering
- **Labs** — laboratory test requests and result review
- **Referrals** — inter-facility and specialist referral workflow

### Outreach Mode

Designed for community health workers operating in the field. Provides access to:

- **Households** — household registration and member management
- **Screenings** — community-level health screenings
- **Immunizations** — vaccination tracking and scheduling
- **GPS** — geolocation tagging for household visits and service delivery points

### Supervisor Mode

Facility and team management mode for supervisors and managers. Provides access to:

- **KPIs** — key performance indicators and facility dashboards
- **Team** — staff overview, attendance, and task assignment
- **Stock** — pharmaceutical and consumable inventory management
- **Escalations** — exception handling and issue resolution workflows

### Offline Edge Mode

A dedicated mode for managing device synchronization and emergency access. Provides access to:

- **Sync Queue** — inspection and management of pending operations
- **Conflict Resolution** — field-level diff review and merge strategy selection
- **Break-Glass** — emergency access to patient data with full audit trail
- **Edge Snapshots** — download and reconciliation of offline data bundles

## Backend Integration

The Provider App communicates exclusively with the **Experience BFF** (Backend for Frontend) layer. All endpoints are scoped under:

```
/internal/v1/mobile/provider/*
```

The BFF enforces **v1.1 header contract** compliance. Requests missing required trust headers or presenting an outdated contract version are rejected.

Key backend patterns:

- **Outbox pattern** — every state mutation produces an event in the service's `event_outbox` table, ensuring reliable Kafka publishing even under partial failure
- **Idempotency** — write operations accept an idempotency key to prevent duplicate processing during retry scenarios, which is critical for offline sync

## Offline Capabilities

The Provider App implements a **local-first architecture** powered by CRDT-based data structures in the `@impilo/mobile-offline` package.

Core offline capabilities:

- **Background sync** — pending operations are synchronized automatically when connectivity is restored
- **Conflict detection** — field-level change tracking identifies divergence between local and server state
- **Edge snapshots** — pre-computed data bundles can be downloaded for extended offline operation
- **Break-glass emergency access** — time-limited access to critical patient data without server connectivity, with full audit trail generation

Offline support varies by mode. Outreach mode is fully offline-capable by design, while Provider and Supervisor modes offer limited offline functionality for essential operations.

## Testing

The test suite uses **Vitest** with a **jsdom** environment. There are 12 test files providing coverage across:

- Navigation and routing
- All four operational modes (Provider, Outreach, Supervisor, Offline Edge)
- Messaging and notification handling
- Telemedicine session management
- Backend integration and BFF communication

Run the test suite:

```bash
cd apps/mobile/provider-app
npx vitest
```

## Local Development

Start the Provider App in development mode:

```bash
cd apps/mobile/provider-app
npm run dev
```

The app expects the following services to be available locally:

| Service | Port |
|---|---|
| Keycloak | 8080 |
| Envoy (public) | 10000 |
| Experience BFF | 3000 |

Refer to the root `docker-compose.yml` for full local infrastructure setup.
