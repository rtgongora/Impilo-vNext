# External App Go-Live Checklist

Before an external application moves from `SANDBOX` → `STAGING` → `PRODUCTION`
in Impilo vNext, the following must be checked off by the publisher and
co-signed by the Impilo integration governance team.

## 1. Identity & publisher

- [ ] `Publisher` record created with verified support and data-protection
      contacts.
- [ ] `ExternalApplication` record created with a stable `appCode`, owning
      `publisherId`, intended environment (`SANDBOX`/`STAGING`/`PRODUCTION`),
      and `integrationCategory`.
- [ ] OAuth2 client credentials issued (or mTLS material exchanged).

## 2. Contracts

- [ ] At least one `IntegrationContract` bound to the external app and
      signed by the publisher.
- [ ] All required `WebhookSubscription`s registered with a valid
      `deliveryUrl` and a working signing secret (verified via
      `POST /internal/v1/webhook-subscriptions/{id}/test`).
- [ ] `ExternalEventSubscription`s for any needed event topics, each
      against an `EXTERNALLY_PUBLISHABLE` event from the catalogue.
- [ ] No subscription to `INTERNAL_PLATFORM` events.

## 3. Security

- [ ] OAuth2 token expiry ≤ 1 hour; refresh tokens rotate at every use.
- [ ] No secret material logged or sent in URLs.
- [ ] HMAC-SHA256 webhook verification implemented on receiver side, with
      a 300-second skew window.
- [ ] Replay protection via `X-Impilo-Webhook-Delivery-Id`.
- [ ] Rate limits documented; the partner can survive the configured
      `rateLimitRpm` plus burst.
- [ ] IP allowlist (if production) provided.
- [ ] Incident-reporting contact reachable 24/7.

## 4. Data protection

- [ ] Purpose-of-use values agreed and stamped into every request.
- [ ] Consent basis recorded where any patient/citizen data flows.
- [ ] Data minimisation: webhooks carry references (e.g. encounter ids),
      not full PHI, except where the contract explicitly authorises it.
- [ ] Data retention policy stated and aligned with Zimbabwe’s health data
      governance posture.

## 5. Clinical safety (if applicable)

- [ ] Clinical safety classification declared on the marketplace item.
- [ ] Failure modes documented (delayed result, missing report, etc.).
- [ ] Fallback behaviour defined when Impilo cannot reach the external
      system (graceful degradation in the user-facing UI).

## 6. Operations & monitoring

- [ ] External app health endpoint reachable from Impilo monitoring.
- [ ] Audit trail flows into `services/audit-ledger-service/` with the
      expected `externalAppId` tag.
- [ ] Marketplace item moved to `APPROVED`; installation created in target
      tenant; lifecycle moved to `ACTIVE`.
- [ ] Integration ops dashboard shows green health for 7 consecutive days
      in staging before promoting to production.

## 7. UX

- [ ] App appears in the Health OS launcher for the right roles / facilities
      (verify via `GET /internal/v1/marketplace/launcher`).
- [ ] Citizen-facing or provider-facing screens render without referencing
      vendor specifics (canonical Impilo terminology only).
- [ ] Nompilo can describe the capability via `explainCapabilityStatus`.

## 8. Sign-off

- [ ] Publisher technical contact signature
- [ ] Publisher data-protection contact signature
- [ ] Impilo Integration Hub owner sign-off
- [ ] Impilo Capability Marketplace owner sign-off
- [ ] Clinical safety officer sign-off (where required)
