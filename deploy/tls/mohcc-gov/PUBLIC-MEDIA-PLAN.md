# Plan: public-internet WebRTC media for LiveKit telemedicine

Enables real off-network telemedicine media on `impilo.mohcc.gov.zw`. Signaling is
already secured (`wss://…/rtc` via Traefik). This covers the **media** path, which
does NOT flow through Traefik and needs ICE/TURN + firewall work.

> **Status 2026-07-20 — platform side IMPLEMENTED (Steps 2–4).** LiveKit now
> advertises the public IP + LAN candidates and runs embedded TURN-over-TLS on
> **5349/TCP** (cert mounted from `impilo-mohcc-gov-zw-tls`; `use_external_ip` +
> `advertise_internal_ip` + `skip_external_ip_validation`; TURN
> `allow_restricted_peer_cidrs: [10.50.1.67/32]`). The renewal hook restarts
> LiveKit only when the cert changes. **The one remaining external action is the
> ZCHPC pfSense rule for `5349/TCP` (Step 3).**

## Current state (2026-07-09)

- ✅ Signaling: `wss://impilo.mohcc.gov.zw/rtc` → Traefik → `livekit:7880` (TLS).
- ⚠️ Media: LiveKit `hostNetwork`, `rtc.tcp_port 7881` / `udp_port 7882`,
  `use_external_ip: false` → advertises the node's **private** iface `10.50.1.67`.
  Works only for on-LAN clients. Node is behind **1:1 NAT** (`10.50.1.67` ↔ public
  `41.57.127.235`); the router **hairpins/drops sustained UDP** for LAN/VM clients,
  so a public-only candidate would break internal users (team's documented warning
  in `livekit-config`).
- ⚠️ Secret: the placeholder LiveKit API secret was **rotated live** (strong random,
  consistent across `livekit-config`, `livekit-egress-config`, `rtc-gateway`). The
  new value lives **only in the cluster** — the repo templates still carry the
  placeholder, so a `helm sync` reverts it. **Step 1 fixes this.**

## Step 1 — Secret management (do FIRST; removes the revert-footgun)

> **Superseded / generalized:** LiveKit is one of a platform-wide family of
> committed placeholder secrets, so this is now tracked as an epic —
> `docs/security/secrets-management-migration-plan.md` (LiveKit = its P1/P2).
> The LiveKit-specific target below still holds; do it as part of that epic.

Move the LiveKit API secret out of ConfigMap/values plaintext into a k8s Secret.

- Create `Secret/livekit-api` (key `api-secret`) in `impilo-full-preview` holding the
  already-rotated value (read it from the live `livekit-config` — don't regenerate,
  or all three components must change together again).
- LiveKit server: provide the key via env `LIVEKIT_KEYS="impilo-preview-key: <secret>"`
  from `secretKeyRef` instead of the `keys:` block in `livekit-config`
  (templates/livekit.yaml + livekit-config.yaml).
- `rtc-gateway` + `livekit-egress`: `LIVEKIT_API_SECRET` / egress `api_secret` via
  `secretKeyRef` to the same Secret.
- Repo (`values-full-preview.yaml:146`, `generate-full-preview-runtime-values.mjs`,
  generated values): replace the literal with a non-secret placeholder + a comment
  that the real value is injected from `Secret/livekit-api`. **Never commit the real
  secret.**
- Also rotate the sibling placeholders spotted alongside it (egress S3
  `preview-minio-change-me`) before public exposure.

## Step 2 — TURN over TLS (media reachability + hairpin fix)

Use LiveKit's built-in TURN so relayed media traverses the NAT/hairpin for both
LAN and internet clients (the "TURN path" the team's note requires).

Add to `livekit.yaml` (`livekit-config` ConfigMap / templates/livekit-config.yaml):

```yaml
rtc:
  tcp_port: 7881
  udp_port: 7882
  use_external_ip: true              # STUN-discover + advertise 41.57.127.235
  advertise_internal_ip: true        # keep LAN candidates for on-site clients
  skip_external_ip_validation: true  # 1:1 NAT has no hairpin self-ping; don't drop the public IP
  # NOTE: nat_1to1_ip is NOT a valid key in livekit-server v1.13.3 (absent from
  # the binary) — use use_external_ip + skip_external_ip_validation instead.
turn:
  enabled: true
  domain: impilo.mohcc.gov.zw        # must match the mounted cert's CN/SAN
  tls_port: 5349                     # TURN/TLS on TCP (looks like https; firewall-friendly)
  external_tls: false                # LiveKit terminates TURN-TLS with the mounted cert
  cert_file: /etc/livekit/tls/tls.crt
  key_file:  /etc/livekit/tls/tls.key
  ttl_seconds: 300
  # LiveKit denies TURN relay to private peer IPs by default; the host-network
  # SFU is at 10.50.1.67, so whitelist ONLY that /32 (nothing broader).
  allow_restricted_peer_cidrs:
    - 10.50.1.67/32
  # DO NOT set turn.udp_port — this slice is TURN over TLS on TCP 5349 only.
```

- **Mount the cert** into the LiveKit pod from the existing `impilo-mohcc-gov-zw-tls`
  secret at `/etc/livekit/tls` (templates/livekit.yaml volume + volumeMount, RO,
  `defaultMode: 0400`). ✅ DONE.
- **Renewal reload**: LiveKit doesn't hot-reload TLS.
  `scripts/tls/sync-mohcc-gov-tls.sh` now fingerprints the leaf cert and, only
  when it changed, runs `kubectl rollout restart deployment/livekit` +
  `rollout status` so renewed TURN certs take effect without bouncing live calls
  on unchanged runs. ✅ DONE.

## Step 3 — Firewall / NAT (OFF-BOX — network admin; cannot be done or verified from the VM)

Open inbound from the internet to the node public IP `41.57.127.235`:

| Port | Proto | Purpose |
|------|-------|---------|
| **5349** | **tcp only** | TURN over TLS (primary relay for off-LAN clients) |
| 7881 | tcp | LiveKit ICE/TCP (direct + fallback) |
| 7882 | udp | LiveKit ICE/UDP (direct media mux) |

Authoritative port posture (do not deviate):

- `5349/tcp` = TURN/TLS.
- `7881/tcp` = ICE/TCP.
- `7882/udp` = direct ICE/UDP mux.
- **`5349/udp` = NOT configured** — the deployed LiveKit config sets only
  `turn.tls_port` (TCP); there is no `turn.udp_port`. Opening 5349/udp would
  forward to nothing. Do NOT open it.
- **`3478/udp` = NOT configured** — no plain-UDP TURN listener in this slice.
  Do NOT open it.

Notes:

- 443/tcp (signaling) is already open via Traefik — do not touch.
- Confirm the `41.57.127.235` ↔ `10.50.1.67` 1:1 NAT is static.
- TURN/TLS on `5349/tcp` is the relay that traverses UDP-hostile client networks;
  it does not require any UDP port to be opened.
- The exact rule to request: `41.57.127.235:5349/TCP → 10.50.1.67:5349/TCP`.

## Step 4 — DNS

`turn.domain` reuses `impilo.mohcc.gov.zw` (already resolves; cert already valid) —
no new record needed. Only add a `turn.` subdomain if TURN is later split to its own
host (would need a cert SAN too).

## Verification (needs a real OFF-network client — not possible from the VM)

1. Pods healthy after config change; `livekit` logs show TURN listener on 5349.
2. From an off-network browser, start a telemedicine session on
   `https://impilo.mohcc.gov.zw`; confirm two-way audio/video.
3. In `chrome://webrtc-internals`, confirm a `relay` (TURN) candidate pair is
   selected when direct fails.
4. Re-test an on-LAN client to confirm the hairpin fix didn't regress them.

## Rollback

- Media: revert `livekit-config` `rtc`/`turn` blocks + remove the cert mount;
  `kubectl rollout restart deploy/livekit`.
- Signaling and the cert are independent and stay in place.
