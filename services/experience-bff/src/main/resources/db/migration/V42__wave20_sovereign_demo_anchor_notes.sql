-- Wave 20 slice 13 — correct sovereign demo anchor notes after Flyway version renumber.
-- Demo seeds: pharmacy V004, mushex V007, dispatch V004 (schema migrations kept at V003/V005).

UPDATE sovereign_demo_anchors SET notes = 'V004 dispense order seed when sovereign overlay up'
WHERE domain_id = 'pharmacy';

UPDATE sovereign_demo_anchors SET notes = 'V007/V008 payment intent seed when sovereign overlay up'
WHERE domain_id = 'mushex';

UPDATE sovereign_demo_anchors SET bff_probe_path = '/internal/v1/finance/payer-ops/payment-intents?sourceType=ADHOC&sourceId=CPID-ZW-00001'
WHERE domain_id = 'mushex';

UPDATE sovereign_demo_anchors SET notes = 'V004 dispatch job seed when sovereign overlay up'
WHERE domain_id = 'dispatch';
