# Public experience implementation inventory

Date: 2026-07-25

This inventory records the implementation baseline used for the unified Impilo
public-to-authenticated experience. It complements the
[public capability register](../registry/public-capability-register.md), which remains
the source of truth for deployed anonymous lanes and deferred backend contracts.

## Product and frontend

- `ui/one-ui-shell` is the canonical Next.js experience shell. The public routes,
  authentication routes and protected workspaces share this application, design tokens,
  middleware and BFF boundary.
- `ui/one-ui-shell/src/app/page.tsx` is the canonical public root for guests and sends an
  existing session to `/home`.
- `PublicShell`, `PublicHeader`, `PublicFooter`, `PublicBackBar`,
  `PublicAccessibilityMenu`, `LanguageSwitcher`, `SkipToContent` and
  `EmergencyHelpButton` provide the shared public chrome.
- Tailwind utilities plus `src/styles/globals.css` provide the visual system. The public
  landing uses fluid type, grid/flex layouts, a 90rem context width, container queries,
  touch-sized actions and reduced-motion rules.
- Mobile parity exists in `apps/mobile`; installable citizen and provider APKs and the
  redroid runtime verification are separate from the responsive web entry. The public
  web remains the universally available low-friction entry.

## Identity, trust and authority

- `src/middleware.ts` admits only the registered public paths without a session. The BFF
  separately enforces its explicit anonymous route allow-list.
- `ContinueWithoutSignIn` and `ReasonedSignInPrompt` express the public-to-verified
  boundary. `IntentLink` creates a fresh gateway intent at click time so the destination
  and current public intent survive authentication.
- `useFindCareJourneyStore` retains care need, service, location, filters, selected
  facility and results in the browser guest session. Feedback and emergency journeys use
  claim/reference codes for pseudonymous continuity.
- Authentication is not authority. `AuthGuardProvider`, route role guards, TSHEPO
  context queries, `RoleJourneyNavigation`, `WorkspaceContextSwitcher` and the
  experience context bars resolve the workspaces a signed-in actor may use.

## Live public integrations

| Need | Existing implementation | System truth |
|---|---|---|
| Find care | `FindCareExperience`, `FindCareMap`, `FindCareResultCard`, facility detail | TUSO + NDILA orchestration; VARAPI practitioners |
| Verify | practitioner verification and facility certificate/credential routes | VARAPI and TUSO public verification lanes |
| Emergency | `EmergencyExperience`, deterministic triage, location picker, nearby care, SOS timeline | DAIDZAI + TUSO/NDILA + MADI; guest path |
| Nompilo | hero command mode, emergency inline ask and authenticated context panels | GUIDANCE anonymous grounded ask; honesty and rate-limit gates |
| Feedback | `PublicFeedbackForm`, triage, status lookup and confirmation receipt | RITO anonymous/guest feedback lanes |
| Health information | `PublicHealthInfo` | GUIDANCE education/search |
| Notices | `PublicNoticesBoard` | GUIDANCE advisories/notices |
| Marketplace | `PublicMarketplaceBrowse` | MSIKA and MSIKA-flow published listings/vendors |
| Cover | `PublicCoverageCompare` | COVERAGE public plans and benefits |
| Wellness | `PublicWellnessExplorer` | SIMBA screening plus stateless BMI/BP calculation |
| Learning | `PublicLearningCatalog` | FUNDO public catalogue |
| Regulation | `PublicRegulatoryExplorer` | TUSO requirements and VARAPI councils |
| Participation | Get Involved pages and receipt/status paths | participation-service anonymous lanes |
| Communication | authenticated Khuluma home/workspace; public journey status cards | guest live chat remains deferred; claim/status continuity is live |

No component invents facility availability, clinical advice, prices, authority or
communication state. Data-bearing public cards use the BFF integrations above and render
explicit loading, empty or unavailable states.

## Shared component coverage

The requested primitives are implemented by the following reusable components:

- public shell and emergency control: `PublicShell`, `PublicHeader`,
  `EmergencyHelpButton`;
- Nompilo modes: `WelcomeHero`, `NompiloAskInline`, `NompiloContextPanel`,
  `NompiloContextualGuidance`, `NompiloGlobalCommandBar`;
- communication continuity: `KhulumaJourneyUpdate`, `PublicKhulumaIndicator`, and the
  authenticated `components/khuluma` workspace;
- adaptive launch and visual layer: `AdaptiveServiceLauncher`, `PublicVisualAsset`;
- care results/map: `FindCareResultCard`, `FacilityExperienceCard`, `FindCareMap`,
  `FindCareFacilityDetail`;
- trust boundary: `ContinueWithoutSignIn`, `ReasonedSignInPrompt`, `IntentLink`;
- guided work: `JourneyStepper`, journey orchestration rails, facility setup wizard and
  the emergency/find-care steppers;
- context and authority: `WorkspaceContextSwitcher`, `RoleJourneyNavigation`, context
  bars and rails;
- confirmations/recovery: the SOS timeline, feedback receipt/status, access-choice
  components and each public data surface's loading/error/empty states.

The repo contains domain-specific implementations where the data or risk model differs;
it does not force clinical, regulatory and public workflows through a misleading generic
component.

## Known contract boundaries

The following are intentionally not represented as live:

- anonymous Khuluma conversation and voice/video;
- temporary emergency identity reconciliation;
- SMS/USSD/voice low-bandwidth channels;
- product-registry BFF browse, a guest marketplace basket and anonymous cost estimates;
- public payer-network search;
- additional anonymous wellness assessments beyond the live stateless BMI/BP contract.

These remain recorded as `DEFERRED` or `PLANNED` in the capability register. The unified
experience provides a real alternate route, an honest unavailable state or a clear
sign-in boundary instead of sample data.
