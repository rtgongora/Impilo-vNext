-- =====================================================================================
-- org-registry :: V009 — who administers a regulator, as distinct from who does its work
--
-- THE GAP.
--
-- V006 seeded thirteen roles and every one of them is OPERATIONAL: registrar, inspector, CPD
-- officer, investigations officer, records officer, and so on. They describe people doing the
-- regulator's statutory work. None of them describes the people who administer the regulator's
-- presence on the platform — who owns the workspace, who administers its users and security, who
-- is accountable for its records. Today that distinction does not exist, so there is no role to
-- grant a founding administrator and no way to say "this person may add users but may not read
-- case files".
--
-- WHY THE SPLIT IS NOT COSMETIC.
--
-- The whole bootstrap trust chain rests on it. "Nobody may self-claim an administrator role" is
-- unenforceable while there is no such thing as an administrator role; "a second administrator is
-- mandatory" cannot be checked if administrators are indistinguishable from registration clerks.
-- So administration becomes a flagged class of role, not a naming convention, and the invariants
-- attach to the flag.
--
-- SEPARATION OF DUTIES.
--
-- The security administrator grants access; the business owner is accountable for what the
-- regulator does with it. One person holding both can grant themselves anything and sign it off,
-- which is the failure the four-eyes rail exists to prevent — so the schema refuses it rather
-- than trusting a process to notice.
--
-- THE COUNTRY ROOT IS NOT A SUPERUSER.
--
-- NATIONAL_TRUST_ADMINISTRATOR is the platform's root trust function: it activates regulators and
-- adjudicates bootstrap requests. It is emphatically NOT an all-councils reader. That is enforced
-- as a DENY at the policy layer (a superuser ALLOW would silently out-rank any absence of grant),
-- and this migration only establishes the role and marks it oversight-scoped so the deny has
-- something to bind to.
-- =====================================================================================

-- ---- Administration is a property of a role, not a naming convention -------------------

ALTER TABLE org_registry.org_registry_appointment_role
    ADD COLUMN IF NOT EXISTS is_administrative    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_bootstrap_only    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS max_concurrent_per_org INT;

COMMENT ON COLUMN org_registry.org_registry_appointment_role.is_administrative IS
    'This role administers the regulator on the platform rather than performing its statutory '
    'work. The bootstrap, succession and four-eyes invariants attach to this flag, so a new '
    'administrative role inherits them by being marked rather than by being remembered.';

COMMENT ON COLUMN org_registry.org_registry_appointment_role.is_bootstrap_only IS
    'Grantable only through the regulator bootstrap rail, and only while the organisation has no '
    'active administrator. Prevents a founding role — which exists precisely because nobody is '
    'there to approve it yet — from being reused later as an ordinary appointment.';

COMMENT ON COLUMN org_registry.org_registry_appointment_role.max_concurrent_per_org IS
    'Upper bound on simultaneous ACTIVE holders at one organisation; NULL means unbounded.';

-- ---- The administration roles -----------------------------------------------------------

INSERT INTO org_registry.org_registry_appointment_role
    (role_code, label, description, is_committee_member, is_oversight, sort_order,
     is_administrative, is_bootstrap_only, max_concurrent_per_org) VALUES

    -- Restricted bootstrap role. Exists to solve the cold-start problem — the first administrator
    -- cannot be appointed by an administrator — and is deliberately capped at one holder so it
    -- cannot quietly become a standing privileged class.
    ('FOUNDING_REGULATOR_ADMINISTRATOR', 'Founding Regulator Administrator',
     'Restricted bootstrap role: the first administrator of a newly activated regulator. Granted '
     'only via the bootstrap rail under four-eyes, only while the organisation has no active '
     'administrator, and expected to be replaced by named administrators once appointed.',
     FALSE, FALSE, 200, TRUE, TRUE, 1),

    ('REGULATOR_BUSINESS_OWNER', 'Business Owner',
     'Accountable for what the regulator does on the platform: the workspace exists to serve their '
     'mandate. Does not administer access — see the security administrator, which this role may '
     'not also hold.',
     FALSE, FALSE, 210, TRUE, FALSE, NULL),

    ('REGULATOR_SECURITY_ADMINISTRATOR', 'Security Administrator',
     'Administers who may enter the workspace and with what role. Separated from the business '
     'owner so that granting access and being accountable for its use are never the same person.',
     FALSE, FALSE, 220, TRUE, FALSE, NULL),

    ('REGULATOR_WORKSPACE_ADMINISTRATOR', 'Workspace Administrator',
     'Configures the workspace itself — queues, routing, templates, working arrangements. Shapes '
     'how work flows; does not decide who may do it.',
     FALSE, FALSE, 230, TRUE, FALSE, NULL),

    ('REGULATOR_RECORDS_ADMINISTRATOR', 'Records Administrator',
     'Accountable for the integrity, retention and disclosure of the regulator''s records. Distinct '
     'from the operational RECORDS_OFFICER, who files and certifies them day to day.',
     FALSE, FALSE, 240, TRUE, FALSE, NULL),

    ('REGULATOR_PROCESS_OWNER', 'Process Owner',
     'Owns a named regulatory process end to end (registration, renewal, inspection, discipline) '
     'and its service standards. Administrative because it governs how a process runs, not a '
     'caseload within it.',
     FALSE, FALSE, 250, TRUE, FALSE, NULL),

    -- The country root. Oversight-scoped, and explicitly NOT an operational reader: the deny that
    -- keeps it out of council case data lands as a policy rule, because an absence of grant can be
    -- out-ranked by a broad admin ALLOW while a DENY cannot.
    ('NATIONAL_TRUST_ADMINISTRATOR', 'National Trust Administration',
     'Platform root trust function: activates regulators and adjudicates bootstrap requests under '
     'four-eyes. A trust role, NOT a superuser — it must never read a regulator''s case data, and '
     'is denied those lanes by policy rather than merely not granted them.',
     FALSE, TRUE, 260, TRUE, FALSE, NULL)

ON CONFLICT (role_code) DO NOTHING;

-- The thirteen V006 roles are operational by definition; state it rather than leaving the default
-- to imply it, so a later reader can tell "not administrative" from "nobody set the flag".
UPDATE org_registry.org_registry_appointment_role
   SET is_administrative = FALSE
 WHERE role_code IN ('REGISTRAR', 'DEPUTY_REGISTRAR', 'REGISTRATION_OFFICER', 'INSPECTOR',
                     'CPD_OFFICER', 'INVESTIGATIONS_OFFICER', 'COMMITTEE_MEMBER', 'FINANCE_OFFICER',
                     'RECORDS_OFFICER', 'LEGAL_OFFICER', 'COUNCIL_CEO', 'HPA_OVERSIGHT_OFFICER',
                     'HPA_INSPECTORATE_OFFICER');

-- ---- Separation of duties, enforced rather than trusted ---------------------------------
--
-- One person may not be both the grantor of access and the party accountable for its use at the
-- same regulator. Expressed as a trigger rather than a constraint because the rule spans rows:
-- it is about the OTHER appointments this person already holds here.

CREATE OR REPLACE FUNCTION org_registry.assert_regulator_duty_separation()
RETURNS TRIGGER AS $$
DECLARE
    conflicting_role TEXT;
BEGIN
    IF NEW.status <> 'ACTIVE' THEN
        RETURN NEW;
    END IF;

    IF NEW.role_code IN ('REGULATOR_SECURITY_ADMINISTRATOR', 'REGULATOR_BUSINESS_OWNER') THEN
        SELECT a.role_code INTO conflicting_role
          FROM org_registry.org_registry_regulatory_appointment a
         WHERE a.tenant_id = NEW.tenant_id
           AND a.organization_id = NEW.organization_id
           AND a.person_health_id = NEW.person_health_id
           AND a.id <> NEW.id
           AND a.status = 'ACTIVE'
           AND a.role_code IN ('REGULATOR_SECURITY_ADMINISTRATOR', 'REGULATOR_BUSINESS_OWNER')
           AND a.role_code <> NEW.role_code
         LIMIT 1;

        IF conflicting_role IS NOT NULL THEN
            RAISE EXCEPTION
                'separation of duties: this person already holds % at this regulator; the security '
                'administrator grants access and the business owner is accountable for its use, so '
                'one person may not hold both', conflicting_role
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_regulator_duty_separation
    ON org_registry.org_registry_regulatory_appointment;
CREATE TRIGGER trg_regulator_duty_separation
    BEFORE INSERT OR UPDATE ON org_registry.org_registry_regulatory_appointment
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_regulator_duty_separation();

-- ---- Supporting index for the administrator-count reads ---------------------------------
-- The succession guard and the bootstrap eligibility check both ask "how many active
-- administrators does this organisation have", on every end() and every bootstrap request.

CREATE INDEX IF NOT EXISTS idx_regulatory_appointment_active_by_org
    ON org_registry.org_registry_regulatory_appointment (tenant_id, organization_id, role_code)
    WHERE status = 'ACTIVE';

-- NOTE deliberately absent:
--   * no ALLOW rule for NATIONAL_TRUST_ADMINISTRATOR anywhere. The country root is established
--     here as an identity; what it may do is decided at the policy layer, and what it may NOT do
--     (read council case data) lands there as an explicit DENY. Granting it broadly here and
--     trimming later would mean shipping a superuser first.
--   * no administrative role marked is_committee_member. Administration of a regulator is not a
--     route to a docket: committee visibility stays case-assigned.
