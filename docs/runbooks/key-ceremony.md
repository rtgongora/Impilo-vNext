# Key Ceremony & Rotation Runbook (tshepo-keys)

Operational runbook for the signing-key hierarchy owned by `tshepo-keys-service` (Trust plane).
Covers the key hierarchy and purposes, generation ceremony (software and HSM), dual control,
K1↔K2 rotation overlap, backup/recovery, rotation intervals, compromise/revocation response,
and JWKS publication.

> Scope: this is the **W4b–W4d key-custody hardening** workstream. All new signing sources and
> HSM/Vault seams are **flag-gated**; the default remains software custody with the existing
> seed-derived QR/card paths so preview stays green without tshepo-keys or an HSM.

---

## 1. Key hierarchy

```
KEK (Key-Encryption-Key, AES-256)                 ← root of software custody
  └── source: KekProvider
        ├── ConfigKekProvider   (impilo.keys.kek-source=config, default)  tshepo.keys.kek (hex)
        └── VaultKekProvider    (impilo.keys.kek-source=vault)            Vault/KMS (stub → wire)

Signing keys (Ed25519, per tenant, purpose-scoped)  ← wrapped/held under custody
  └── custody: KeyCustodyProvider
        ├── SoftwareKeyCustodyProvider (impilo.keys.custody-provider=software, default)
        │       private key = AES-256-GCM( KEK , rawPrivateKey )  stored in Postgres
        └── Pkcs11KeyCustodyProvider   (impilo.keys.custody-provider=pkcs11)
                private key = non-exportable HSM handle (exportPrivate() forbidden)
```

- **KEK** encrypts every stored software private key. Whoever holds the KEK can decrypt all
  software-custody keys — treat it as a root secret. In production it MUST come from a secret
  manager (env injection today; Vault/KMS via `VaultKekProvider` once wired).
- **Signing keys** are Ed25519, minted per tenant and **purpose-scoped** (fail-closed): a key may
  only sign for the purpose it was issued for.

### Key purposes (`KeyPurpose`)

| Purpose | Used by | Notes |
|---|---|---|
| `GENERAL` | legacy / unscoped | auto-provisioned if absent (backward compatible) |
| `STEP_UP` | high-assurance auth artefacts | fail-closed |
| `OFFLINE_CAPABILITY` | tshepo-offline capability tokens | fail-closed |
| `OFFLINE_PACK` | signed offline data packs | fail-closed |
| `PERMIT` | data-access governance permits | fail-closed |
| `DOCUMENT_SIGNER` | GDHCN document-signer certs | fail-closed |
| `VDHC` | Verifiable Digital Health Certificates | fail-closed |
| `QR_SIGNING` | **VITO citizen QR tokens** (pickup/wallet/emergency/health-id) | W4b |
| `CARD_ASSERTION` | **SMART-card print-agent QR assertions** | W4b |

Sensitive purposes are **never silently minted** — `getActiveKeyForPurpose` throws if a key for
the purpose is not provisioned (`KEY_LOOKUP_FAILED_CLOSED` outbox event emitted).

---

## 2. Custody & signing modes (flags)

| Flag | Default | Alternatives | Effect |
|---|---|---|---|
| `impilo.keys.custody-provider` | `software` | `pkcs11` | where private keys live / how they sign |
| `impilo.keys.kek-source` | `config` | `vault` | where the software KEK comes from |
| `impilo.keys.pkcs11.config` | *(unset)* | path | SunPKCS11 config; unset ⇒ HSM provider inert |
| `vito.qr.signing-source` | `seed` | `tshepo-keys` | VITO QR: local seed vs sovereign key service |
| `card-print.qr.signing-source` | `seed` | `tshepo-keys` | card assertions: local seed vs sovereign |
| `vito.tshepo-keys.base-url` | `http://tshepo-keys-service:8081` | — | VITO → tshepo-keys endpoint |
| `card-print.tshepo-keys.base-url` | `http://tshepo-keys-service:8081` | — | card-print → tshepo-keys endpoint |

**Migration order (per environment):** provision `QR_SIGNING` / `CARD_ASSERTION` keys in
tshepo-keys → flip `*.qr.signing-source=tshepo-keys` on VITO / card-print → confirm new QRs/cards
carry tshepo-keys kids and verify via JWKS → keep the seed path available for one release as
fallback → remove seed config in a later release.

---

## 3. Key generation ceremony

### 3.1 Software custody (default)
1. Confirm the KEK is present and exactly 32 bytes (AES-256): sourced by `ConfigKekProvider`
   from `tshepo.keys.kek` (hex), injected from the environment/secret manager — never committed.
2. Generate the purpose-scoped key (admin API / `KeyManagementController`), recording tenant +
   `KeyPurpose`. Ed25519 keypair is minted in-process; the private key is AES-256-GCM-encrypted
   under the KEK and stored as `[12-byte IV | ciphertext | GCM tag]`.
3. Verify: the new key appears ACTIVE, its public key is published in the JWKS, and a
   `KEY_GENERATED` outbox event was emitted.

### 3.2 HSM custody (non-exportable) — `pkcs11`
1. Install the vendor PKCS#11 module and provide a SunPKCS11 config at
   `impilo.keys.pkcs11.config`; set `impilo.keys.custody-provider=pkcs11`.
2. Generate keys **inside the HSM** under dual control (see §4). Private keys are
   **non-exportable**: `Pkcs11KeyCustodyProvider.exportPrivate()` always throws, and
   `supportsExport()` returns `false`.
3. All signing uses the module: raw signatures via `KeyCustodyProvider.sign`, and **JWS is
   assembled from a custody sign over the JWS signing input** (`Ed25519SigningService.signJwsWithKey`
   takes the custody-sign path when export is forbidden) — so JWS works on an HSM **without ever
   exporting the private key**.

> Until a real module is wired, the PKCS#11 provider is **inert**: it fails closed on
> generate/sign (it will not fall back to software keys). This is intentional — no unaudited
> downgrade of custody.

---

## 4. Dual control

- Key generation, rotation, and revocation are **two-person** operations: one operator initiates,
  a second authorises. Record both actors; every action emits an auditable outbox event
  (`KEY_GENERATED`, `KEY_ROTATED`, `KEY_REVOKED`) with `rotatedBy` / `revokedBy`.
- HSM ceremonies additionally require the vendor's M-of-N quorum (smart cards / PED keys) for
  partition login. Never store all quorum factors together.
- The KEK (software custody) is split/escrowed so no single person can reconstruct it.

---

## 5. Rotation & K1↔K2 overlap

**Why overlap matters:** a card or QR is signed under a kid (K1). If K1 disappears at rotation,
every already-issued credential breaks. Overlap keeps the superseded key **verify-only** until it
expires, so issued cards/QRs survive to their expiry.

- **Rotation** (`KeyRotationService.rotateKey`, manual or scheduled) marks the current key
  `ROTATED` (keeps `expiresAt`) and generates a new `ACTIVE` key (K2). New credentials sign under
  K2; the K2 kid appears in new tokens.
- **JWKS publishes the overlap set** — `GET /v1/keys/jwks` returns `ACTIVE` **and**
  `ROTATED-but-unexpired` keys (`SigningKeyRepository.findAllVerificationKeys`). So a K1-signed
  credential still resolves and verifies during the window. `REVOKED` and expired keys are
  excluded immediately.
- **Verification is status-agnostic by kid**: `Ed25519SigningService.verifyJws` resolves the key
  by kid regardless of `ACTIVE`/`ROTATED`, so tshepo-keys itself keeps verifying K1 credentials.
- **VITO seed path** keeps its own overlap: `vito.qr.signing-key-seed-previous` retains the old
  seed's key verify-only. In `tshepo-keys` mode the JWKS overlap set replaces this.

**Rotation intervals**
- Default automatic rotation: **90 days** (`tshepo.keys.rotation-interval-days`), scheduler runs
  daily at 02:00 UTC.
- Overlap window = remaining life of the ROTATED key (its `expiresAt`); size credential TTLs so
  they expire within the overlap window.

---

## 6. Backup & recovery

- **Software keys:** covered by the standard Postgres backup (encrypted private keys) **plus** the
  KEK escrow. Recovery requires both the database rows and the KEK — back them up separately and
  with independent access control. Restoring the DB without the KEK yields undecryptable keys (by
  design).
- **HSM keys:** back up per vendor procedure (wrapped key backup to a paired HSM or M-of-N backup
  tokens). Private material never leaves the module in the clear.
- **JWKS is derivable** from the key rows (public keys only) — no separate backup needed.
- Test recovery on a non-production estate before relying on it.

---

## 7. Compromise / revocation response

1. **Revoke** the affected key(s): `KeyRotationService.revokeKey` sets status `REVOKED` and emits
   `KEY_REVOKED`. A `REVOKED` key drops out of the JWKS immediately — all its credentials stop
   verifying at once (no overlap for revoked keys).
2. **Rotate** to a fresh key so signing continues.
3. If the **KEK** is suspected compromised: treat all software private keys as exposed — rotate
   every software key, re-key the KEK, and re-encrypt. Prefer migrating to HSM custody.
4. If an **HSM** is compromised: follow vendor incident procedure; zeroize and re-provision.
5. Audit the outbox event chain (`KEY_GENERATED`/`KEY_ROTATED`/`KEY_REVOKED`/
   `KEY_LOOKUP_FAILED_CLOSED`) for the blast radius.

---

## 8. JWKS publication

- Endpoint: `GET /v1/keys/jwks` (unauthenticated, RFC 7517), cached in Redis (~60s).
- Contents: OKP/Ed25519 **public** keys only (`use:sig`); private material never appears.
- Set membership: `ACTIVE` + `ROTATED-and-unexpired` (overlap); excludes `REVOKED`/expired.
- Consumers: VITO (`vito.qr.signing-source=tshepo-keys`) and card-print
  (`card-print.qr.signing-source=tshepo-keys`) resolve verifiers by the kid carried in each
  token; any external verifier can do the same.
- Cache invalidation: `JwksService.invalidateCache()` is called on rotation/generation so the
  published set reflects the current overlap window promptly.

---

## 9. Proof / tests

- `KeyRotationOverlapTest` — credential signed under K1 still verifies after rotating to K2;
  JWKS publishes both kids; a revoked key drops out immediately.
- `HsmJwsAssemblyTest` — JWS assembled via custody-sign (export forbidden) is a valid, verifiable
  EdDSA JWS and `exportPrivate` is never called (JWS-on-HSM).
- `Pkcs11KeyCustodyProviderTest` — export forbidden, `supportsExport()==false`, inert without a
  module (fails closed).
- `KekProviderTest` — config KEK provider yields the 32-byte KEK; Vault provider throws until wired.
- `QrAssertionServiceTshepoKeysTest` (card-print) — end-to-end signing via a stub tshepo-keys
  `POST /v1/sign` + `GET /v1/keys/jwks`, verified against the JWKS-published public key.
