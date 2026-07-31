# W19 — Realtime phase 2

## What landed

| Piece | Location |
|---|---|
| Shared gateway core | `libs/realtime-gateway` (`RealtimeHub`, `RealtimeEvent`, `RealtimeSubscription`, `RealtimeInstance`, `SubscriptionChannelResolver` interface, `InvalidationHint`, `RealtimeCoreConfiguration`) |
| Reactor + depMgmt | `services/pom.xml` module + BOM entry |
| Khuluma membership resolver | `KhulumaChannelResolver` implements the interface (JPA stays in khuluma) |
| PCT membership resolver | `EmergencyChannelResolver` — admits `episode:{uuid}` only in-tenant |
| PCT publish (env-gated) | `EmergencyRealtimePublisher` behind `pct.emergency.realtime-enabled` (default **false**) |
| UI transport | `ui/one-ui-shell/src/lib/realtime/realtime-transport.ts`; `khuluma-socket.ts` re-exports |

## Env gates

| Layer | Gate | Default |
|---|---|---|
| PCT publish | `PCT_EMERGENCY_REALTIME_ENABLED` / `pct.emergency.realtime-enabled` | `false` |
| PCT Redis fan-out | `pct.emergency.realtime-redis-enabled` | `true` (only when publish enabled) |
| UI | `NEXT_PUBLIC_REALTIME_WS` / `_SSE`, falling back to `NEXT_PUBLIC_KHULUMA_*` | unset → no-op |

## Frame shape (invalidation only)

```json
{ "event_type": "…", "channel": "episode:<uuid>|tenant:<uuid>", "episode_id": "<uuid>", "revision": 1 }
```

No PHI / clinical payload. Kafka outbox remains the system of record for peers.

## Browser path (bridge)

PCT has no WebSocket host. When `pct.emergency.realtime-enabled=true`, PCT publishes to Redis topic
`pct:emergency:realtime` (episode + tenant channels). Khuluma's `RealtimeRedisConfig` also
subscribes to that topic and fans into its local hub, so existing
`NEXT_PUBLIC_KHULUMA_WS` / `NEXT_PUBLIC_REALTIME_WS` clients receive hints.

UI: `useEmergencyRealtime` on command, episode board, and spine pages invalidates react-query keys.

## Left in khuluma (transport / auth)

`WebSocketConfig`, `KhulumaWebSocketHandler`, `RealtimeHandshakeInterceptor`, `WsTokenValidator`, `RealtimeStreamController`, `RedisRealtimeDispatcher`, `RealtimeRedisConfig` — wire paths and JWT stay service-owned; they now import the shared hub types.
