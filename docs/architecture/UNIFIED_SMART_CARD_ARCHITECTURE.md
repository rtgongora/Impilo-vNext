# Unified SMART Card — One Physical Card, Three Functions

> **Concept (product doctrine):** One physical card with three functions —
> **(1) Identity**, **(2) Financial transactions**, **(3) Carrying the Personal Health Record.**
> One card, one person anchor (Health ID), one on-card cryptographic identity, three governed function-facets.

This document specifies the **technology** for that card: the physical medium, the on-card vs
server split, the cryptography, the data model, the lifecycle, and the offline/sync behaviour — and
the reconciliation from today's fragmented implementation.

---

## 0. Grounding — what already exists (this is a unification, not greenfield)

All three functions and the physical-card technology are already built, but split across services with
**no shared card identity** (the core defect this design fixes):

| Facet | Where it lives today | Key tech in place |
|---|---|---|
| Physical personalisation | `card-print-agent` (:8291) | `SecureElementKeyService` generates a **NIST P-256 (secp256r1) EC keypair on the card's secure element**; `PdfRenderService` prints a **QR code** (zxing); `VitoCardClient` provisions the SE public key to VITO on print (G032) |
| **1. Identity** | `vito-service` `SmartCardEntity` | `did_uri` (`did:impilo:<sha256>`), `public_key` (SE public key PEM), `card_number`, `health_id`, `previous_card_id`, status FSM; `QrSigningService` mints **Ed25519 JWS** QR tokens (purpose = HEALTH_ID / WALLET / EMERGENCY / PICKUP, TTL) |
| **2. Financial** | `mushe-wallet-service` `CardEntity` + wallet | PIN (BCrypt, 3 tries → block), activate/block/replace, `card.walletId` → double-entry wallet ledger; offline transactions carry a **JWS signed by the card key** |
| **3. PHR** | `mushe-wallet-service` `CardHealthDataService` | Critical summaries + **IPS bundles**, **AES-256-GCM** (per-record key HMAC-SHA256-derived from a fail-closed master key), **SHA-256** integrity, queued in `card_update_queue` for sync to the card via card-print-agent or an **NFC writer**; tshepo-keys custody planned |

**The defect:** VITO's `SmartCardEntity` (identity) and mushe's `CardEntity` (financial + PHR carrier)
are **two independent records with no shared reference** — no shared card id, no linking events. A
single physical card is therefore represented twice, and a replacement/revocation on one side does
not propagate to the other. The unified architecture makes **one canonical card credential** the spine
all three facets reference.

---

## 1. The physical card technology

A **dual-interface secure-element smart card** (contact + contactless/NFC), printed with a QR code:

- **Secure Element (SE) — the root of trust.** An on-card tamper-resistant chip holding a **NIST P-256
  EC keypair** generated *on the card* at personalisation (private key never leaves the SE). This is
  already the `card-print-agent` model. The SE also holds the **financial PIN** and the **offline
  purse counter** (see §4).
- **NFC / contactless data area.** Read/write storage (a few KB) for the **encrypted PHR bundle**
  (IPS) and the **signed offline financial balance**, written by an NFC writer / the card-print-agent
  (`card_update_queue` is the existing sync queue).
- **Printed QR code.** A signed, short-TTL pointer (Ed25519 JWS, `QrSigningService`) for camera-only
  presentation where NFC is unavailable — resolves server-side to the person/card.
- **Human-readable face.** Name, card number, Health ID (masked), photo — printed by
  `PdfRenderService`.

> **Why a secure element, not a printed-QR-only card:** functions 2 and 3 require on-card secrets
> (PIN, purse, encrypted PHR) and offline cryptographic proof (the card *signs* a challenge / a
> transaction). A printed QR alone cannot hold a private key or an offline balance. The SE is the
> minimum technology that makes all three functions offline-capable and forgery-resistant. The QR is
> the graceful-degradation channel for identity presentation only.

---

## 2. The canonical Card Credential (the shared spine)

One record, owned by **VITO** (the identity system-of-record), that every facet references:

```
CardCredential {
  cardNumber        // canonical, human-readable, printed on the card
  healthId          // the person anchor (one Health ID per person)
  seedPublicKey     // the SE P-256 public key (PEM) — the on-card root of trust
  didUri            // did:impilo:<sha256(publicKey)> — the W3C DID
  status            // REQUESTED → PRINTED → ACTIVE → SUSPENDED → REVOKED
  previousCardId    // replacement chain (secure handover)
}
```

- **VITO owns `CardCredential`** because the card *is* an identity artifact; the SE public key + DID +
  Health-ID binding are identity truth.
- **mushe-wallet-service references `CardCredential.cardNumber`** for the financial + PHR-carrier
  facets — it does **not** mint its own second card record. Its `CardEntity` becomes a *money projection*
  keyed by `cardNumber` (+ `walletId`), not an independent identity.
- **Propagation is event-driven.** VITO emits `impilo.vito.card.{issued,activated,suspended,revoked,replaced}`
  on the outbox; mushe-wallet (and any PHR sync) consume these so a single physical event (e.g. a
  lost-card revocation) reaches all three facets atomically-enough. (Today mushe's `WalletEventConsumer`
  listens only to `mushex.payment.status.changed` — this design adds the card lifecycle topic.)

---

## 3. Function 1 — Identity

- **On card:** SE P-256 private key (never leaves), DID, Health-ID reference, printed QR.
- **Server SoR:** VITO `CardCredential` + the DID document (`did:impilo`).
- **Offline authentication (challenge–response):** a verifier (kiosk, provider device, offline reader)
  issues a random **nonce**; the card signs it with the SE key; the verifier checks the signature
  against the DID's public key (cached DID doc). No network needed. The card proves it holds the
  private key without exposing it.
- **QR presentation (degraded / camera-only):** `QrSigningService` mints a short-TTL Ed25519 JWS
  pointer; the scanner resolves it online. Used where the reader can't do NFC challenge-response.
- **Trust integration:** every server-side card auth resolves through **tshepo** (ext_authz → assurance
  level). Card-present + PIN raises the assurance level (per the graduated-friction doctrine).

---

## 4. Function 2 — Financial transactions

- **Server SoR = mushe-wallet-service** (the money wallet: double-entry ledger, balance, transfer).
  VITO never holds money (its parallel `WalletService` is deprecated — see §9).
- **On card:** the **PIN** (verified in the SE, BCrypt server-mirror for online), and an **offline
  stored-value purse** — a signed balance + monotonic **transaction counter/nonce** in the SE.
- **Online spend:** PIN → mushe `POST /internal/v1/wallets/{id}/debit`; the ledger is authoritative.
- **Offline spend (no network):** the card debits its SE purse and emits a **JWS-signed offline
  transaction** (amount, counter, timestamp, card DID) — the existing "offline transactions carry a
  JWS signature from the card key" mechanism. On next sync, mushe **reconciles** each offline txn into
  the ledger, using the monotonic counter to reject replays and detect gaps. Server balance wins;
  offline purse is a bounded float reconciled up.
- **Replacement:** the wallet stays with the *person* (keyed by Health ID / `walletId`); a new card
  gets a fresh SE purse seeded from the reconciled server balance. No money lives only on a card.

---

## 5. Function 3 — Carrying the Personal Health Record

- **Record SoR = the clinical record** (BUTANO / SHR, CPID-keyed). The card is an **offline signed
  cache**, never the source of truth.
- **On card (NFC data area):** an **encrypted IPS bundle** (International Patient Summary) + critical
  summaries — the existing `CardHealthDataService` payload: serialized → compressed → **AES-256-GCM**
  (per-record key HMAC-SHA256-derived) → **SHA-256** integrity hash. Key custody moves to
  **tshepo-keys-service** (today a fail-closed deployment master key).
- **Point-of-care offline read:** a provider with a reader + the person's PIN/consent decrypts and
  reads the IPS at the bedside with no network — the core value of "carrying the PHR."
- **Sync:** when a clinical encounter completes, the server re-serializes the IPS and queues a card
  update (`card_update_queue`) for the NFC writer. The card is refreshed; conflicts never arise
  because the card is read-mostly (writes flow server → card).
- **Consent:** what may be written to / read from the card is gated by **Mvumo** consent + tshepo
  purpose-of-use. PHI-on-card carries only what consent permits.

---

## 6. On-card data partition

| Zone | Contents | Access |
|---|---|---|
| **Secure Element (tamper-resistant)** | P-256 private key; PIN; offline purse balance + counter | Never leaves the card; used to *sign* / *verify PIN* on-chip |
| **NFC data area (encrypted)** | AES-256-GCM IPS/PHR bundle; signed offline balance snapshot; DID doc cache | Read with reader + consent; written server→card via NFC writer |
| **Printed surface** | Card number, name, masked Health ID, photo, **signed QR** | Human / camera |

---

## 7. Cryptography & key stack (all already present)

| Purpose | Primitive |
|---|---|
| Card root of trust / offline auth / offline txn signing | **EC NIST P-256 (secp256r1)** SE keypair |
| DID | **`did:impilo:<sha256(publicKey)>`** (W3C DID) |
| QR presentation tokens | **Ed25519 JWS** (short TTL), `QrSigningService` |
| Offline financial transactions | **JWS** signed by the SE key + monotonic counter |
| PHR-on-card confidentiality | **AES-256-GCM**, per-record HMAC-SHA256-derived key |
| PHR integrity | **SHA-256** of plaintext |
| PIN | verified in SE; **BCrypt** server mirror |
| Key custody | **tshepo-keys-service** (target; fail-closed master key today) |

---

## 8. Unified lifecycle (one event stream, three facets)

```
REQUEST ──▶ PERSONALISE ──▶ PRINT ──▶ ACTIVATE ──▶ ACTIVE ──▶ (SUSPEND) ──▶ REVOKE/REPLACE
  vito       card-print-agent  card-print   set PIN     in use        lost/stolen    secure handover
             (SE keypair)      (QR+face)   (mushe)   (all 3 facets)   (mushe+vito)   (new SE keypair,
                                                                                     new DID, wallet
                                                                                     stays with person,
                                                                                     PHR re-synced)
```

Each transition VITO records on `CardCredential` and emits a `impilo.vito.card.*` event. mushe-wallet
consumes it to keep the money projection + card-PHR state consistent (e.g. REVOKE blocks the card's
purse and stops PHR sync). **Secure handover** (`RecoveryService`, now identity-only) revokes the old
credential and issues a new one; mushe reacts by migrating the wallet's card reference and re-seeding
the offline purse — no money and no PHR is trapped on the dead card.

---

## 9. Reconciliation from today's fragmented state (migration path)

1. **Money SoR consolidated (done).** VITO's parallel `WalletService`/`WalletController` are
   `@Deprecated`; `RecoveryService` is identity-only. mushe-wallet is the money SoR.
2. **Introduce `CardCredential` as VITO's canonical card record** (extend `SmartCardEntity`: it already
   has cardNumber, healthId, didUri, publicKey, previousCardId — it *is* the credential; formalise it
   and publish the lifecycle events).
3. **Repoint mushe `CardEntity` to reference `cardNumber`** (a foreign reference to the VITO credential)
   instead of being an independent card identity; add the `impilo.vito.card.*` consumer to
   `WalletEventConsumer`.
4. **Move PHR key custody to tshepo-keys-service** and gate PHR-on-card writes/reads through Mvumo
   consent + tshepo purpose-of-use.
5. **Data migration prerequisite (operator):** before any card-record cleanup, reconcile/migrate
   existing card–wallet balances so nothing is lost (mushe is the money holder).

---

## 10. Standards & interoperability

- **Identity:** W3C DID (`did:impilo`), JOSE/JWS, EC P-256 — standard, verifiable offline.
- **PHR:** HL7 **IPS** (International Patient Summary) as the on-card clinical payload — interoperable
  with the SHR / FHIR gateway.
- **Financial:** double-entry ledger + signed offline purse — auditable, reconcilable; the offline-purse
  + counter model mirrors established stored-value-card practice.

---

## 10a. Implementation status (build log)

| Phase | Status | Detail |
|---|---|---|
| **1. VITO CardCredential spine + lifecycle events** | ✅ **BUILT** | `CardLifecycleService` now emits canonical `SMART_CARD` events on every transition — `CARD_REQUESTED/PRINTED/ACTIVATED/SUSPENDED/REVOKED/REPLACED` — each carrying the full credential payload (cardNumber, healthId, didUri, status, previousCardId, revocationReason). Secure handover links the replacement chain (`recordReplacement` → `CARD_REPLACED`). Topic: `impilo.vito.cards` (+ legacy `vito.cards`). Unit-tested. |
| **2. mushe references the credential + safety integration** | ✅ **BUILT** | `cards.vito_card_number` link + `findByVitoCardNumber`; `VitoCardEventConsumer` on `impilo.vito.cards` calls `freezeForVitoCard` → a revoked/suspended SMART card **freezes its linked money card** (idempotent, no-op when unlinked). Unit-tested. *Live cross-service Kafka flow needs a deploy-time integration check.* |
| **3. Financial offline purse + JWS reconciliation** | ⛔ **SPEC (needs real env / hardware)** | No offline-txn/counter/JWS/purse seam exists in mushe today (vito's was the only one, now deprecated). Full build: (a) propagate the SE **public key** on the card events so mushe can cache it (extend the Phase-1 payload); (b) an offline-txn ingestion endpoint that **verifies the JWS against the card's P-256 SE public key**, enforces a **monotonic counter** (replay/gap detection), and reconciles each txn into the double-entry ledger (server balance authoritative). Meaningful testing requires **real card-signed transactions** (SE hardware) — deferred rather than shipped as blind money-crypto. |
| **4. PHR key custody → tshepo-keys + Mvumo consent gate** | ⛔ **SPEC (platform-gap blocked)** | Key custody move is **blocked**: `CardHealthDataService` documents that *the platform has no data-encryption-key (KMS) service — tshepo-keys is signing-only*. The fail-closed deployment master key is the honest current state until a data-key service exists. The **Mvumo consent gate** on the encounter→card PHR write (`WalletEventConsumer.onEncounterCompleted`) is buildable but needs a mushe→Mvumo client (cross-service). |

**Net:** the *unification core* — one canonical card credential, one lifecycle event stream, and the cross-facet safety loop (revoke → freeze money) — is built and tested. Phases 3–4 are money-/PHI-crypto and are honestly gated on card hardware and a platform KMS respectively; their precise specs are above. Two operator prerequisites remain from §9: migrate any legacy vito card-wallet balances into mushe before dropping the deprecated vito ledger, and (Phase-3 enabler) decide whether mushe caches the SE public key from card events.

## 11. Summary

One physical **secure-element smart card** carries three governed functions off one cryptographic
identity: the SE P-256 keypair is the root of trust; the **canonical `CardCredential`** (VITO-owned) is
the shared spine; **identity** is SE challenge-response + signed QR; **financial** is a mushe-wallet
ledger with a signed offline purse; the **PHR** is an encrypted, consent-gated IPS cache synced from
the SHR. The build work is *unification* — one card record, one lifecycle event stream, three facets
that already exist — not new invention.
