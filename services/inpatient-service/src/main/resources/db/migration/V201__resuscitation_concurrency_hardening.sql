-- Resus concurrency hardening (emergency pack W6b), continuing the trauma lane's exclusive
-- resuscitation_* class from V200. Two changes, both load-bearing:
--
-- 1. resuscitation_event.client_event_id — the idempotent-retry protection every other write-once
--    clinical record in this pack already carries (resuscitation_medication V200, emergency_medication_
--    admin V207). A device documenting mid-code and retrying blind on a dropped ack must not double-
--    count a CPR cycle or a defibrillation — and once V201's derivation (below) makes those counts a
--    pure function of the event log, a duplicate event would corrupt every read, not just create a
--    harmless extra row.
--
-- 2. resuscitation_record header hardening + a real optimistic lock. Six new header scalars
--    (team_leader, current_rhythm, airway_status, access_status, outcome, stand_down_reason) get
--    record_version for JPA @Version / If-Match semantics — a 409 on a real concurrent header edit,
--    never a silent overwrite. cpr_cycles/defibrillations/rosc_achieved/initial_rhythm/final_rhythm
--    are UNCHANGED as columns (still present, still read), but as of this wave the APPLICATION layer
--    stops accepting them as caller-supplied overwrites (InpatientClinicalService.recordResuscitation)
--    and instead derives them by recomputing from resuscitation_event on every write — a pure function
--    of the append-only event log, so two concurrent writers recording DIFFERENT events converge to
--    the same answer on the next read rather than racing to stomp a shared counter. This is the fix
--    for the exact hazard the design doc names: "two writers racing a counter is the actual lost-
--    update source and it is invisible in the current schema."
--
-- Deliberately NOT in this migration: the event_time/entered_by four-clocks-two-people split the
-- design doc also calls for. recorded_at/actor_id already exist and are load-bearing; splitting them
-- from a true clinical event_time and a distinct entered_by is a real enhancement but not required for
-- THIS wave's concurrency-safety goal, and is deferred rather than bundled in to keep this migration's
-- blast radius to what it actually fixes.

ALTER TABLE inpatient.resuscitation_event ADD COLUMN client_event_id VARCHAR(128);

CREATE UNIQUE INDEX uq_resuscitation_event_client_id
    ON inpatient.resuscitation_event (tenant_id, activation_id, client_event_id)
    WHERE client_event_id IS NOT NULL;

COMMENT ON COLUMN inpatient.resuscitation_event.client_event_id IS
    'Idempotent-retry token, caller-minted. A device retrying a blind write after a dropped ack must '
    'never double-count a CPR cycle or defibrillation once the summary is derived from this table.';

ALTER TABLE inpatient.resuscitation_record
    ADD COLUMN team_leader VARCHAR(128),
    ADD COLUMN current_rhythm VARCHAR(50),
    ADD COLUMN airway_status VARCHAR(50),
    ADD COLUMN access_status VARCHAR(50),
    ADD COLUMN outcome VARCHAR(50),
    ADD COLUMN stand_down_reason TEXT,
    ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;

-- Declared, not enforced against history: this is a brand-new column on an existing table, so a
-- NOT VALID CHECK costs nothing to add and documents intent without scanning/blocking existing rows —
-- same discipline as V200's chk_ea_protocol_type on this same table family.
ALTER TABLE inpatient.resuscitation_record
    ADD CONSTRAINT chk_rr_outcome CHECK (outcome IS NULL OR outcome IN (
        'ROSC_SUSTAINED', 'ROSC_LOST_AGAIN', 'DIED', 'STOOD_DOWN_FUTILE',
        'TRANSFERRED_TO_THEATRE', 'TRANSFERRED_OTHER')) NOT VALID;

COMMENT ON COLUMN inpatient.resuscitation_record.record_version IS
    'JPA @Version — the header-only optimistic lock (team_leader, current_rhythm, airway_status, '
    'access_status, outcome, stand_down_reason). A 409 on a real concurrent edit; the time-series in '
    'resuscitation_event stays append-only and never locked — a lost keystroke during CPR is worse '
    'than an occasional 409 on the header.';
COMMENT ON COLUMN inpatient.resuscitation_record.cpr_cycles IS
    'DERIVED since W6b: recomputed from resuscitation_event (channel=CPR, code=CYCLE) on every event '
    'write, never client-set directly. See InpatientClinicalService.recomputeResuscitationSummary.';
COMMENT ON COLUMN inpatient.resuscitation_record.defibrillations IS
    'DERIVED since W6b: recomputed from resuscitation_event (channel=CPR, code=DEFIBRILLATION). '
    'See InpatientClinicalService.recomputeResuscitationSummary.';
COMMENT ON COLUMN inpatient.resuscitation_record.rosc_achieved IS
    'DERIVED since W6b: recomputed from the latest resuscitation_event (channel=RHYTHM, code=ROSC). '
    'See InpatientClinicalService.recomputeResuscitationSummary.';
