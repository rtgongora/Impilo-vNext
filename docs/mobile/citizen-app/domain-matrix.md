# Citizen App Domain Matrix

Maps each domain to its data model, event types, and privacy classification.

## Domain Overview

| Domain | Primary Entity | DB Table(s) | Event Namespace | PII Level |
|---|---|---|---|---|
| Personal | Patient Profile | `patients` | `impilo.experience.citizen.profile-*` | HIGH — name, DOB, contact |
| Appointments | Appointment | `appointments` | `impilo.experience.citizen.appointment-*` | MEDIUM — facility, dates |
| Prescriptions | Prescription | `prescriptions` | `impilo.experience.citizen.prescription-*` | HIGH — medication data |
| Lab Results | Lab Result | `lab_results` | `impilo.experience.citizen.result-*` | HIGH — clinical values |
| Coverage | Coverage Plan | `coverage_plans` | n/a (read-only) | MEDIUM — insurance data |
| Social Feed | Feed Item | `feed_items`, `feed_likes` | n/a (read + like) | LOW — public content |
| Marketplace | Service, Request | `marketplace_services`, `service_requests` | `impilo.experience.citizen.service-*` | MEDIUM — service bookings |
| Messaging | Conversation, Message | `conversations`, `messages`, `conversation_participants` | `impilo.experience.citizen.conversation-*`, `impilo.experience.citizen.message-*` | HIGH — message content |
| Telehealth | Telehealth Session | `citizen_telehealth_sessions` | `impilo.experience.citizen.teleconsult-*` | HIGH — clinical sessions |
| Support | Support Ticket | `citizen_support_tickets` | `impilo.experience.citizen.support-ticket-*` | MEDIUM — issue descriptions |
| Consent | Consent Preference | `consent_preferences` | `impilo.experience.citizen.consent-*` | HIGH — consent decisions |

## Event Types

| Event | Trigger | Payload Keys |
|---|---|---|
| `profile-updated.v1` | PATCH profile | `patient_id`, changed fields |
| `consent-updated.v1` | PATCH consent | `consent_id`, `granted` |
| `account-deleted.v1` | DELETE account | `patient_id` |
| `appointment-requested.v1` | POST appointment | `appointment_id`, `type`, `facility_id` |
| `appointment-cancelled.v1` | POST cancel | `appointment_id` |
| `prescription-refill-requested.v1` | POST refill | `prescription_id` |
| `service-requested.v1` | POST service request | `request_id`, `service_id` |
| `service-cancelled.v1` | POST cancel request | `request_id` |
| `conversation-created.v1` | POST conversation | `conversation_id`, `type` |
| `message-sent.v1` | POST message | `message_id`, `conversation_id` |
| `teleconsult-requested.v1` | POST session | `session_id`, `type` |
| `teleconsult-joined.v1` | POST join | `session_id` |
| `teleconsult-ended.v1` | POST end | `session_id` |
| `support-ticket-created.v1` | POST ticket | `ticket_id`, `category` |

## Privacy and Data Access

- **No PII in SHR**: The citizen app follows the platform rule — BUTANO (HAPI FHIR) uses CPID only; PII stays in VITO.
- **Tenant Isolation**: Every query includes `tenant_id` in the WHERE clause. Cross-tenant data access is impossible at the SQL level.
- **Consent-Gated Access**: The consent preferences table allows citizens to control data sharing. Consent status is checked before sharing data with third parties.
- **Account Deletion**: Full GDPR-style account deletion is supported. The DELETE endpoint soft-deletes patient data and publishes an `account-deleted.v1` event for downstream cleanup.
