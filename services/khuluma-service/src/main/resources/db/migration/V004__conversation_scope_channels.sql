-- =============================================================================
-- khuluma-service V004 — Conversation scope for channels/communities/broadcast
-- (Phase 5 / W5, G-KH-02)
--
-- Facility/programme channels, client communities and broadcasts reuse the existing
-- conversation model (conversation_type already supports CHANNEL/BROADCAST) but need a
-- SCOPE so members can discover the channels for their facility/programme/community.
-- =============================================================================

ALTER TABLE khuluma_conversations
    ADD COLUMN scope_type VARCHAR(32),   -- FACILITY | PROGRAMME | COMMUNITY (null for direct/group)
    ADD COLUMN scope_ref  UUID;          -- the facility / programme / community id

-- Channel discovery: find the channels/communities for a given scope within a tenant.
CREATE INDEX idx_khuluma_conversation_scope
    ON khuluma_conversations (tenant_id, scope_type, scope_ref, status);
