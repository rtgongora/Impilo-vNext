-- Nompilo guidance seeds for the Msika marketplace domain (WS#4 — Msika Health Marketplace).
-- Reuses the existing guidance.guidance_item registry (V004); Msika owns the marketplace
-- discovery/listing data, Nompilo (guidance-service) owns the guidance catalogue. Routes point
-- at real Msika storefront-lane surfaces under /marketplace/store and /marketplace/seller.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Storefront home: orientation ------------------------------------------------------
    ('MSIKA_STORE_WELCOME', 'msika', 'ORIENTATION', '/marketplace/store', 'CITIZEN', NULL, 'first_time',
     'Find health products and services',
     'The marketplace lets you discover, request and book approved health products and services from verified providers, facilities and programmes. Billing stays with Costa, payments with MusheX, and clinical items route through your care team.',
     'Browse listings', '/marketplace/store/search', 'msika', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 60, FALSE),

    -- Listing detail: regulated/clinical gate explainer --------------------------------
    ('MSIKA_REGULATED_GATE', 'msika', 'LOCKED_STATE', '/marketplace/store/listing', 'CITIZEN', NULL, 'regulated_item',
     'This item is regulated',
     'Regulated and prescription items need an eligibility or licence check before checkout, and some route to a provider or clinical order. This protects your safety — Msika never bypasses the clinical owner.',
     'Check eligibility', '/marketplace/store/listing', 'msika', 'nompilo.guidance.locked.explain', FALSE, NULL, 'WARN', 20, TRUE),

    -- Buyer activity / tracking: next-step explainer -----------------------------------
    ('MSIKA_TRACK_NEXT', 'msika', 'STATUS_EXPLANATION', '/marketplace/store/activity', 'CITIZEN', NULL, 'awaiting_payment',
     'Your order is waiting for payment',
     'This order is approved and waiting for payment. Paying continues the flow to booking and fulfilment. Your billing detail comes from Costa and the payment is handled securely by MusheX.',
     'View order', '/marketplace/store/activity', 'mushex', 'nompilo.guidance.context.view', TRUE, NULL, 'RECOMMEND', 30, FALSE),

    -- Dependant purchase banner ---------------------------------------------------------
    ('MSIKA_DEPENDANT', 'msika', 'STATUS_EXPLANATION', '/marketplace/store', 'CITIZEN', NULL, 'acting_for_dependant',
     'You are shopping for someone you care for',
     'You are acting on behalf of a dependant under an active delegation. Their consent and your relationship are recorded with every cross-person action. Switch back to yourself any time.',
     'Review delegation', '/citizen/wallet/care-team', 'mvumo', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, TRUE),

    -- Seller centre: getting started ----------------------------------------------------
    ('MSIKA_SELLER_START', 'msika', 'NEXT_BEST_ACTION', '/marketplace/seller', 'PROVIDER', NULL, 'no_storefront',
     'Set up your seller storefront',
     'To list on the marketplace, create a storefront. Your provider licence, facility status or programme is verified before regulated listings can go live. Stock and fulfilment stay with their owning services.',
     'Create storefront', '/marketplace/seller', 'msika', 'nompilo.guidance.context.view', FALSE, NULL, 'RECOMMEND', 50, FALSE),

    -- Seller: listing awaiting approval -------------------------------------------------
    ('MSIKA_LISTING_PENDING', 'msika', 'STATUS_EXPLANATION', '/marketplace/seller/listings', 'PROVIDER', NULL, 'listing_awaiting_approval',
     'Your listing is awaiting approval',
     'Submitted listings go through moderation before they are visible to buyers. Regulated items get an extra safety and eligibility review. You will be notified when a decision is made.',
     'View listings', '/marketplace/seller/listings', 'msika', 'nompilo.guidance.context.view', TRUE, NULL, 'INFO', 50, FALSE),

    -- Moderation queue: governance reminder ---------------------------------------------
    ('MSIKA_MODERATION', 'msika', 'PROTOCOL', '/marketplace/seller/moderation', 'SUPPORT', NULL, 'always',
     'Moderate fairly and on the record',
     'Approve, request correction or reject each listing against catalogue, safety and eligibility rules. Every decision is audited — there is no admin bypass. Suspend a live listing if a safety concern emerges.',
     'Open queue', '/marketplace/seller/moderation', 'msika', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 55, TRUE),

    -- Report a listing / safety → Rito --------------------------------------------------
    ('MSIKA_REPORT', 'msika', 'ESCALATION', '/marketplace/store/listing', 'CITIZEN', NULL, 'safety_concern',
     'Something not right? Report it',
     'If a listing or order raises a quality or safety concern, report it. Your report goes to Rito, the quality and safety service — Msika links it but does not own the case.',
     'Report a concern', '/marketplace/store/listing', 'rito', 'nompilo.guidance.context.view', FALSE, NULL, 'WARN', 35, TRUE)
ON CONFLICT (tenant_id, guidance_key) DO NOTHING;
