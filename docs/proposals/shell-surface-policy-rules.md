# Proposal — policy rules for the signed-in shell surface

**Status: DRAFT FOR PO REVIEW. Nothing here has been applied.** No migration is written, no
rule is seeded, and the estate is unchanged. Three decisions at the end are yours, not mine.

## Why this exists

With `ext_authz` enabled, a signed-in citizen session was denied on **17 shell endpoints**
(2026-08-06, browser-verified, rolled back after ~4 minutes). Every denial was
`NO_ALLOW_RULE` — rules were found and evaluated, and none granted the request.

That is not a bug. The estate holds 529 active rules and they are all **domain** rules:
clinical lanes, learning, the confidential lane. Every rule a citizen can match is pinned by
`path_contains` to somewhere else:

```
CITIZEN + workspace-state → conditions: {"path_contains": "/confidential/"}
<any role>                → conditions: {"path_contains": "/learning/v11/enrolments"}
```

There is exactly one rule naming `workspace-state`, and it only fires on the confidential
lane. **The shell's own surface was never authored.** Enabling the gateway gate without it
denies the product to the person using it.

## What the shell asks for

Observed from a real signed-in session, deduplicated. All are `GET`.

| # | endpoint | what it carries |
|---|---|---|
| 1 | `/internal/v1/shell/workspace-state` | which workspace the shell should render |
| 2 | `/internal/v1/session/experience` | the session's own experience/mode |
| 3 | `/internal/v1/nompilo/context` | assistant context for this session |
| 4 | `/internal/v1/profile/visibility` | the person's own visibility preferences |
| 5 | `/internal/v1/identity/assurance/status` | the person's own assurance level |
| 6 | `/internal/v1/identity/affiliations` | the person's own affiliations |
| 7 | `/internal/v1/identity/linked-ids` | the person's own linked identifiers |
| 8 | `/internal/v1/assistant/notifications` | the person's own inbox |
| 9 | `/internal/v1/appointments` | the person's own appointments |
| 10 | `/internal/v1/mobile/citizen/feed` | the person's own feed |
| 11 | `/internal/v1/mobile/citizen/appointments` | the person's own appointments (mobile lane) |
| 12 | `/internal/v1/community/groups` | community directory |
| 13 | `/internal/v1/facilities` | facility directory |

## The safety fact this proposal rests on

**The actor is server-derived, so none of these can be pointed at someone else.**
`ActorContextFilter` overrides `X-Actor-ID` with the `health_id` claim from the validated
bearer JWT, after Spring Security has verified it. Controllers reading
`@RequestHeader(ACTOR_ID)` therefore receive the authenticated person, not a client assertion.
No endpoint above takes a subject as a query parameter.

That is what makes "allow a signed-in person to read this" safe to express as one rule: the
scoping to *self* is enforced in the BFF, not by the rule. **If that filter is ever removed or
bypassed, every rule below becomes a cross-person read.** It is the load-bearing assumption
and should be stated in the migration comment, not left implicit.

## Scope by `path_contains`, never `resource_type`

`deriveResourceType` takes the last non-UUID path segment, which for this surface produces
dangerously generic values:

```
/internal/v1/identity/assurance/status  → "status"
/internal/v1/nompilo/context            → "context"
/internal/v1/mobile/citizen/feed        → "feed"
/internal/v1/community/groups           → "groups"
```

A rule on `resource_type = 'status'` would grant every `…/status` endpoint in the estate.
Every rule below therefore leaves `resource_type` NULL and pins the path — the same idiom the
existing seeds already use, and for the same reason.

`facility_scope` and `workspace_scope` are `false` throughout: a person reading their own shell
has no facility context, and `PolicyEngine` refuses a facility-scoped rule when
`facilityId` is null.

## The proposed rules

Three tiers, because they do not carry the same thing and should not be decided together.

### Tier 1 — Session chrome (rows 1–3)

Carries no personal record data: which workspace to render, which mode the session is in,
assistant context. This is the shell drawing itself.

```sql
INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'shell-chrome-workspace-state',
 'The shell reads which workspace to render for the signed-in person. No record data.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/shell/workspace-state"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-chrome-session-experience',
 'The session''s own experience/mode. Server-derived actor; no subject parameter.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/session/experience"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-chrome-nompilo-context',
 'Assistant context for this session. Nompilo never overrides provider judgement.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/nompilo/context"}', true);
```

Role is NULL deliberately: a provider needs their shell to render too, and pinning these to
CITIZEN would break the Work surface the moment ext_authz is on.

### Tier 2 — The person's own record-adjacent reads (rows 4–11)

Same shape, but these carry personal data — an inbox, appointments, affiliations, linked
identifiers, a feed. Safe only because of the server-derived actor above.

```sql
-- …identical column list…
('…0001'::uuid, 'shell-self-profile-visibility',    …, '{"path_contains": "/profile/visibility"}',        true),
('…0001'::uuid, 'shell-self-assurance-status',      …, '{"path_contains": "/identity/assurance/status"}', true),
('…0001'::uuid, 'shell-self-affiliations',          …, '{"path_contains": "/identity/affiliations"}',     true),
('…0001'::uuid, 'shell-self-linked-ids',            …, '{"path_contains": "/identity/linked-ids"}',       true),
('…0001'::uuid, 'shell-self-notifications',         …, '{"path_contains": "/assistant/notifications"}',   true),
('…0001'::uuid, 'shell-self-appointments',          …, '{"path_contains": "/internal/v1/appointments"}',  true),
('…0001'::uuid, 'shell-self-citizen-feed',          …, '{"path_contains": "/mobile/citizen/feed"}',       true),
('…0001'::uuid, 'shell-self-citizen-appointments',  …, '{"path_contains": "/mobile/citizen/appointments"}', true);
```

**RESOLVED — pin anchored.** An earlier draft warned that `"/appointments"` would also match
`/internal/v1/facilities/{id}/appointments`. **That path does not exist** — zero matches across
every service — so the over-grant I described was hypothetical, and I should have checked
before raising it. The pin is nonetheless anchored to `"/internal/v1/appointments"` rather than
`"/appointments"`, because a bare substring is a standing trap for whatever path someone adds
next, and the anchored form costs nothing today.

### Tier 3 — Directory reads (rows 12–13)

Not personal data. `facilities` is the national register — already public-lane readable — and
`community/groups` is a directory.

```sql
('…0001'::uuid, 'shell-directory-facilities',       …, '{"path_contains": "/internal/v1/facilities"}', true),
('…0001'::uuid, 'shell-directory-community-groups', …, '{"path_contains": "/community/groups"}',       true);
```

## What these rules do NOT grant

- **No writes.** Every rule is `action = 'GET'`. A POST to the same path matches nothing.
- **No cross-person reads.** Enforced by `ActorContextFilter`, not by these rules.
- **No clinical access.** None of these paths reach PCT, OROS, pharmacy or the SHR.
- **No override of a DENY.** DENY wins because rules are ordered `effect DESC` *before*
  priority — a DENY is evaluated first whatever its number, so a specially-protected or
  work-mode boundary rule still bites. (An earlier draft of this line said "priority 70, below
  every existing DENY", which was wrong twice over: the highest active DENY is priority 50, and
  priority does not decide DENY-vs-ALLOW precedence at all. Priority 70 matters only for
  ordering *among ALLOWs* — it places these after the existing 40–60 lane rules, so a narrower
  lane rule is still consulted first.)
- **No effect on the confidential lane**, which keeps its own narrower rules at priority 40.

## The three open decisions — now resolved, with one correction

**1. Assurance floor — RESOLVED: no floor, and the reason is measured.** Across every decision
the PDP has logged: **780 null, 25 UNVERIFIED, 62 LOA3.** A `min_loa` / `min_aal` condition
would therefore deny roughly **93%** of real decisions, including the browser session that
prompted this proposal. A floor today is not a control, it is the same outage with a better
name.

The measurement also surfaces something worth its own look: assurance *can* be populated —
LOA3 appears 62 times — but is absent from the overwhelming majority of decisions. **Gating on
a signal that is populated 7% of the time gates on nothing and denies everything.** Fix the
signal first, then choose the floor. Recorded here as a finding, not fixed in this proposal.

**2. `/appointments` pin width.** As above. Substring matching makes this rule broader than
its name suggests.

**3. Role-agnostic vs per-role — RESOLVED: role-agnostic.** These are *self*-scoped reads: the
scoping is done by actor identity via `ActorContextFilter`, not by role. Role is the wrong axis
here, and duplicating each rule per role would add maintenance while failing silently for any
role the duplication misses — a provider's shell going blank the first time someone adds a
cadre. Role-based narrowing belongs where roles actually differentiate capability, which is the
clinical surface, not shell chrome.

## How to verify this worked, and how to know if it did not

Applying these is not the proof. The acceptance test is the one that caught both failed
cutovers:

1. `bash scripts/test/verify-extauthz-cutover-readiness.sh` → expect 6/6.
2. Flip `envoy.extAuthz.enabled`, restart Envoy.
3. **Sign in through a browser and count denials on the shell endpoints.** Zero is the pass
   condition. Anything else rolls back.

`NO_ALLOW_RULE` on a shell endpoint after this lands means a rule is missing or mis-pinned.
`NO_MATCHING_RULES` would mean the plane merge regressed. The two are different failures and
the PDP log distinguishes them.

## Suggested landing

Migration `V309__shell_surface_policy_rules.sql` in `tshepo-authz-service` (current head is
**V308**), seeded `active = true` (unlike V055's boundary rules, which are staged inert — these grant rather than deny, so
seeding them inactive would mean applying a migration that changes nothing and forgetting the
second step). Out-of-order migration is enabled for this service, so V309 applies cleanly.
