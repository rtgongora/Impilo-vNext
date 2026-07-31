-- Authentication assurance (AAL) and identity proofing assurance (IAL/LoA) are
-- independent policy dimensions. These rules were written to constrain the
-- strength/freshness of the login session, so migrate them by intent. Explicit
-- identity-proofing rules such as break-glass governance retain min_loa.
UPDATE tshepo_authz.policy_rule
SET conditions = (conditions - 'min_loa')
        || jsonb_build_object('min_aal', conditions -> 'min_loa')
WHERE conditions ? 'min_loa'
  AND (
      name LIKE 'asset-%'
      OR name LIKE 'theatre-emergency-%'
      OR name LIKE 'theatre-consent-exception-%'
      OR name LIKE 'confidential-lane-%'
  );
