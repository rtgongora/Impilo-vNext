# Citizen App Feature Map

This document provides a comprehensive mapping of every feature in the Citizen App, linking each to its domain, screens, service layer, BFF route, and backend service.

## Feature Matrix

| Feature Area | Domain | Screen(s) | Service File | BFF Route | Backend Service | Status |
|---|---|---|---|---|---|---|
| Profile View/Edit | Personal | PersonalScreen → ProfileSection | `profileService.ts` | `GET/PATCH /internal/v1/mobile/citizen/profile` | VITO | IMPLEMENTED |
| Consent Management | Personal | SettingsSection | `profileService.ts` | `GET/PATCH /internal/v1/mobile/citizen/profile/consents` | VITO | IMPLEMENTED |
| Account Deletion | Personal | SettingsSection | `profileService.ts` | `DELETE /internal/v1/mobile/citizen/profile/account` | VITO | IMPLEMENTED |
| Appointments | Personal | AppointmentsSection | `appointmentService.ts` | `GET/POST /internal/v1/mobile/citizen/appointments` | TUSO | IMPLEMENTED |
| Appointment Cancel | Personal | AppointmentsSection | `appointmentService.ts` | `POST /internal/v1/mobile/citizen/appointments/{id}/cancel` | TUSO | IMPLEMENTED |
| Prescriptions | Personal | PrescriptionsSection | `prescriptionService.ts` | `GET /internal/v1/mobile/citizen/prescriptions` | OROS | IMPLEMENTED |
| Prescription Refill | Personal | PrescriptionsSection | `prescriptionService.ts` | `POST /internal/v1/mobile/citizen/prescriptions/{id}/refill` | OROS | IMPLEMENTED |
| Lab Results | Personal | ResultsSection | `labResultService.ts` | `GET /internal/v1/mobile/citizen/results` | ZIBO | IMPLEMENTED |
| Coverage Plans | Personal | CoverageSection | `coverageService.ts` | `GET /internal/v1/mobile/citizen/coverage` | PCT | IMPLEMENTED |
| Social Feed | Social | SocialFeedScreen | `feedService.ts` | `GET /internal/v1/mobile/citizen/feed` | Experience BFF | IMPLEMENTED |
| Feed Likes | Social | SocialFeedScreen | `feedService.ts` | `POST/DELETE /internal/v1/mobile/citizen/feed/{id}/like` | Experience BFF | IMPLEMENTED |
| Service Catalog | Marketplace | MarketplaceScreen | `marketplaceService.ts` | `GET /internal/v1/mobile/citizen/marketplace/services` | Experience BFF | IMPLEMENTED |
| Service Detail | Marketplace | MarketplaceScreen | `marketplaceService.ts` | `GET /internal/v1/mobile/citizen/marketplace/services/{id}` | Experience BFF | IMPLEMENTED |
| Service Requests | Marketplace | MarketplaceScreen | `marketplaceService.ts` | `POST /internal/v1/mobile/citizen/marketplace/requests` | Experience BFF | IMPLEMENTED |
| Request Tracking | Marketplace | MarketplaceScreen | `marketplaceService.ts` | `GET /internal/v1/mobile/citizen/marketplace/requests` | Experience BFF | IMPLEMENTED |
| Request Cancel | Marketplace | MarketplaceScreen | `marketplaceService.ts` | `POST /internal/v1/mobile/citizen/marketplace/requests/{id}/cancel` | Experience BFF | IMPLEMENTED |
| Conversations | Messaging | MessagingInboxScreen | `messagingService.ts` | `GET/POST /internal/v1/mobile/citizen/messaging/conversations` | Experience BFF | IMPLEMENTED |
| Messages | Messaging | ThreadViewScreen | `messagingService.ts` | `GET/POST /internal/v1/mobile/citizen/messaging/conversations/{id}/messages` | Experience BFF | IMPLEMENTED |
| Read Receipts | Messaging | ThreadViewScreen | `messagingService.ts` | `POST /internal/v1/mobile/citizen/messaging/conversations/{id}/read` | Experience BFF | IMPLEMENTED |
| Telehealth Sessions | Telehealth | TelehealthListScreen | `telehealthService.ts` | `GET /internal/v1/mobile/citizen/telehealth/sessions` | Experience BFF | IMPLEMENTED |
| Request Teleconsult | Telehealth | TelehealthListScreen | `telehealthService.ts` | `POST /internal/v1/mobile/citizen/telehealth/sessions` | Experience BFF | IMPLEMENTED |
| Join Session | Telehealth | TelehealthSessionScreen | `telehealthService.ts` | `POST /internal/v1/mobile/citizen/telehealth/sessions/{id}/join` | Experience BFF | IMPLEMENTED |
| End Session | Telehealth | TelehealthSessionScreen | `telehealthService.ts` | `POST /internal/v1/mobile/citizen/telehealth/sessions/{id}/end` | Experience BFF | IMPLEMENTED |
| Support Tickets | Support | HomeScreen (quick action) | `supportService.ts` | `GET/POST /internal/v1/mobile/citizen/support/tickets` | Experience BFF | IMPLEMENTED |
| Knowledge Articles | Support | HomeScreen (quick action) | `supportService.ts` | `GET /internal/v1/mobile/citizen/support/articles` | Experience BFF | IMPLEMENTED |
| Notifications | Cross-cutting | NotificationsScreen | `@impilo/mobile-messaging` | Push + in-app channels | Messaging Gateway | IMPLEMENTED |

## Notes

- All BFF routes are prefixed with `/internal/v1/mobile/citizen` and enforce v1.1 header contract compliance.
- Every write operation publishes a domain event via the transactional outbox pattern.
- The `X-Actor-ID` header identifies the citizen; the BFF resolves this to a patient ID via CPID lookup.
- The "Backend Service" column indicates the primary service handling the domain logic. Citizen BFF controllers in the Experience BFF handle orchestration.
