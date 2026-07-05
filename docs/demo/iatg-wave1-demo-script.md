# IATG Wave 1 — Demo Script

End-to-end walkthrough of the Identity, Access and Trust Governance Wave 1 slice: from an
empty platform to an honestly-statused provider and facility, proving the doctrine in
[`docs/doctrine/identity-access-trust-governance.md`](../doctrine/identity-access-trust-governance.md)
(section references below). Delivery boundaries:
[`docs/registry/iatg-wave1-leases.md`](../registry/iatg-wave1-leases.md).

> **Honesty framing to state up front**: at no point in this demo is anything "made
> valid" by an admin override. Every status shown is what a source actually says; every
> allowance is a recorded platform decision with a reason.

## 1. Origin admin creates Country Operation Zimbabwe (two approvals)

**Doctrine**: §1 Platform Origin Authority, §2 Country/National Administration.
The Platform Origin Administrator initiates **Create Country Operation: Zimbabwe**
(country, jurisdiction, governing authority = MoHCC, legal framework binding).
**Show**: the action parks in a pending state — it cannot take effect on one key. A
*second, independent* origin approver confirms. Only then does the country operation
exist. **Also show**: both origin actions in the audit trail, each with actor and
justification.

## 2. Origin admin appoints the National Administrator

**Doctrine**: §2. The origin admin appoints the first National Administrator for
Zimbabwe, recording appointment source, date, appointed-by, expiry, and scope (second
origin approval again required). **Show**: the appointment record with all governance
attributes, and that from here on, routine governance actions are performed by the
national administrator — the origin key steps back (origin-as-daily-operator would be an
audit flag).

## 3. Organization onboarded and verified

**Doctrine**: §5.2 organization model, §8 Channel C. The national administrator (or a
delegated governance role) registers an organization — e.g. a mission health organization
— with type and jurisdiction, then verifies its **authorized representatives** (real
Health IDs, scoped, expiring). Evidence is checked against national authority records.
**Show**: the organization goes operational with honest legitimacy status; an unverified
affiliation claim stays `pending`, visibly.

## 4. Provider preloaded via Channel A

**Doctrine**: §6 Regulatory Authority Channel. Council register data is preloaded: a
provider record for "Dr. T. Moyo" is created from the council register with a
**pre-assigned, confidential Provider ID** in `pending` linkage state, registry status
`registered-active`. **Show**: the provider exists in the registry *before any sign-up*,
and the council number is stored as **matching evidence**, not as the identity (§4).

## 5. Citizen claims the Provider ID

**Doctrine**: §3, §4, §10. A person signs in with their **Health ID** (person first) and
claims their professional identity, supplying the council number as matching evidence.
The claim matches the preloaded Channel A record — no duplicate Provider ID is created.
**Show**: the linkage moves from `pending` to matched; **and** that holding the Provider
ID grants nothing yet — a professional action attempted immediately after claiming is
still refused by policy, because a Provider ID request is never a shortcut into
privileges (§10).

## 6. EC evidence upgrades trust to EMPLOYMENT_MATCHED

**Doctrine**: §7 Channel B, §9 trust blocks. The provider's EC number is matched against
MoHCC/HSC employment and posting records. **Show**: the provider's Employment Trust block
gains `EC matched` and `posting verified`, and the composite provider state reaches
**EMPLOYMENT_MATCHED** (`matched-both` when council + employment both hold). Professional
Trust was not touched by this step — the blocks move independently.

## 7. GET trust profile shows four honest blocks

**Doctrine**: §9. Fetch the provider's composed trust profile via the Experience BFF
(composition only — the BFF is not a system of record). **Show** all four blocks with
per-fact sources and honest gaps:

| Block | Example state shown |
|---|---|
| Identity Trust | Health ID verified · biographic verified · contact **unverified** |
| Professional Trust | Council registration confirmed (Channel A) · licence current |
| Employment Trust | EC matched · posting verified (Channel B) · supervisor **not yet confirmed** |
| Operational Trust | Facility assignment active · workspace active · shift **not active** |

Point out: this is deliberately not one "verified ✓" flag — every consumer can see what
is verified, by whom, and what remains open.

## 8. Facility composite shows per-source legitimacy with reason

**Doctrine**: §5.1, §7. Fetch the composite view of a rural public facility. **Show** the
per-source legitimacy exactly as the doctrine requires — *"this facility exists, this is
who says it exists, this is its status, this is whether the platform allows it to
operate, and why"*:

| Source | Status |
|---|---|
| `HPA_LEGAL` | **EXPIRED** |
| `MINISTRY_OPERATIONAL` | **REGISTERED_CURRENT** |
| Platform decision | **GOVERNMENT_OPERATIONAL_EXCEPTION** — with recorded reason (public facility operating under ministry authority; HPA licence lapsed; exception authority, scope and expiry recorded) |

Close by contrasting: a *private* facility with the same `HPA_LEGAL: EXPIRED` would get
the honest status and an adjudication path (§11) — never the exception automatically,
because the sovereign operational exception belongs to government alone (§7).
