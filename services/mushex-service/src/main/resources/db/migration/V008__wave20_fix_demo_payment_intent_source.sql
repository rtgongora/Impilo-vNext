-- Wave 20 slice 13 — align demo payment intent source_type with MusheX SourceType enum.

UPDATE mushex_payment_intents
SET source_type = 'ADHOC'
WHERE idempotency_key = 'wave20-demo-rx-billing-0001'
  AND source_type = 'RX_DISPENSE';
