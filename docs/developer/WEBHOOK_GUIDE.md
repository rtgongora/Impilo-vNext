# Webhook Guide — External App → Impilo, Impilo → External App

Impilo vNext delivers governed events to approved external apps and accepts
signed callbacks from those apps. All deliveries are signed and replay-
protected.

## Subscribing as an external app

1. Register your external application:
   ```http
   POST /internal/v1/external-apps
   ```
2. Bind a contract:
   ```http
   POST /internal/v1/integration-contracts
   ```
3. Subscribe to one or more **externally publishable** events from the
   [Event Catalogue](#event-catalogue):
   ```http
   POST /internal/v1/webhook-subscriptions
   {
     "externalAppId": "ext-app-...",
     "integrationContractId": "ic-...",
     "eventTopics": ["lab.result.received"],
     "deliveryUrl": "https://partner.example/webhooks/impilo",
     "signatureMethod": "HMAC_SHA256",
     "retryPolicy": {"maxRetries": 6, "initialDelayMs": 1000, "maxDelayMs": 60000},
     "deadLetterAfterRetries": 6
   }
   ```
4. Receive a per-subscription signing-secret reference. The actual secret
   value is stored in `vault-kms` and never returned in any API.

## Delivery envelope

```http
POST /webhooks/impilo HTTP/1.1
Content-Type: application/json
X-Impilo-Webhook-Delivery-Id: 8b1f...
X-Impilo-Webhook-Topic: lab.result.received
X-Impilo-Webhook-Signature: t=1735689600,v1=<hex hmac-sha256>

{ "event": "lab.result.received", "schemaVersion": "1.0.0", "data": { ... } }
```

The signed payload is the **literal string** `"<timestamp>.<body>"` where
`<body>` is the exact UTF-8 JSON the receiver sees. Reject any delivery
whose `t` value is more than 300 seconds from your wall-clock time.

Reference verifier (Java):
```java
// services/integration-hub/.../WebhookSigner.java
new WebhookSigner().verify(secret, body, signatureHeader, 300);
```

Reference verifier (Node, pseudo):
```js
import { createHmac, timingSafeEqual } from "node:crypto";
function verify(secret, body, header, maxSkew = 300) {
  const map = Object.fromEntries(header.split(",").map(p => p.split("=", 2)));
  const t = Number(map.t); if (!Number.isFinite(t)) return false;
  if (Math.abs(Date.now()/1000 - t) > maxSkew) return false;
  const expected = createHmac("sha256", secret).update(`${t}.${body}`).digest("hex");
  return timingSafeEqual(Buffer.from(expected), Buffer.from(map.v1));
}
```

## Retry policy and dead-letter

* Initial delay defaults to 1s, exponential backoff up to `maxDelayMs`.
* After `deadLetterAfterRetries` consecutive failures the subscription
  is paused and the integration ops dashboard surfaces the failure.
* `consecutiveFailures` resets to 0 on the next successful 2xx delivery.

## Replay protection

* Each delivery carries a unique `X-Impilo-Webhook-Delivery-Id`.
* Receivers SHOULD record delivery ids and ignore duplicates.
* Events are also delivery-idempotent at the event-id level (every event
  emitted from the outbox has a stable `eventId` distinct from the
  delivery id).

## Event catalogue

Browse the governed event catalogue at `/developer/event-catalogue` in the
one-ui-shell or `GET /internal/v1/event-catalogue?classification=EXTERNALLY_PUBLISHABLE`.

Internal-only events (`classification: INTERNAL_PLATFORM`) MUST NOT be
exposed to external apps — they exist only for sovereign service
coordination.

## Inbound webhooks (App → Impilo)

When an external app needs to call Impilo (for example, to deliver a lab
report from the LIMS), it MUST sign the request with the same scheme using
the secret issued to its `ExternalApplication`. Impilo verifies the
signature via the same `WebhookSigner.verify` helper at the gateway edge.

Inbound deliveries are rejected if:

* `X-External-App-Id` does not match an `ACTIVE` external application.
* The integration contract does not allow the requested API operation.
* The signature does not verify or the timestamp is outside the skew window.
* The body declares an event not whitelisted on the contract.
