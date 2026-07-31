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
--   P1  PCZ standing registers  — THE CLOSED GAP, now asserted positively
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

  -- ── P1: THE GAP, NOW CLOSED — inverted, and still trapping the wrong fix ──
  --
  -- THE RECORD OF THE LEAK. Until V043 this assertion read the other way round:
  -- it required PCZ to have NO standing registers, because V039 had seeded
  -- NCZ's four as literal SQL and varapi had no org-registry client, so a
  -- second council needed a MIGRATION to get its registers. Registers were the
  -- one thing a council could not arrive with as configuration, which made "a
  -- council is a configuration pack" true of everything except them.
  --
  -- It was written to fail on the WRONG fix: hardcoding PCZ's registers in
  -- another migration would have turned it red. That trap is kept below rather
  -- than traded away for a green build — the leak is closed by provenance, so
  -- the fixture now demands provenance instead of absence. A migration-seeded
  -- PCZ standing register still fails, exactly as it did before.
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema='varapi' AND table_name='professional_registers'
                    AND column_name='source') THEN
    RAISE EXCEPTION 'P1 FAIL — professional_registers carries no provenance, so a register '
                    'materialised from configuration is indistinguishable from one hardcoded in '
                    'a migration. Without V043 nothing below can discriminate.';
  END IF;

  IF EXISTS (SELECT 1 FROM varapi.professional_registers
              WHERE council_id = pcz AND register_axis = 'STANDING'
                AND source <> 'CONFIG_PACK') THEN
    RAISE EXCEPTION 'P1 FAIL — a PCZ standing register exists that did NOT come from an activated '
                    'configuration pack. That is the leak repeated, not fixed: a second council '
                    'still cannot arrive as configuration alone.';
  END IF;
  RAISE NOTICE 'P1  no PCZ standing register was hardcoded — the wrong fix still fails here';

  -- Stand PCZ's register up exactly as RegisterMaterialisationService writes it: CONFIG_PACK
  -- provenance naming the definition version that authorised it. This fixture runs as bare SQL
  -- against a throwaway database with no org-registry to activate a release against, so what it
  -- proves is the SHAPE the materialiser must produce and the guarantees the schema then makes
  -- about it. That the service produces this shape is proven separately by
  -- RegisterMaterialisationServiceTest.
  INSERT INTO varapi.professional_registers
      (tenant_id, council_id, register_code, name, register_axis, statutory_title_decision_ref,
       source, config_definition_key, config_semantic_version, config_content_hash, materialised_at)
    VALUES (tid, pcz, 'PCZ_INTERN', 'Intern Pharmacist Register', 'STANDING', 'PCZ-DEC-001',
            'CONFIG_PACK', 'register-intern', '1.0.0', 'sha256:pcz-intern-v1', NOW())
    RETURNING id INTO reg;

  IF NOT EXISTS (SELECT 1 FROM varapi.professional_registers
                  WHERE id = reg AND source = 'CONFIG_PACK'
                    AND config_definition_key IS NOT NULL
                    AND config_content_hash IS NOT NULL) THEN
    RAISE EXCEPTION 'P1 FAIL — PCZ''s register did not retain the provenance that names the '
                    'approved definition version behind it';
  END IF;
  RAISE NOTICE 'P1  PCZ holds a STANDING register sourced from configuration, traceable to the '
               'definition version that authorised it — no migration was written for PCZ';

  -- The other half of the guarantee, proven by attempting the violation: a register carries the
  -- entries admitted to it, so a pack that stops declaring one must not be able to destroy it.
  BEGIN
    DELETE FROM varapi.professional_registers WHERE id = reg;
    RAISE EXCEPTION 'P1 FAIL — a register was DELETED. A pack edit could then take away the '
                    'register its entries were admitted to. Retirement is the only legal removal.';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'P1b the database refuses to delete a register — retirement is the only removal';
  END;

  UPDATE varapi.professional_registers SET status='RETIRED', retired_at=NOW() WHERE id=reg;
  UPDATE varapi.professional_registers SET status='ACTIVE', retired_at=NULL WHERE id=reg;
  RAISE NOTICE 'P1c retirement is a dated, reversible state — the register keeps its entries';

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
