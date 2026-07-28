-- ============================================================================
-- NCZ Student Registration — schema invariant proof (NCZ-W1C).
--
-- THE POINT: this slice's guarantees are all refusals — a training institution
-- that cannot read the whole case, a returned section that does not restart the
-- application, a number that is never reused, and a fee the Council has not set
-- that reports NOT_CONFIGURED rather than falling through as free. Each is
-- asserted by ATTEMPTING the violation.
--
-- Run inside BEGIN..ROLLBACK against a database carrying varapi V039-V042:
--   { echo BEGIN; cat <V039..V042>; cat this; echo ROLLBACK; } | psql -d varapi
--
-- Two real defects were caught by running it rather than reading it:
--   * array_length() of an EMPTY array is NULL, and a CHECK passes on NULL — so
--     `array_length(scope_sections,1) >= 1` accepted the empty scope it existed
--     to refuse. COALESCE(...,0) closed it.
--   * UPDATE ... RETURNING yields the row AFTER the update, so the numbering
--     sequence issued ordinal 2 first and silently burned ordinal 1.
-- ============================================================================
DO $$
DECLARE tid UUID; cid BIGINT; pid BIGINT; app BIGINT; polid BIGINT; inv BIGINT; n varapi.issued_number;
        v RECORD;
BEGIN
  SELECT id, tenant_id INTO cid, tid FROM varapi.councils WHERE council_code='NCZ';
  SELECT id INTO pid FROM varapi.provider LIMIT 1;
  INSERT INTO varapi.provider_applications (provider_id, tenant_id, application_type, workflow_state, council_id)
    VALUES (pid, tid, 'STUDENT_REGISTRATION', 'DRAFT', cid) RETURNING id INTO app;
  INSERT INTO varapi.application_section (tenant_id, application_id, section_key, label, sequence_no)
    VALUES (tid, app, 'identity', 'Identity', 10), (tid, app, 'enrolment', 'Enrolment', 20),
           (tid, app, 'documents', 'Documents', 30);
  RAISE NOTICE 'S1  an application has sections a student completes over time';

  -- The bounded contributor.
  BEGIN
    INSERT INTO varapi.application_contributor_invite
        (tenant_id, application_id, contributor_kind, scope_sections)
      VALUES (tid, app, 'TRAINING_INSTITUTION', ARRAY['*']);
    RAISE EXCEPTION 'S2 FAIL — a contributor was granted the whole case by wildcard';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'S2  a contributor cannot be granted everything'; END;
  BEGIN
    INSERT INTO varapi.application_contributor_invite
        (tenant_id, application_id, contributor_kind, scope_sections) VALUES (tid, app, 'TRAINING_INSTITUTION', ARRAY[]::TEXT[]);
    RAISE EXCEPTION 'S3 FAIL — an invite with no scope was accepted';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'S3  an invite must name its sections'; END;
  BEGIN
    INSERT INTO varapi.application_contributor_invite
        (tenant_id, application_id, contributor_kind, scope_sections)
      VALUES (tid, app, 'TRAINING_INSTITUTION', ARRAY['enrolment','assessment']);
    RAISE EXCEPTION 'S4 FAIL — an invite named a section the application does not have';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'S4  an invite cannot name a section that does not exist'; END;
  INSERT INTO varapi.application_contributor_invite
      (tenant_id, application_id, contributor_kind, scope_sections)
    VALUES (tid, app, 'TRAINING_INSTITUTION', ARRAY['enrolment']) RETURNING id INTO inv;
  RAISE NOTICE 'S5  the institution is invited to confirm enrolment, and only enrolment';

  -- Section-level return.
  BEGIN
    UPDATE varapi.application_section SET state='RETURNED', returned_reason='blurry scan'
      WHERE application_id=app AND section_key='documents';
    RAISE EXCEPTION 'S6 FAIL — a section was returned on an unsubmitted application';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'S6  nothing to return before submission'; END;
  UPDATE varapi.provider_applications SET workflow_state='SUBMITTED', submitted_at=NOW() WHERE id=app;
  BEGIN
    UPDATE varapi.application_section SET state='RETURNED' WHERE application_id=app AND section_key='documents';
    RAISE EXCEPTION 'S7 FAIL — a section was returned with no reason';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'S7  a returned section must say why'; END;
  UPDATE varapi.application_section SET state='RETURNED', returned_reason='The certified copy is unreadable.'
    WHERE application_id=app AND section_key='documents';
  IF (SELECT submitted_at FROM varapi.provider_applications WHERE id=app) IS NULL
     OR (SELECT workflow_state FROM varapi.provider_applications WHERE id=app) <> 'SUBMITTED' THEN
    RAISE EXCEPTION 'S8 FAIL — returning a section restarted the application';
  END IF;
  RAISE NOTICE 'S8  returning one section leaves the case submitted and in the queue';
  IF (SELECT return_count FROM varapi.application_section WHERE application_id=app AND section_key='documents') <> 1 THEN
    RAISE EXCEPTION 'S9 FAIL — the return was not counted';
  END IF;
  RAISE NOTICE 'S9  the return is counted, so repeated returns are visible';

  -- Numbering.
  SELECT id INTO polid FROM varapi.numbering_policy WHERE council_id=cid AND policy_key='student-index';
  IF polid IS NULL THEN RAISE EXCEPTION 'N0 FAIL — the student index policy was not seeded'; END IF;
  BEGIN
    UPDATE varapi.numbering_policy SET status='ACTIVE' WHERE id=polid;
    RAISE EXCEPTION 'N1 FAIL — issuance was activated with no format';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'N1  issuance cannot be activated without a format'; END;
  BEGIN
    PERFORM varapi.reserve_number(tid, polid, 'STUDENT', 'stu-1', app, 'rig');
    RAISE EXCEPTION 'N2 FAIL — a number was issued under an unset format';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'N2  no number is minted while the format is a council decision'; END;

  UPDATE varapi.numbering_policy SET format_pattern='NCZ-STU-{seq}', status='ACTIVE' WHERE id=polid;
  n := varapi.reserve_number(tid, polid, 'STUDENT', 'stu-1', app, 'rig');
  IF n.ordinal <> 1 THEN
    RAISE EXCEPTION 'N3 FAIL — the first number issued was ordinal %, not 1', n.ordinal;
  END IF;
  RAISE NOTICE 'N3  with a format set, the FIRST number is ordinal 1: %', n.number;
  UPDATE varapi.issued_number SET state='ISSUED', issued_at=NOW() WHERE id=n.id;
  BEGIN
    UPDATE varapi.issued_number SET number='NCZ-STU-999999' WHERE id=n.id;
    RAISE EXCEPTION 'N4 FAIL — an issued number was re-pointed';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'N4  a number that meant someone is never re-pointed'; END;
  BEGIN
    DELETE FROM varapi.issued_number WHERE id=n.id;
    RAISE EXCEPTION 'N5 FAIL — an issued number was deleted, freeing it for reuse';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'N5  numbers are never deleted, so never reused'; END;
  DECLARE n2 varapi.issued_number;
  BEGIN
    n2 := varapi.reserve_number(tid, polid, 'STUDENT', 'stu-2', app, 'rig');
    IF n2.ordinal <= n.ordinal THEN
      RAISE EXCEPTION 'N6 FAIL — the sequence did not advance (% then %)', n.ordinal, n2.ordinal;
    END IF;
  END;
  RAISE NOTICE 'N6  the next reservation advances the sequence';

  -- Fees.
  SELECT * INTO v FROM varapi.fee_verdict(tid, cid, 'STUDENT_INDEX');
  IF v.verdict <> 'NOT_CONFIGURED' THEN
    RAISE EXCEPTION 'F1 FAIL — an unset fee reported %, not NOT_CONFIGURED', v.verdict;
  END IF;
  RAISE NOTICE 'F1  an unset fee reports NOT_CONFIGURED — never zero, never "no fee applies"';
  BEGIN
    UPDATE varapi.regulatory_fee_schedule SET status='ACTIVE' WHERE council_id=cid AND fee_code='STUDENT_INDEX';
    RAISE EXCEPTION 'F2 FAIL — a fee with no amount was activated';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'F2  a fee cannot be made chargeable without a value'; END;
  BEGIN
    UPDATE varapi.regulatory_fee_schedule SET amount=25 WHERE council_id=cid AND fee_code='STUDENT_INDEX';
    RAISE EXCEPTION 'F3 FAIL — an amount was set with no currency';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'F3  an amount without a currency is not a price'; END;
  UPDATE varapi.regulatory_fee_schedule SET amount=25, currency='USD', status='ACTIVE'
    WHERE council_id=cid AND fee_code='STUDENT_INDEX';
  SELECT * INTO v FROM varapi.fee_verdict(tid, cid, 'STUDENT_INDEX');
  IF v.verdict <> 'PAYABLE' OR v.amount <> 25 THEN
    RAISE EXCEPTION 'F4 FAIL — a configured fee did not report PAYABLE (%, %)', v.verdict, v.amount;
  END IF;
  RAISE NOTICE 'F4  once the Council sets it, the same fee reports PAYABLE 25 USD';
  SELECT * INTO v FROM varapi.fee_verdict(tid, cid, 'NO_SUCH_CODE');
  IF v.verdict <> 'NO_SUCH_FEE' THEN
    RAISE EXCEPTION 'F5 FAIL — an unknown fee code reported %', v.verdict;
  END IF;
  RAISE NOTICE 'F5  an unknown fee code is distinguishable from an unset one';
END $$;
