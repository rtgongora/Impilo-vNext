# MADI Domain Model

> Canonical aggregates in `services/madi-service` schema `madi`.

## Entity groups

### Donor engagement

```
DonorProfile
  ├─ DonorEligibilityScreening
  ├─ DonorDeferral
  ├─ DonorCommunicationPreference
  └─ DonorFeedback
```

| Entity | Key fields | Notes |
|--------|------------|-------|
| `DonorProfile` | `donor_id`, `person_cpid`, `blood_group`, `rh_factor`, `status` | Anchored to VITO CPID — no PII duplicate |
| `DonorEligibilityScreening` | `result` (ELIGIBLE, DEFERRED_*), `drive_id` | Pre-donation gate |
| `DonorDeferral` | `reason_code`, `deferred_until` | Temporary or permanent |
| `DonorFeedback` | `rating`, `comments`, `drive_id` | Post-donation quality signal |

### Donation drives

```
DonationDrive
  ├─ DonationDriveAttendance
  ├─ DonorEligibilityScreening (at drive)
  └─ BloodCollection
```

Lifecycle: `DRAFT → PUBLISHED → OPEN → CLOSED`

### Blood units and processing

```
BloodCollection → BloodUnit → ProcessingEvent → ComponentUnit
BloodInventoryBalance (per facility / blood bank)
```

Component types align with `ComponentType` enum (e.g. packed red cells, platelets, plasma).

### Clinical order fulfilment

```
BloodOrder
  ├─ BloodOrderItem
  ├─ BloodSample
  ├─ CrossmatchResult
  ├─ BloodReservation
  └─ BloodIssue
```

Order status follows `BloodOrderStatus`: DRAFT → SUBMITTED → … → ISSUED / CANCELLED.

### Transfusion

```
TransfusionEpisode
  └─ TransfusionObservation
```

Episode status: `TransfusionStatus` — started, in progress, completed, verified.

### Haemovigilance

```
AdverseTransfusionReaction → HaemovigilanceCase
```

Case status: `HaemovigilanceCaseStatus` — OPEN → INVESTIGATING → CLOSED.

## Identifiers

| Class | MADI usage |
|-------|------------|
| Person CPID | Donor and patient anchor (no PII in MADI clinical tables) |
| Donor ID | MADI donor registry UUID |
| Drive ID | Donation drive instance |
| Order ID | Blood order |
| Episode ID | Transfusion episode |
| Bag / unit numbers | Physical traceability |

## Events (outbox)

All state transitions emit Kafka events via `event_outbox` (`MadiOutboxPublisher`, `MadiEventEmitter`). Event names follow `madi.*` prefix convention.
