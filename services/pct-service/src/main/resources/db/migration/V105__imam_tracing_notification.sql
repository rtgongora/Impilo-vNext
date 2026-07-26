-- Defaulter tracing must not depend on a human recording the absence.
--
-- V104 emits IMAM_TRACING_REQUIRED when a review is recorded as missed. Proving the vertical on
-- the estate exposed the hole in that: a child who simply stops coming, and for whom nobody
-- creates the missed-visit row, produces no event at all. The pull-based worklist finds them, but
-- nothing pushes — and the failure mode of this whole feature is a child nobody notices has
-- stopped attending. A trigger that only fires when someone notices is half a trigger.
--
-- A scheduled sweep now raises the event from elapsed time. This column makes the sweep
-- idempotent: it records the missed-visit count already notified, so the event fires once when the
-- child crosses into tracing and once more when they cross into defaulting — the second crossing
-- is new information, a daily repeat of the first is noise that teaches people to ignore it.

ALTER TABLE pct.pct_imam_episodes
    ADD COLUMN tracing_notified_missed_visits INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN tracing_notified_at TIMESTAMPTZ;
