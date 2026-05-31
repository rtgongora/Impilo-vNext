# Full Mobile Parity Matrix (Tier-1)

**Generated**: 1970-01-01T00:00:00.000Z
**Source**: `ui/one-ui-shell/src/lib/routes.ts`
**Tier**: tier1
**Status**: done=49 missing=0 total=49

## Citizen app

| Web route | Title | Guard | Mobile files | Status |
|---|---|---|---|---|
| `/` | Home | `auth` | `apps/mobile/citizen-app/src/screens/HomeScreen.tsx` | DONE |
| `/citizen/health-id/qr` | My Health ID QR | `auth` | `apps/mobile/citizen-app/src/screens/personal/HealthIdSection.tsx` | DONE |
| `/communication` | Communication Hub | `auth` | `apps/mobile/citizen-app/src/screens/messaging/MessagingInboxScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/messaging/ThreadViewScreen.tsx` | DONE |
| `/communication/secure-messaging` | Secure Messaging | `auth` | `apps/mobile/citizen-app/src/screens/messaging/MessagingInboxScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/messaging/ThreadViewScreen.tsx` | DONE |
| `/home` | Home | `auth` | `apps/mobile/citizen-app/src/screens/HomeScreen.tsx` | DONE |
| `/home/profile` | My Profile | `auth` | `apps/mobile/citizen-app/src/screens/personal/ProfileSection.tsx` | DONE |
| `/marketplace` | Health Marketplace | `auth` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/bookings` | Bookings | `auth` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/cart` | Shopping Cart | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/catalog` | Service Catalog | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/ops` | Marketplace Operations | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/orders` | My Orders | `auth` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/orders/[id]` | Order Details | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/pickup` | Pickup Handoff | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/substitutions` | Substitutions | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/vendor` | Vendor Fulfilment | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/vendor/orders` | Vendor Orders | `role` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/marketplace/vendors` | Vendors | `auth` | `apps/mobile/citizen-app/src/screens/marketplace/MarketplaceScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/marketplace/CartScreen.tsx` | DONE |
| `/monitoring` | Remote Monitoring | `auth` | `apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx` | DONE |
| `/monitoring/alerts` | Monitoring Alerts | `auth` | `apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx` | DONE |
| `/monitoring/care-plans` | Chronic Care Plans | `auth` | `apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx` | DONE |
| `/monitoring/devices` | My Devices | `auth` | `apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx` | DONE |
| `/monitoring/provider-dashboard` | Patient Monitoring Dashboard | `facility` | `apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx` | DONE |
| `/monitoring/readings` | Readings & Trends | `auth` | `apps/mobile/citizen-app/src/screens/personal/MonitoringSection.tsx` | DONE |
| `/telemedicine` | Telemedicine Hub | `auth` | `apps/mobile/citizen-app/src/screens/telehealth/TelehealthListScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/telehealth/TelehealthSessionScreen.tsx` | DONE |
| `/telemedicine/new` | New Teleconsultation | `auth` | `apps/mobile/citizen-app/src/screens/telehealth/TelehealthListScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/telehealth/TelehealthSessionScreen.tsx` | DONE |
| `/telemedicine/session/[sessionId]` | Teleconsult Session | `auth` | `apps/mobile/citizen-app/src/screens/telehealth/TelehealthListScreen.tsx`<br/>`apps/mobile/citizen-app/src/screens/telehealth/TelehealthSessionScreen.tsx` | DONE |
| `/wellness` | Wellness Hub | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/activity` | Activity & Fitness | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/challenges` | Challenges | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/clubs` | Clubs & Communities | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/coaching` | Coaching & Habits | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/community` | Wellness Community | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/connect` | Health Connect ingest | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/diet` | Diet & Nutrition | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/goals` | Health Goals | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/programs` | Prevention Programs | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/routes` | Routes & Places | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/screenings` | Screening Schedule | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |
| `/wellness/sleep` | Sleep & Recovery | `auth` | `apps/mobile/citizen-app/src/screens/personal/WellnessSection.tsx` | DONE |

## Provider app

| Web route | Title | Guard | Mobile files | Status |
|---|---|---|---|---|
| `/clinical` | Clinical Care | `facility` | `apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/QueueManagementScreen.tsx` | DONE |
| `/clinical-tools` | Clinical Tools | `facility` | `apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/QueueManagementScreen.tsx` | DONE |
| `/communication/secure-messaging` | Secure Messaging | `auth` | `apps/mobile/provider-app/src/screens/provider/MessagingScreen.tsx` | DONE |
| `/ehr/[patientId]/results` | Results | `facility` | `apps/mobile/provider-app/src/screens/provider/ResultsViewScreen.tsx` | DONE |
| `/lab/results` | Results Review | `facility` | `apps/mobile/provider-app/src/screens/provider/ResultsViewScreen.tsx` | DONE |
| `/queue` | Patient Queue | `facility` | `apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/ClinicalToolsScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/QueueManagementScreen.tsx` | DONE |
| `/queue/search` | Patient Search | `facility` | `apps/mobile/provider-app/src/screens/provider/PatientLookupScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/PatientRegistrationScreen.tsx` | DONE |
| `/queue/walk-in` | Walk-in Registration | `facility` | `apps/mobile/provider-app/src/screens/provider/PatientLookupScreen.tsx`<br/>`apps/mobile/provider-app/src/screens/provider/PatientRegistrationScreen.tsx` | DONE |
| `/shift/handover` | Shift Handover | `facility` | `apps/mobile/provider-app/src/screens/provider/ShiftHandoffScreen.tsx` | DONE |

## Notes

- Tier-1 is intentionally a **journey set**, not all 252 web routes.

- Update `tools/parity/generate-mobile-parity-matrix.mjs` mapping rules when Tier-1 definition changes.
