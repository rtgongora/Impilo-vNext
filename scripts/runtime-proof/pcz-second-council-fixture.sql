-- ============================================================================
-- PCZ second-council fixture (NCZ-W1E). Its job is to FAIL, not to add coverage.
--
-- The Nurses Council was meant to be the FIRST configuration pack on a
-- generalisable platform, not the architecture for all councils. That claim is
-- either true or it is marketing, and the only way to tell is to stand a second
-- council up through the same machinery and see what breaks.
--
-- Everything below configures the Pharmacists Council (PCZ) as DATA. If any of
-- it required a code change, the claim was false.
--
--   P1  PCZ standing registers  — THE KNOWN GAP, asserted deliberately
--   P2  PCZ numbering policy    — same engine, different council
--   P3  PCZ fee schedule        — same gate, different council
--   P4  the fee gate answers per council, with no NCZ fallback
--   P5  an unset PCZ fee is NOT_CONFIGURED, exactly as NCZ's is
--   P6  a configured PCZ fee is PAYABLE while NCZ's stays unset — the two
--       councils do not contaminate one another
--   P7  the register-entry lifecycle governs PCZ identically
--   P8  numbering is per policy: PCZ's sequence starts at 1, not after NCZ's
--
-- Run inside BEGIN..ROLLBACK against a database carrying varapi V039-V042.
-- ============================================================================
DO $$
DECLARE
  tid UUID; ncz BIGINT; pcz BIGINT; reg BIGINT; pol BIGINT; pid BIGINT;
  n varapi.issued_number; fee RECORD; nczfee RECORD; e1 BIGINT;
BEGIN
  SELECT id, tenant_id INTO ncz, tid FROM varapi.councils WHERE council_code='NCZ';
  SELECT id INTO pcz FROM varapi.councils WHERE council_code='PCZ';
  IF pcz IS NULL THEN RAISE EXCEPTION 'P0 FAIL — PCZ is not seeded; the fixture has nothing to prove'; END IF;

  -- ── P1: THE KNOWN GAP, stated rather than skirted ────────────────────────
  -- V039 seeded NCZ's four standing registers as literal SQL. PCZ has none, and
  -- there is no mechanism to materialise them from an activated configuration
  -- pack — varapi has no org-registry client. So a second council currently
  -- needs a MIGRATION to get its registers, which is precisely the leak this
  -- fixture exists to find.
  --
  -- This assertion is deliberately written the way it is: it FAILS if someone
  -- "fixes" the gap by hardcoding PCZ's registers in another migration, which is
  -- the wrong fix. It must be INVERTED, not deleted, when registers are
  -- materialised from configuration.
  IF EXISTS (SELECT 1 FROM varapi.professional_registers
              WHERE council_id = pcz AND register_axis = 'STANDING') THEN
    RAISE EXCEPTION 'P1 FAIL — PCZ standing registers exist. If they were materialised from an '
                    'activated pack, invert this assertion. If they were hardcoded in a migration, '
                    'that is the leak repeated, not fixed.';
  END IF;
  RAISE NOTICE 'P1  KNOWN GAP CONFIRMED: registers are not yet materialised from configuration';

  -- Stand PCZ's register up as the fixture (what the materialiser will do).
  INSERT INTO varapi.professional_registers
      (tenant_id, council_id, register_code, name, register_axis, statutory_title_decision_ref)
    VALUES (tid, pcz, 'PCZ_INTERN', 'Intern Pharmacist Register', 'STANDING', 'PCZ-DEC-001')
    RETURNING id INTO reg;
  RAISE NOTICE 'P2  a PCZ standing register stands up through the same table and CHECKs';

  -- ── P3/P4: numbering, per council ────────────────────────────────────────
  INSERT INTO varapi.numbering_policy
      (tenant_id, council_id, policy_key, label, format_pattern, status, config_version_ref)
    VALUES (tid, pcz, 'intern-index', 'Intern index number', 'PCZ-INT-{seq}', 'ACTIVE',
            'pharmacists-council/intern-index')
    RETURNING id INTO pol;
  n := varapi.reserve_number(tid, pol, 'INTERN', 'pharm-1', NULL, 'rig');
  IF n.ordinal <> 1 THEN
    RAISE EXCEPTION 'P3 FAIL — PCZ''s first number was ordinal %, so the sequence is shared with '
                    'another council rather than being per policy', n.ordinal;
  END IF;
  IF n.number NOT LIKE 'PCZ-INT-%' THEN
    RAISE EXCEPTION 'P3 FAIL — PCZ got the number % , which is not its own format', n.number;
  END IF;
  RAISE NOTICE 'P3  PCZ numbering starts at 1 in its OWN format (%) — sequences are per policy', n.number;

  -- ── P5/P6: the fee gate answers per council, with no NCZ fallback ────────
  INSERT INTO varapi.regulatory_fee_schedule
      (tenant_id, council_id, fee_code, description, amount_decision_ref, status)
    VALUES (tid, pcz, 'INTERN_INDEX', 'Payable on admission to the Intern Register.',
            'PCZ-DEC-002', 'PENDING_REGULATOR_APPROVAL');
  SELECT * INTO fee FROM varapi.fee_verdict(tid, pcz, 'INTERN_INDEX');
  IF fee.verdict <> 'NOT_CONFIGURED' THEN
    RAISE EXCEPTION 'P5 FAIL — an unset PCZ fee reported %, not NOT_CONFIGURED', fee.verdict;
  END IF;
  RAISE NOTICE 'P5  an unset PCZ fee behaves exactly as an unset NCZ fee does';

  UPDATE varapi.regulatory_fee_schedule SET amount=40, currency='USD', status='ACTIVE'
   WHERE council_id=pcz AND fee_code='INTERN_INDEX';
  SELECT * INTO fee FROM varapi.fee_verdict(tid, pcz, 'INTERN_INDEX');
  SELECT * INTO nczfee FROM varapi.fee_verdict(tid, ncz, 'STUDENT_INDEX');
  IF fee.verdict <> 'PAYABLE' OR fee.amount <> 40 THEN
    RAISE EXCEPTION 'P6 FAIL — a configured PCZ fee did not report PAYABLE 40 (% %)',
                    fee.verdict, fee.amount;
  END IF;
  IF nczfee.verdict = 'PAYABLE' THEN
    RAISE EXCEPTION 'P6 FAIL — setting PCZ''s fee made NCZ''s look configured. The councils are '
                    'contaminating one another.';
  END IF;
  RAISE NOTICE 'P6  PCZ payable at 40 USD while NCZ stays % — no cross-council contamination',
               nczfee.verdict;

  -- A fee code that belongs to the OTHER council must not resolve here.
  SELECT * INTO fee FROM varapi.fee_verdict(tid, pcz, 'STUDENT_INDEX');
  IF fee.verdict <> 'NO_SUCH_FEE' THEN
    RAISE EXCEPTION 'P6 FAIL — NCZ''s fee code resolved against PCZ (%)', fee.verdict;
  END IF;
  RAISE NOTICE 'P6b another council''s fee code does not resolve — lookups are council-scoped';

  -- ── P7: the entry lifecycle governs PCZ identically ──────────────────────
  SELECT id INTO pid FROM varapi.provider LIMIT 1;
  INSERT INTO varapi.provider_council_registration_records
      (tenant_id, provider_id, council_id, register_id, registration_number, status,
       effective_from, admitted_via)
    VALUES (tid, pid, pcz, reg, n.number, 'ADMITTED', CURRENT_DATE, 'fixture')
    RETURNING id INTO e1;
  BEGIN
    UPDATE varapi.provider_council_registration_records SET status='MIGRATED' WHERE id=e1;
    RAISE EXCEPTION 'P7 FAIL — a PCZ entry migrated with no successor; the FSM is not applied '
                    'to this council';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'P7  the V039 lifecycle governs PCZ identically (migration needs a successor)';
  END;

  -- ── P8: application machinery is council-agnostic ────────────────────────
  RAISE NOTICE 'P8  every step above ran on the SHARED engines with PCZ data and no code change';
END $$;
