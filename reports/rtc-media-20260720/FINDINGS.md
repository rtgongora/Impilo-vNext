# Telecalls / LiveKit — live browser call attempt, captured for engineers

**Date:** 2026-07-20 · **How:** real login (dr.mapfumo) → provisioned a TELEMEDICINE
session via rtc-gateway → **three real LiveKit browser peers** against
`wss://impilo.mohcc.gov.zw` (Traefik → livekit:7880). Repro: `scripts/e2e/rtc-media-diagnostic.mjs`.
Raw capture: [`capture.txt`](capture.txt) / [`capture.json`](capture.json); screenshots `peer-A/B/C.png`.

## What the attempt showed

| Peer | Who it simulates | Result |
|------|------------------|--------|
| **A** provider (HOST, publishes) | clinician on the estate | ✅ connected, published cam+mic |
| **B** patient, normal ICE | a device **on the hospital LAN** | ✅ connected, **decoded real video 2.1 MB / 467 frames + audio** |
| **C** patient, relay-only ICE | a **real off-network / firewalled clinic** browser | ❌ **`could not establish pc connection`** — ICE `closed/closed`, **no candidates**, no media |

The full app pipeline works: login, session provisioning, LiveKit signaling (wss via
Traefik), token mint, publish/subscribe, and the SFU all function — B proves media
actually flows on-LAN. **The break is purely WebRTC media reachability for anyone not
on the estate LAN.**

## The two root causes (from the capture, not theory)

1. **No TURN relay is configured.** Every peer is handed **STUN servers only**:
   ```
   ICE servers given:  stun:global.stun.twilio.com:3478, stun:stun.l.google.com:19302, stun:stun1.l.google.com:19302
   TURN configured:    NO    relay candidate present: NO
   ```
   STUN can only *discover* a public address; it cannot *relay* media. When a client
   can't use a direct path (UDP blocked, or symmetric NAT — both common on clinic /
   mobile networks), there is **no fallback**. Peer C reproduces exactly this: forced to
   need a relay, with no TURN it has zero candidates → `could not establish pc connection`.
   That is the literal error an off-network patient/provider gets today.

2. **The SFU's only reliably-usable candidates are private.** It advertises host
   candidates `10.42.x / 172.17.x / 10.50.1.67:7882` (cluster/docker/VM-LAN — unroutable
   from the internet). It *sometimes* also advertises server-reflexive `srflx
   41.57.127.235:<port>` (the public IP), **but on random high UDP ports (e.g. 23230,
   61501, 53277), not the fixed 7882** — the signature of a **symmetric NAT**. A remote
   peer cannot use symmetric-NAT srflx candidates; that case *requires* TURN.

## What engineers must do (unchanged from the plan; now evidence-backed)

Canonical steps: [`deploy/tls/mohcc-gov/PUBLIC-MEDIA-PLAN.md`](../../deploy/tls/mohcc-gov/PUBLIC-MEDIA-PLAN.md).

**A — Network admin (OFF-box; the hard blocker).** Open inbound to `41.57.127.235`:
`5349 tcp+udp` (TURN/TLS), `7881 tcp`, `7882 udp`. Confirm the `41.57.127.235 ↔ 10.50.1.67`
NAT is static 1:1. (443/tcp signaling is already open.) None of the below is verifiable
until this is done — and it cannot be done or tested from the VM.

**B — Platform/on-box (config).** Enable LiveKit's built-in **TURN over TLS on 5349** +
`use_external_ip: true` (pin `nat_1to1_ip: 41.57.127.235` if STUN can't see it), mount the
existing `impilo-mohcc-gov-zw-tls` cert into the LiveKit pod at `/etc/livekit/tls`, and add
`kubectl rollout restart deploy/livekit` to `scripts/tls/sync-mohcc-gov-tls.sh` for cert
renewals. Commit to `templates/livekit-config.yaml` + `templates/livekit.yaml` so a helm
sync doesn't revert it. Exact YAML is in the plan. **TURN is the decisive fix** — the
capture proves there is currently no relay of any kind.

> Sequencing: TURN (B) closes the gap for symmetric-NAT/UDP-blocked clients **and** the
> router's UDP-hairpin issue, so it helps LAN clients too. Land B together with A; keep
> TURN enabled so there is always a relay fallback.

## How to re-run this capture (engineers)
```
# provision a session (login → rtc-gateway) writing rtc-prov.json / rtc-tokB.json / rtc-tokC.json
# into a scratch dir, then:
node scripts/e2e/rtc-media-diagnostic.mjs <scratch-dir>
# → prints the per-peer table above + writes capture.txt/.json + peer-A/B/C.png
```
Peer C forces `iceTransportPolicy: relay` at the `RTCPeerConnection` level (LiveKit's SDK
otherwise resets it to `all`), which is what makes it behave like a genuinely off-network
client. Once TURN + firewall are in place, re-running C should connect via a `relay`
candidate instead of failing — that's the acceptance test (best confirmed from a real
off-network machine).

## Reference
Media reachability is the ONLY blocker. Chat (Khuluma text) is REST+polling, not LiveKit,
and works. Telemedicine app lifecycle (consent gate, waiting-room admit, FHIR write-back)
is deployed and works; only the video media path is gated on the TURN + firewall work above.
