# Plan: public-internet WebRTC media for LiveKit telemedicine

Enables real off-network telemedicine media on `impilo.mohcc.gov.zw`. Signaling is
already secured (`wss://…/rtc` via Traefik). This covers the **media** path, which
does NOT flow through Traefik and needs ICE/TURN + firewall work.

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
  use_external_ip: true          # STUN-discover + advertise 41.57.127.235
  # If STUN can't see the public IP behind 1:1 NAT, pin it explicitly:
  # nat_1to1_ip: "41.57.127.235"
turn:
  enabled: true
  domain: impilo.mohcc.gov.zw    # must match the mounted cert's CN/SAN
  tls_port: 5349                 # TURN/TLS (looks like https; firewall-friendly)
  external_tls: false            # LiveKit terminates TURN-TLS with the mounted cert
  cert_file: /etc/livekit/tls/tls.crt
  key_file:  /etc/livekit/tls/tls.key
```

- **Mount the cert** into the LiveKit pod from the existing `impilo-mohcc-gov-zw-tls`
  secret at `/etc/livekit/tls` (templates/livekit.yaml volume + volumeMount).
- **Renewal reload**: LiveKit doesn't hot-reload TLS. Extend
  `scripts/tls/sync-mohcc-gov-tls.sh` to also
  `kubectl rollout restart deploy/livekit -n impilo-full-preview` after updating the
  secret, so renewed TURN certs take effect.

## Step 3 — Firewall / NAT (OFF-BOX — network admin; cannot be done or verified from the VM)

Open inbound from the internet to the node public IP `41.57.127.235`:

| Port | Proto | Purpose |
|------|-------|---------|
| 5349 | tcp + udp | TURN over TLS (primary relay for off-LAN clients) |
| 7881 | tcp | LiveKit ICE/TCP (direct + fallback) |
| 7882 | udp | LiveKit ICE/UDP (direct media) |

- 443/tcp (signaling) is already open via Traefik.
- Confirm the `41.57.127.235` ↔ `10.50.1.67` 1:1 NAT is static.
- If UDP is unreliable through the upstream firewall, TURN/TLS on 5349/tcp is the
  fallback that almost always works.

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
