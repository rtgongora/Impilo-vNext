-- Emergency care value sets (emergency pack W4c).
--
-- zibo held ZERO emergency terminology (the only "emergency" hit was EMERGENCY_MEDICINE as a
-- clinical specialty). These five code systems + value sets are the canonical terminology behind
-- vocabularies the emergency backend already enforces in CHECK constraints and engines, so the
-- terminology plane and the transactional truth agree rather than drift:
--   * triage priority        <- WhoIittEngine TriagePriority + the pct ed_triage_assessment CHECK
--   * triage tool            <- pct V202 chk_ed_triage_tool (WHO_IITT authoritative; ESI/MTS advisory)
--   * emergency entry route  <- pct V200 chk_ee_entry_route (14 routes)
--   * emergency episode class<- pct V200 chk_ee_episode_class (11 classes)
--   * emergency episode state<- pct V200 chk_ee_state (the FSM)
--
-- Each is a CODE_SYSTEM holding the concepts + a VALUE_SET composing it, both PUBLISHED so
-- POST /v1/validate/coding can validate immediately. Idempotent on (tenant, canonical_url, version).
-- Acuity/authority/phase are carried as typed CodeSystem properties so a consumer can read the
-- IITT->acuity mapping and the authoritative-vs-advisory distinction FROM the terminology.
--
-- NOTE ON PROVENANCE: triage priority is WHO/ICRC/MSF IITT (not locally adaptable); the entry-route,
-- episode-class and state vocabularies are Impilo operational vocabularies (this platform's own),
-- honestly published as such, not attributed to an external standard they do not come from.

-- 1. Triage priority (IITT) -------------------------------------------------
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES
('00000000-0000-0000-0000-000000000001', 'CODE_SYSTEM',
 'https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-priority', '1.0.0',
 'ImpiloEmergencyTriagePriority', 'Emergency triage priority (WHO IITT)',
 'WHO/ICRC/MSF Interagency Integrated Triage Tool priority tiers; the derived_acuity property is the documented 1-5 mapping. NOT_TRIAGEABLE has no acuity — it is a refusal, never a stored tier.',
 'PUBLISHED',
 $cs${
   "resourceType":"CodeSystem",
   "url":"https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-priority",
   "version":"1.0.0","status":"active","content":"complete",
   "property":[{"code":"derived_acuity","type":"integer","description":"Documented IITT tier to 1-5 acuity mapping"}],
   "concept":[
     {"code":"RED","display":"Red — immediate","property":[{"code":"derived_acuity","valueInteger":1}]},
     {"code":"YELLOW","display":"Yellow — urgent","property":[{"code":"derived_acuity","valueInteger":3}]},
     {"code":"GREEN","display":"Green — non-urgent","property":[{"code":"derived_acuity","valueInteger":5}]},
     {"code":"NOT_TRIAGEABLE","display":"Not triageable — insufficient assessment to triage"}
   ]
 }$cs$,
 md5('emergency-triage-priority-1.0.0'),
 'MoHCC / WHO IITT', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

('00000000-0000-0000-0000-000000000001', 'VALUE_SET',
 'https://impilo.gov.zw/fhir/ValueSet/emergency-triage-priority', '1.0.0',
 'ImpiloEmergencyTriagePriorityVS', 'Emergency triage priority value set',
 'All IITT triage priorities.', 'PUBLISHED',
 $cs${"resourceType":"ValueSet","url":"https://impilo.gov.zw/fhir/ValueSet/emergency-triage-priority","version":"1.0.0","status":"active","compose":{"include":[{"system":"https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-priority"}]}}$cs$,
 md5('emergency-triage-priority-vs-1.0.0'),
 'MoHCC / WHO IITT', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

-- 2. Triage tool ------------------------------------------------------------
('00000000-0000-0000-0000-000000000001', 'CODE_SYSTEM',
 'https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-tool', '1.0.0',
 'ImpiloEmergencyTriageTool', 'Emergency triage tool',
 'The tool that set the acuity of record. WHO_IITT is authoritative (PO ruling); ESI and MTS are advisory only and may never set the acuity; IMPILO_5 is a manually-entered acuity.',
 'PUBLISHED',
 $cs${
   "resourceType":"CodeSystem",
   "url":"https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-tool",
   "version":"1.0.0","status":"active","content":"complete",
   "property":[{"code":"authority","type":"string","description":"AUTHORITATIVE | ADVISORY | MANUAL"}],
   "concept":[
     {"code":"WHO_IITT","display":"WHO Interagency Integrated Triage Tool","property":[{"code":"authority","valueString":"AUTHORITATIVE"}]},
     {"code":"ESI","display":"Emergency Severity Index (advisory)","property":[{"code":"authority","valueString":"ADVISORY"}]},
     {"code":"MTS","display":"Manchester Triage System (advisory)","property":[{"code":"authority","valueString":"ADVISORY"}]},
     {"code":"IMPILO_5","display":"Manually-entered 5-level acuity","property":[{"code":"authority","valueString":"MANUAL"}]}
   ]
 }$cs$,
 md5('emergency-triage-tool-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

('00000000-0000-0000-0000-000000000001', 'VALUE_SET',
 'https://impilo.gov.zw/fhir/ValueSet/emergency-triage-tool', '1.0.0',
 'ImpiloEmergencyTriageToolVS', 'Emergency triage tool value set',
 'All emergency triage tools.', 'PUBLISHED',
 $cs${"resourceType":"ValueSet","url":"https://impilo.gov.zw/fhir/ValueSet/emergency-triage-tool","version":"1.0.0","status":"active","compose":{"include":[{"system":"https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-tool"}]}}$cs$,
 md5('emergency-triage-tool-vs-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

-- 3. Emergency entry route --------------------------------------------------
('00000000-0000-0000-0000-000000000001', 'CODE_SYSTEM',
 'https://impilo.gov.zw/fhir/CodeSystem/emergency-entry-route', '1.0.0',
 'ImpiloEmergencyEntryRoute', 'Emergency episode entry route',
 'How a person entered the emergency episode. Impilo operational vocabulary (mirrors pct chk_ee_entry_route).',
 'PUBLISHED',
 $cs${
   "resourceType":"CodeSystem",
   "url":"https://impilo.gov.zw/fhir/CodeSystem/emergency-entry-route",
   "version":"1.0.0","status":"active","content":"complete",
   "concept":[
     {"code":"WALK_IN","display":"Walk-in"},
     {"code":"AMBULANCE","display":"Ambulance"},
     {"code":"STATUTORY_SERVICE","display":"Statutory service (police/fire)"},
     {"code":"INTERFACILITY_REFERRAL","display":"Inter-facility referral"},
     {"code":"CHW_REFERRAL","display":"Community health worker referral"},
     {"code":"PRIVATE_VEHICLE","display":"Private vehicle"},
     {"code":"WORKPLACE_OR_SCHOOL","display":"Workplace or school"},
     {"code":"MASS_CASUALTY","display":"Mass casualty incident"},
     {"code":"IN_FACILITY_DETERIORATION","display":"In-facility deterioration"},
     {"code":"CLINIC_ESCALATION","display":"Clinic escalation"},
     {"code":"TELECONSULT_ESCALATION","display":"Teleconsult escalation"},
     {"code":"PUBLIC_REQUEST","display":"Public request (SOS)"},
     {"code":"DIRECT_SPECIALIST","display":"Direct specialist"},
     {"code":"UNKNOWN_OR_UNRESPONSIVE","display":"Unknown or unresponsive"}
   ]
 }$cs$,
 md5('emergency-entry-route-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

('00000000-0000-0000-0000-000000000001', 'VALUE_SET',
 'https://impilo.gov.zw/fhir/ValueSet/emergency-entry-route', '1.0.0',
 'ImpiloEmergencyEntryRouteVS', 'Emergency entry route value set',
 'All emergency entry routes.', 'PUBLISHED',
 $cs${"resourceType":"ValueSet","url":"https://impilo.gov.zw/fhir/ValueSet/emergency-entry-route","version":"1.0.0","status":"active","compose":{"include":[{"system":"https://impilo.gov.zw/fhir/CodeSystem/emergency-entry-route"}]}}$cs$,
 md5('emergency-entry-route-vs-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

-- 4. Emergency episode class ------------------------------------------------
('00000000-0000-0000-0000-000000000001', 'CODE_SYSTEM',
 'https://impilo.gov.zw/fhir/CodeSystem/emergency-episode-class', '1.0.0',
 'ImpiloEmergencyEpisodeClass', 'Emergency episode class',
 'Clinical class of an emergency episode. Impilo operational vocabulary (mirrors pct chk_ee_episode_class).',
 'PUBLISHED',
 $cs${
   "resourceType":"CodeSystem",
   "url":"https://impilo.gov.zw/fhir/CodeSystem/emergency-episode-class",
   "version":"1.0.0","status":"active","content":"complete",
   "concept":[
     {"code":"TRAUMA","display":"Trauma"},{"code":"MEDICAL","display":"Medical"},
     {"code":"OBSTETRIC","display":"Obstetric"},{"code":"PAEDIATRIC","display":"Paediatric"},
     {"code":"NEONATAL","display":"Neonatal"},{"code":"TOXICOLOGY","display":"Toxicology"},
     {"code":"PSYCHIATRIC","display":"Psychiatric"},{"code":"ENVIRONMENTAL","display":"Environmental"},
     {"code":"BURNS","display":"Burns"},{"code":"MCI","display":"Mass casualty"},
     {"code":"UNDIFFERENTIATED","display":"Undifferentiated"}
   ]
 }$cs$,
 md5('emergency-episode-class-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

('00000000-0000-0000-0000-000000000001', 'VALUE_SET',
 'https://impilo.gov.zw/fhir/ValueSet/emergency-episode-class', '1.0.0',
 'ImpiloEmergencyEpisodeClassVS', 'Emergency episode class value set',
 'All emergency episode classes.', 'PUBLISHED',
 $cs${"resourceType":"ValueSet","url":"https://impilo.gov.zw/fhir/ValueSet/emergency-episode-class","version":"1.0.0","status":"active","compose":{"include":[{"system":"https://impilo.gov.zw/fhir/CodeSystem/emergency-episode-class"}]}}$cs$,
 md5('emergency-episode-class-vs-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

-- 5. Emergency episode state (FSM) ------------------------------------------
('00000000-0000-0000-0000-000000000001', 'CODE_SYSTEM',
 'https://impilo.gov.zw/fhir/CodeSystem/emergency-episode-state', '1.0.0',
 'ImpiloEmergencyEpisodeState', 'Emergency episode state',
 'Lifecycle state of an emergency episode; the phase property groups the FSM (PRE_ARRIVAL / OPEN / CLOSED / MERGED). Mirrors pct chk_ee_state.',
 'PUBLISHED',
 $cs${
   "resourceType":"CodeSystem",
   "url":"https://impilo.gov.zw/fhir/CodeSystem/emergency-episode-state",
   "version":"1.0.0","status":"active","content":"complete",
   "property":[{"code":"phase","type":"string","description":"PRE_ARRIVAL | OPEN | CLOSED | MERGED"}],
   "concept":[
     {"code":"PRE_ARRIVAL","display":"Pre-arrival","property":[{"code":"phase","valueString":"PRE_ARRIVAL"}]},
     {"code":"OPEN_UNTRIAGED","display":"Open — untriaged","property":[{"code":"phase","valueString":"OPEN"}]},
     {"code":"OPEN_IN_CARE","display":"Open — in care","property":[{"code":"phase","valueString":"OPEN"}]},
     {"code":"OPEN_AWAITING_ACCEPTANCE","display":"Open — awaiting acceptance","property":[{"code":"phase","valueString":"OPEN"}]},
     {"code":"CLOSED_HANDED_OVER","display":"Closed — handed over","property":[{"code":"phase","valueString":"CLOSED"}]},
     {"code":"CLOSED_DISCHARGED","display":"Closed — discharged","property":[{"code":"phase","valueString":"CLOSED"}]},
     {"code":"CLOSED_DIED","display":"Closed — died","property":[{"code":"phase","valueString":"CLOSED"}]},
     {"code":"CLOSED_LEFT_BEFORE_ASSESSMENT","display":"Closed — left before assessment","property":[{"code":"phase","valueString":"CLOSED"}]},
     {"code":"CLOSED_LEFT_AGAINST_ADVICE","display":"Closed — left against advice","property":[{"code":"phase","valueString":"CLOSED"}]},
     {"code":"CLOSED_ABSCONDED","display":"Closed — absconded","property":[{"code":"phase","valueString":"CLOSED"}]},
     {"code":"CLOSED_DECLINED_CARE","display":"Closed — declined care","property":[{"code":"phase","valueString":"CLOSED"}]},
     {"code":"MERGED","display":"Merged into another episode","property":[{"code":"phase","valueString":"MERGED"}]}
   ]
 }$cs$,
 md5('emergency-episode-state-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now()),

('00000000-0000-0000-0000-000000000001', 'VALUE_SET',
 'https://impilo.gov.zw/fhir/ValueSet/emergency-episode-state', '1.0.0',
 'ImpiloEmergencyEpisodeStateVS', 'Emergency episode state value set',
 'All emergency episode states.', 'PUBLISHED',
 $cs${"resourceType":"ValueSet","url":"https://impilo.gov.zw/fhir/ValueSet/emergency-episode-state","version":"1.0.0","status":"active","compose":{"include":[{"system":"https://impilo.gov.zw/fhir/CodeSystem/emergency-episode-state"}]}}$cs$,
 md5('emergency-episode-state-vs-1.0.0'),
 'MoHCC', 'ZW', 'emergency-valueset-seed', 'emergency-valueset-seed', now())
ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
