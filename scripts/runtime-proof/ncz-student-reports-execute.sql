-- ============================================================================
-- NCZ student registration reports — DO THEY EXECUTE? (NCZ-W1D)
--
-- THE POINT, and it is not the numbers. Five of the seven ACTIVE governed report
-- definitions in reporting-service embed SQL against varapi.* — and the reporting
-- database has no varapi schema, no postgres_fdw and no dblink. They look exactly
-- like reports. They cannot run. Nobody noticed because a registry row and a
-- working report are indistinguishable until someone executes one.
--
-- So every query behind the W1D board is run here against the real schema. A
-- report that has never been executed is a claim, not a capability.
--
-- Run inside BEGIN..ROLLBACK against a database carrying varapi V039-V042.
-- ============================================================================
-- Every report query, run against the real schema. The point is not the numbers — it is that each
-- statement PARSES and EXECUTES against the tables as they actually are. Five ACTIVE definitions in
-- reporting-service look exactly like reports and return nothing, because nobody ever ran them.
DO $$
DECLARE tid UUID; cid BIGINT; n BIGINT; fee RECORD;
BEGIN
  SELECT id, tenant_id INTO cid, tid FROM varapi.councils WHERE council_code='NCZ';

  SELECT count(*) INTO n FROM varapi.provider_applications a
   WHERE a.tenant_id=tid AND a.council_id=cid AND a.application_type='STUDENT_REGISTRATION'
     AND a.workflow_state='SUBMITTED';
  RAISE NOTICE 'R1  submitted count executes (%)', n;

  SELECT count(DISTINCT a.id) INTO n FROM varapi.provider_applications a
    JOIN varapi.application_section s ON s.application_id = a.id
   WHERE a.tenant_id=tid AND a.council_id=cid AND a.application_type='STUDENT_REGISTRATION'
     AND s.state='RETURNED';
  RAISE NOTICE 'R2  awaiting-applicant joins sections (%)', n;

  SELECT count(DISTINCT a.id) INTO n FROM varapi.provider_applications a
    JOIN varapi.application_contributor_invite i ON i.application_id = a.id
   WHERE a.tenant_id=tid AND a.council_id=cid AND a.application_type='STUDENT_REGISTRATION'
     AND i.status IN ('INVITED','ACCEPTED');
  RAISE NOTICE 'R3  awaiting-institution joins invites (%)', n;

  SELECT count(*) INTO n FROM varapi.provider_council_registration_records r
    JOIN varapi.professional_registers p ON p.id = r.register_id
   WHERE r.tenant_id=tid AND r.council_id=cid AND r.status='ADMITTED' AND p.register_axis='STANDING';
  RAISE NOTICE 'R4  admissions join the STANDING registers V039 added (%)', n;

  SELECT count(*) INTO n FROM varapi.issued_number i
    JOIN varapi.numbering_policy p ON p.id = i.policy_id
   WHERE i.tenant_id=tid AND p.council_id=cid AND i.state='ISSUED';
  RAISE NOTICE 'R5  issued index numbers join the policy (%)', n;

  PERFORM 1 FROM (SELECT band, count(*) FROM (
      SELECT CASE WHEN NOW()-a.submitted_at < INTERVAL '7 days' THEN '0-6 days'
                  WHEN NOW()-a.submitted_at < INTERVAL '30 days' THEN '7-29 days'
                  WHEN NOW()-a.submitted_at < INTERVAL '90 days' THEN '30-89 days'
                  ELSE '90+ days' END AS band
        FROM varapi.provider_applications a
       WHERE a.tenant_id=tid AND a.council_id=cid AND a.application_type='STUDENT_REGISTRATION'
         AND a.submitted_at IS NOT NULL AND a.closed_at IS NULL) b
      GROUP BY band) ageing;
  RAISE NOTICE 'R6  the ageing bands execute';

  PERFORM 1 FROM (SELECT s.section_key, sum(s.return_count) AS returns,
                         count(*) FILTER (WHERE s.state='RETURNED') AS open_returns
                    FROM varapi.application_section s
                    JOIN varapi.provider_applications a ON a.id = s.application_id
                   WHERE s.tenant_id=tid AND a.council_id=cid
                   GROUP BY s.section_key) t;
  RAISE NOTICE 'R7  returns-by-section executes (the FILTER clause parses)';

  SELECT count(*) INTO n FROM varapi.application_contributor_invite
   WHERE tenant_id=tid AND status='INVITED';
  RAISE NOTICE 'R8  the institution board executes (%)', n;

  -- THE DISTINCTION. With the fee unset, fee_verdict must NOT report PAYABLE, so the board can
  -- separate "the applicant has not paid" from "the Council has not set the fee".
  SELECT * INTO fee FROM varapi.fee_verdict(tid, cid, 'STUDENT_INDEX');
  IF fee.verdict = 'PAYABLE' THEN
    RAISE EXCEPTION 'R9 FAIL — an unset fee reported PAYABLE, so the board would chase people '
                    'for a payment nobody can make';
  END IF;
  RAISE NOTICE 'R9  awaiting-payment and awaiting-council-decision cannot be conflated (fee=%)', fee.verdict;
END $$;
