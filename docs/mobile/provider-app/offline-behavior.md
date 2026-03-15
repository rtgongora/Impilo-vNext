# Provider App Offline Behavior

This document describes the offline architecture, synchronization mechanisms, and data integrity guarantees of the Provider App.

## Architecture

The offline subsystem is implemented in the `@impilo/mobile-offline` shared package. It provides a **local-first architecture** where all write operations are captured locally before being transmitted to the server. The sync engine uses **CRDT (Conflict-free Replicated Data Type)** structures to enable deterministic merge resolution for concurrent edits.

Key architectural components:

| Component | Responsibility |
|---|---|
| `SyncEngine` | Orchestrates background sync, retry scheduling, and batch transmission |
| `LocalStore` | CRDT-backed on-device data store with change tracking |
| `ConflictResolver` | Field-level diff computation and merge strategy execution |
| `SnapshotManager` | Edge snapshot download, storage, and reconciliation |
| `NetworkMonitor` | Connectivity detection and status broadcasting |
| `BreakGlassManager` | Emergency access activation, audit trail generation |

## Queue Management

Every write operation performed while offline (or while online, as a reliability measure) is captured as a `SyncQueueItem`.

### SyncQueueItem Structure

| Field | Description |
|---|---|
| `id` | Unique identifier for the queue entry |
| `idempotencyKey` | Client-generated key to prevent duplicate processing on the server |
| `operation` | The HTTP method and endpoint to invoke |
| `payload` | Serialized request body |
| `status` | Current sync status (see below) |
| `createdAt` | Timestamp of local creation |
| `attempts` | Number of sync attempts made |
| `lastError` | Most recent error message, if any |

### Queue Statuses

| Status | Description |
|---|---|
| `PENDING` | Queued locally, awaiting sync |
| `SYNCING` | Currently being transmitted to the server |
| `SYNCED` | Successfully acknowledged by the server |
| `FAILED` | Sync attempted and failed; eligible for retry |
| `CONFLICT` | Server rejected due to conflicting state; requires resolution |

### Retry Logic

Failed items are retried with exponential backoff. The retry schedule is:

1. First retry: 5 seconds
2. Second retry: 30 seconds
3. Third retry: 2 minutes
4. Fourth retry: 10 minutes
5. Subsequent retries: 30-minute intervals

Items that remain in `FAILED` status after 10 attempts are surfaced to the user in the Offline Edge mode Queue tab for manual intervention.

## Conflict Resolution

When the server detects that a record has been modified since the client's last known version, it returns a conflict response. The sync engine transitions the queue item to `CONFLICT` status and creates a `ConflictItem` for user review.

### ConflictItem Structure

| Field | Description |
|---|---|
| `id` | Unique conflict identifier |
| `queueItemId` | Reference to the originating sync queue item |
| `resourceType` | The type of resource in conflict (e.g., `Encounter`, `Screening`) |
| `resourceId` | The identifier of the conflicting resource |
| `localVersion` | The client's version of the resource |
| `serverVersion` | The server's current version of the resource |
| `fieldDiffs` | Array of field-level differences between local and server versions |
| `resolvedAt` | Timestamp of resolution, if resolved |

### Resolution Strategies

| Strategy | Behavior |
|---|---|
| `LOCAL_WINS` | The client's version overwrites the server state. The server version is discarded. |
| `SERVER_WINS` | The server's version is accepted. The local changes are discarded. |
| `MANUAL_MERGE` | The user selects individual field values from both versions to construct a merged result. |

The Conflict Detail screen in Offline Edge mode presents a side-by-side comparison of local and server values for each conflicting field. The user selects a resolution strategy and confirms the merge before the resolved item is re-queued for sync.

## Edge Snapshots

Edge snapshots are pre-computed data bundles that can be downloaded to the device for extended offline operation. They are used primarily in Outreach mode, where community health workers may operate without connectivity for hours or days.

### Snapshot Lifecycle

1. **Download** — The device requests a snapshot for its assigned catchment area or facility. The BFF compiles the relevant patient demographics, visit schedules, and reference data into a compressed bundle.
2. **Storage** — The snapshot is stored locally and indexed for efficient lookup by the `LocalStore`.
3. **Operation** — The app reads from the snapshot for all read operations while offline. Write operations are queued as `SyncQueueItem` entries.
4. **Reconciliation** — When connectivity is restored, the sync engine transmits all queued writes and then requests a delta update to refresh the local snapshot with any server-side changes that occurred during the offline period.

## Entitlement Verification

The app supports **offline-capable CPID (Clinical Patient Identifier) entitlement checks**. When online, entitlement status is verified in real time against the PCT service. When offline, the most recently cached entitlement data is used, with a staleness indicator shown to the user.

Cached entitlement data includes:

- CPID validity and status
- Active coverage periods
- Benefit category eligibility
- Last verification timestamp

## Break-Glass Emergency Access

Break-glass provides time-limited emergency access to patient data when the device is offline and the requested data falls outside the user's cached scope.

### Activation Flow

1. The user initiates break-glass from the Emergency tab in Offline Edge mode.
2. The app displays a confirmation dialog explaining the audit implications.
3. The user provides a clinical justification (free text, minimum 20 characters).
4. The app grants elevated read access to locally cached data for a configurable duration (default: 60 minutes).
5. A detailed audit record is created locally, capturing the user identity, justification, timestamp, and all records accessed.
6. When connectivity is restored, the audit record is transmitted to TSHEPO as a priority sync item before any other queued operations.

### Audit Trail

Every break-glass activation generates an audit entry containing:

| Field | Description |
|---|---|
| `userId` | The authenticated user who activated break-glass |
| `facilityId` | The facility context at the time of activation |
| `justification` | The user-provided clinical justification |
| `activatedAt` | Timestamp of activation |
| `expiresAt` | Timestamp of access expiry |
| `accessedRecords` | List of CPIDs and resource types accessed during the session |
| `deactivatedAt` | Timestamp of deactivation (manual or automatic expiry) |

## Network Detection

The `NetworkMonitor` component listens for online and offline events from the platform network APIs. It maintains a reactive state that is consumed by:

- **`NetworkStatusBar`** — A persistent UI indicator showing the current connectivity state. Displays green when online, amber when syncing, and red when offline.
- **`SyncEngine`** — Triggers immediate sync attempts when the device transitions from offline to online.
- **Mode-specific logic** — Features that require connectivity display appropriate disabled states and informational messages.

Network state transitions are debounced to avoid false positives from transient connectivity fluctuations.

## Mode-Specific Offline Behavior

| Mode | Offline Capability | Details |
|---|---|---|
| Provider | Limited | Patient demographics and recent encounters are cached. New encounters can be queued offline. Lab orders, referrals, and prescriptions require connectivity. |
| Outreach | Full | All workflows operate offline by design. Household visits, screenings, and immunizations are captured locally and batch-synced on reconnect. GPS coordinates are recorded offline. |
| Supervisor | Limited | Dashboard KPIs are cached for read-only viewing. Stock adjustments can be queued. Team management and escalation resolution require connectivity. |
| Offline Edge | Native | This mode is purpose-built for offline state management and is fully functional without connectivity. |

## Data Integrity

The offline architecture relies on two complementary patterns to guarantee data integrity:

### Outbox Pattern (Backend)

Every backend service writes domain events to an `event_outbox` table within the same database transaction as the state mutation. A separate publisher process reads from the outbox and publishes to Kafka. This ensures that no event is lost even if Kafka is temporarily unavailable.

### Idempotency Keys (Client)

Every `SyncQueueItem` carries a client-generated `idempotencyKey`. The BFF layer uses this key to deduplicate requests. If a sync transmission succeeds but the acknowledgment is lost (e.g., due to a network drop), the client will retry with the same idempotency key, and the server will return the original response without re-processing the operation.

Together, these patterns ensure **exactly-once semantics** for all write operations flowing from the device to the backend, regardless of network reliability.
