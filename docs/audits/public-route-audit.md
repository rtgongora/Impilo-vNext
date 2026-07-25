# Public route audit

Date: 2026-07-25

The audit covers the routes admitted by `src/middleware.ts`, the public links exposed by
the shared header/footer and the public BFF capability register.

## Classification

| Classification | Routes | Result |
|---|---|---|
| Functional public journey | `/`, `/welcome/find-care/**`, `/welcome/emergency/**`, `/welcome/report/**`, `/welcome/health-info`, `/welcome/notices`, `/welcome/marketplace`, `/welcome/coverage`, `/welcome/wellness`, `/welcome/learning`, `/welcome/regulatory`, `/get-involved/**`, `/status` | Retained and linked from one shell. Data comes from registered anonymous lanes or a documented deterministic emergency protocol. |
| Public verification/information | `/verify/**`, `/about`, `/contact`, `/privacy`, `/terms`, `/consent`, `/account-deletion`, `/download`, `/welcome/accessibility` | Public. Verification exposes only approved registry fields. |
| Authentication boundary | `/auth/**` and protected actions reached through a reasoned prompt or intent link | Retained. Sign-in is visible but is no longer the first public task. |
| Professional entry | `/provider/get-access`, `/welcome/regulatory`, the Work on Impilo section | Public explainer/entry. Authentication does not imply professional authority. |
| Machine/public support | `/.well-known/**`, `/geo/**`, `/map/**`, static assets and gateway paths | Retained. Map assets remain public so the list/map journey does not silently break. |
| Legacy site seam | `/services`, `/solutions`, `/features`, `/resources`, `/docs`, `/training`, `/apps`, `/community`, `/technical` | Redirected into the canonical shell or a real public journey. |

## Repaired seams

- The previous public Vite site no longer owns `/` or brochure paths at the edge.
  `deploy/tls/mohcc-gov/public-website.yaml` now exposes only `/.well-known/**` for
  mobile association compatibility. The one-ui-shell ingress catch-all owns all
  human-facing routes.
- `/` renders the need-first `PublicLanding` for a guest. `/welcome` renders the same
  composition for compatibility. A current session continues to `/home`.
- The logo and all Home/Back controls point to `/`. There is no “website versus VNext”
  hand-off language.
- Legacy brochure URLs receive permanent redirects to real content. No redirect target
  requires authentication.
- The shared header exposes Home, Health Services, Find, Health Information,
  Get Involved, Download, Sign In, language, accessibility and a persistent Emergency
  action. Its compact navigation wraps into a grid instead of causing page-level
  horizontal scrolling.

## Journey and continuity findings

- Find care is a guest journey with list/map alternatives, optional geolocation, manual
  location, clear errors and browser-session preservation.
- Emergency is one-click public access with call actions, deterministic danger-sign
  escalation, guest assistance, location alternatives, Nompilo guidance and an SOS
  status timeline.
- RITO feedback begins anonymously/guest, separates safety escalation, confirms the
  submission and provides claim-code status continuity.
- Health information, notices, marketplace, coverage, wellness, learning and regulatory
  routes are real reads. Their protected next actions explain the sign-in boundary.
- The public Khuluma indicator appears only for a real locally persisted active journey.
  Receipt cards link to the real status path and state that guest live messaging is not
  yet enabled.

## Dead, duplicate and placeholder result

No primary landing action points to a placeholder or dead route. The obsolete
human-facing public-site ingress is removed rather than hidden behind a second brand
surface. Incomplete backend capabilities are not promoted as available; they remain in
the public capability register with their missing contract.
