-- Mother↔child birth dyad: make the relationship edge idempotent so a replayed birth event cannot
-- record it twice.
--
-- WHY. The mother↔child parentage relationship is established by PctBirthRelationshipConsumer from
-- pct's newborn.episode.opened event. Kafka delivery is at-least-once, and the newborn record can be
-- enriched-and-re-emitted (pct.newborn.record.updated rides the same topic), so the consumer can see
-- the same birth more than once. Without a uniqueness guard each redelivery would append another
-- identical MOTHER_OF edge, and a child would accumulate a dozen "born to the same mother" rows.
--
-- WHAT. A partial unique index over the ACTIVE edge: one active relationship of a given type between
-- an ordered (client, related_client) pair per tenant. Partial on status='ACTIVE' so that ending a
-- relationship (status flips) and later re-establishing it is still possible — the guard is against
-- duplicate *live* edges, not against history. This is the vito V055 slot the RMNP lease reserved
-- for dyad idempotency; it is additive and touches no existing row.
--
-- The relationship types themselves are a free VARCHAR(64) with no CHECK (see V022), mapped
-- @Enumerated(STRING) in Java, so BIRTH_MOTHER_OF / BORN_TO need no schema change — only this guard.

CREATE UNIQUE INDEX IF NOT EXISTS uq_client_relationship_active_edge
    ON vito.client_relationship (tenant_id, client_health_id, related_client_health_id, relationship_type)
    WHERE status = 'ACTIVE';

COMMENT ON INDEX vito.uq_client_relationship_active_edge IS
    'One active relationship of a given type per ordered (client, related_client) pair per tenant. Makes the mother↔child birth dyad idempotent under at-least-once event redelivery; partial on ACTIVE so ended relationships may be re-established.';
