# Plan: public-internet WebRTC media for LiveKit telemedicine

Enables real off-network telemedicine media on `impilo.mohcc.gov.zw`. Signaling is
already secured (`wss://…/rtc` via Traefik). This covers the **media** path, which
does NOT flow through Traefik and needs ICE/TURN + firewall work.

> **Status 2026-07-20 — DIRECT-MEDIA path LIVE; TURN-relay SNI solution IMPLEMENTED,
> gated on 2 external items (DNS + cert SAN).**
>
> Background (why the plain 5349 plan failed): LiveKit v1.13.3 HARDCODES the
> client-advertised TURN URL to `turns:<turn.domain>:443?transport=tcp` — NOT
> `:5349` (a 3-browser `rtc-media-diagnostic` showed every client handed
> `turns:impilo.mohcc.gov.zw:443`; confirmed by LiveKit docs "if not using a load
> balancer, `turn.tls_port` needs to be 443, as that is the port advertised" and
> livekit/livekit#3595). Node `:443` is Traefik, so opening pfSense `5349` can NEVER
> carry relay — **do NOT request a `5349` firewall rule.**
>
> **Solution (this change):** a DEDICATED SNI `turn.impilo.mohcc.gov.zw` that Traefik
> TCP-routes on `HostSNI(...)` with **TLS passthrough** → `livekit:5349`. LiveKit
> keeps terminating the TLS (external_tls:false). Relay then rides the already-open
> public `:443` — **no new pfSense port**.
>   `turns:turn.impilo.mohcc.gov.zw:443 → 41.57.127.235:443 → Traefik(websecure)
>    → HostSNI passthrough → livekit:5349 → LiveKit TURN`
>
> **Landed in repo + live (DNS-independent, inert until DNS/cert):**
> - `turn.domain: turn.impilo.mohcc.gov.zw` (livekit-config).
> - `livekit` Service exposes `5349/TCP` (`turn-tls`).
> - `IngressRouteTCP impilo-mohcc-gov-livekit-turn` (HostSNI passthrough → livekit:5349).
> - Verified live: SNI handshake to `:443` with `-servername turn.impilo.mohcc.gov.zw`
>   reaches the LiveKit TURN listener (passthrough plumbing proven); existing HTTPS
>   + `/rtc` unregressed; direct media still works.
>
> **⛔ Remaining external prerequisites (this VM cannot do these):**
> 1. **DNS** — `turn.impilo.mohcc.gov.zw. A 41.57.127.235` (TTL 300). Authoritative
>    zone is `ns1.gta.gov.zw` / `ns.gta.gov.zw` (GTA/ZCHPC), not this VM.
> 2. **Cert SAN** — add `turn.impilo.mohcc.gov.zw` to the existing
>    `impilo-mohcc-gov-zw-tls` cert (multi-SAN, HTTP-01, blocked on #1):
>    `certbot certonly --webroot -w /var/www/letsencrypt --cert-name impilo.mohcc.gov.zw
>    -d impilo.mohcc.gov.zw -d turn.impilo.mohcc.gov.zw`
>    (the `renew_hook` re-syncs the secret and restarts LiveKit on cert change).
>
> Once both land: relay works with no further code change. Until then a UDP-hostile
> off-network client still cannot relay; direct media (7881/7882 to 41.57.127.235)
> is the working off-network path.

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

Inbound forwarding needed to the node public IP `41.57.127.235` for the current
**direct-media** posture:

| Port | Proto | Purpose | State |
|------|-------|---------|-------|
| 443  | tcp | HTTPS/WSS signaling (Traefik) | already open — do not touch |
| 7881 | tcp | LiveKit ICE/TCP (direct media) | already open — keep |
| 7882 | udp | LiveKit ICE/UDP (direct media mux) | already open — keep |

Authoritative port posture (do not deviate):

- `7881/tcp` = ICE/TCP (direct).
- `7882/udp` = direct ICE/UDP mux.
- **`5349/tcp` = do NOT request.** The TURN server listens here internally, but
  LiveKit advertises TURN to clients on `:443` (hardcoded — see the ⚠️ status
  note above), so an external `5349` rule cannot carry any relay traffic.
- **`5349/udp` = NOT configured** — no `turn.udp_port` in the config.
- **`3478/udp` = NOT configured** — no plain-UDP TURN listener.

Notes:

- No new pfSense/firewall rule is required for the direct-media posture — the
  existing `7881/tcp` + `7882/udp` forwards already carry it.
- Confirm the `41.57.127.235` ↔ `10.50.1.67` 1:1 NAT is static.
- Relay (for UDP-hostile client networks) is deferred to the SNI fix in the status
  note; that fix carries relay over the already-open `443`, still no new port.

## Step 4 — DNS

`turn.domain` reuses `impilo.mohcc.gov.zw` (already resolves; cert already valid) —
no new record needed. Only add a `turn.` subdomain if TURN is later split to its own
host (would need a cert SAN too).

## Verification

Direct-media posture (what to test now, needs a real OFF-network client — not
possible from the VM):

1. Pod healthy; `livekit` log shows `nodeIP 41.57.127.235` + `using external IPs …
   advertiseInternalIP:true`. ✅ done 2026-07-20.
2. From an off-network browser on a network that permits outbound UDP/TCP, start a
   telemedicine session on `https://impilo.mohcc.gov.zw`; confirm two-way A/V over a
   **direct** candidate to `41.57.127.235:7882/udp` (or `7881/tcp`).
3. Re-test an on-LAN client — no regression. ✅ done 2026-07-20 (video 2.1 MB /
   467 frames decoded).

Relay (only after the SNI fix in the status note lands): in
`chrome://webrtc-internals` confirm the client is offered
`turns:turn.impilo.mohcc.gov.zw:443` and a `relay` pair is selected when direct
fails. Until then, a UDP-hostile client will NOT connect (no working relay).

## Rollback

- Media: revert `livekit-config` `rtc`/`turn` blocks + remove the cert mount;
  `kubectl rollout restart deploy/livekit`.
- Signaling and the cert are independent and stay in place.
