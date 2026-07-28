-- W15 discovered and closes a real integration gap: nothing that registers an ED walk-in/ambulance
-- arrival ever mints an emergency_episode. InFacilityDeteriorationConsumer (W6b) and MciBulkMintService
-- (W11) both call EmergencyEpisodeService.open(); EdVisitService — the actual ED registration path,
-- and the dominant real-world emergency entry route — never has. The entire episode spine built across
-- W2-W13 (the six safety alerts, the 15-disposition mandatory-content gate, the acceptance handshake,
-- the command view, the identity-link audit trail) is consequently invisible for an ordinary ED
-- walk-in. This is the same "backend built, never wired" failure class this pack has closed twice
-- already (W0's X-Trauma-Episode-ID header drop, W10's totally unreachable EmergencyEpisodeController)
-- — found and fixed in the same wave, not deferred.
--
-- Additive, nullable: existing ed_visit rows are unaffected. EdVisitService.openVisit now mints the
-- episode synchronously and stamps this column; a pre-existing visit with no episode is a fact about
-- when it was created, not an error state.
ALTER TABLE ed_visit ADD COLUMN emergency_episode_id UUID REFERENCES emergency_episode(episode_id);

CREATE INDEX idx_ed_visit_emergency_episode ON ed_visit (emergency_episode_id) WHERE emergency_episode_id IS NOT NULL;

COMMENT ON COLUMN ed_visit.emergency_episode_id IS
    'The pct.emergency_episode minted alongside this visit at registration (entry_route=WALK_IN or '
    'AMBULANCE, entrySourceRef=visit_id, idempotent). NULL for visits registered before this wave.';
