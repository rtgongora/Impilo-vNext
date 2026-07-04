-- Queue-level escalation: who escalated a queue item, when, and why.
-- Escalation bumps priority (floor 5 on the 1-5 triage-derived scale) and
-- optionally transfers the item to a target queue; these columns make the
-- escalated state visible to staff and auditable.

ALTER TABLE pct_queue_items ADD COLUMN escalated_at TIMESTAMPTZ;
ALTER TABLE pct_queue_items ADD COLUMN escalated_by VARCHAR(128);
ALTER TABLE pct_queue_items ADD COLUMN escalation_reason TEXT;
