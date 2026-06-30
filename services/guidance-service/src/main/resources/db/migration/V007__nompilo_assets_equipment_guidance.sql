-- Nompilo guidance seeds for the Assets/Devices/Equipment/IoT domain (WS#5).
-- Reuses the existing guidance.guidance_item registry (V004). asset-registry-service owns the
-- asset/equipment/device truth; Nompilo (guidance-service) owns the guidance catalogue. Routes
-- point at real WS#5 surfaces under /operations/equipment. Each item routes to a sovereign owner
-- (asset-registry / rito / khuluma / inventory) — Nompilo never owns the underlying transaction.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Equipment registry: orientation ---------------------------------------------------
    ('ASSETS_REGISTRY_WELCOME', 'assets', 'ORIENTATION', '/operations/equipment', 'FACILITY_ADMIN', 2, 'first_time',
     'Manage equipment through its operational life',
     'This registry tracks each equipment item from registration through assignment, maintenance, calibration and retirement. Stock and spare parts stay with inventory (Dura); facility identity stays with Tuso. Register, search and open any item to see its full lifecycle.',
     'Register equipment', '/operations/equipment', 'asset-registry', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 60, FALSE),

    -- Readiness: real gaps, not a score -------------------------------------------------
    ('ASSETS_READINESS_GAPS', 'assets', 'STATUS_EXPLANATION', '/operations/equipment/readiness', 'FACILITY_ADMIN', 2, 'readiness_gaps',
     'Why a service point is not ready',
     'Readiness compares the equipment each service point requires against what is operational right now. A gap means a required item is missing, broken, under maintenance or uncalibrated — not a cosmetic score. Resolve the underlying item to close the gap.',
     'View readiness', '/operations/equipment/readiness', 'asset-registry', 'nompilo.guidance.locked.explain', FALSE, NULL, 'WARN', 25, TRUE),

    -- Calibration: unsafe explainer -----------------------------------------------------
    ('ASSETS_CALIBRATION_UNSAFE', 'assets', 'LOCKED_STATE', '/operations/equipment/calibration', 'FACILITY_ADMIN', 2, 'calibration_failed',
     'This equipment is flagged unsafe',
     'A failed calibration marks equipment UNSAFE and removes it from readiness until it is recalibrated and returned to service. This protects patients — recording a passing calibration restores it.',
     'Open calibration', '/operations/equipment/calibration', 'asset-registry', 'nompilo.guidance.locked.explain', FALSE, NULL, 'WARN', 20, TRUE),

    -- Fault → safety routing to Rito ----------------------------------------------------
    ('ASSETS_FAULT_SAFETY', 'assets', 'ESCALATION', '/operations/equipment', 'FACILITY_ADMIN', 2, 'safety_concern',
     'Safety faults go to Rito',
     'Report any equipment fault here. A routine fault opens a corrective maintenance task; a safety-flagged fault is linked to Rito, the quality and safety service. Asset-registry links the case but does not own it.',
     'Report a fault', '/operations/equipment', 'rito', 'nompilo.guidance.context.view', FALSE, NULL, 'WARN', 30, TRUE),

    -- IoT alerts: no fabricated telemetry -----------------------------------------------
    ('ASSETS_IOT_ALERTS', 'assets', 'STATUS_EXPLANATION', '/operations/equipment/iot', 'FACILITY_ADMIN', 2, 'active_alert',
     'Device alerts come from real signals',
     'These alerts are derived from real telemetry and heartbeats ingested by the IoT ingestion service — no synthetic readings are generated. Request a Khuluma notification to escalate, or resolve an alert once handled. Clinical observations route to Butano; cold-chain breaches route to Madi.',
     'Open IoT status', '/operations/equipment/iot', 'khuluma', 'nompilo.guidance.context.view', TRUE, NULL, 'RECOMMEND', 35, TRUE),

    -- Maintenance due: next best action -------------------------------------------------
    ('ASSETS_MAINTENANCE_DUE', 'assets', 'NEXT_BEST_ACTION', '/operations/equipment/maintenance', 'FACILITY_ADMIN', 2, 'maintenance_due',
     'Equipment is due for maintenance',
     'These items are past their maintenance date. Assign a technician (workforce is owned by Vashandi), complete the task and return the equipment to service. Spare parts are requested from inventory/Oros — never held here.',
     'Open maintenance', '/operations/equipment/maintenance', 'asset-registry', 'nompilo.guidance.context.view', TRUE, NULL, 'RECOMMEND', 40, FALSE),

    -- Transfer / custody handover -------------------------------------------------------
    ('ASSETS_TRANSFER_HANDOVER', 'assets', 'PROTOCOL', '/operations/equipment', 'FACILITY_ADMIN', 2, 'transfer_pending',
     'Confirm custody on handover',
     'Transferring equipment records a custody change. The receiving facility confirms receipt before custody moves — this keeps the audit trail honest. Approvals are required where the transfer is flagged.',
     'View equipment', '/operations/equipment', 'asset-registry', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 45, TRUE),

    -- Field deployment ------------------------------------------------------------------
    ('ASSETS_DEPLOYMENT_KIT', 'assets', 'PROTOCOL', '/operations/equipment/deployment', 'FACILITY_ADMIN', 2, 'always',
     'Track field deployment kits',
     'Deployment kits bundle equipment for outreach or emergency use. Prepare a kit, deploy it, then reconcile it on return — losses or damage automatically flag the affected equipment so the registry stays truthful.',
     'Open deployment kits', '/operations/equipment/deployment', 'asset-registry', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 50, FALSE)
ON CONFLICT (tenant_id, guidance_key) DO NOTHING;
