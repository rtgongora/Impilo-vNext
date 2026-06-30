package impilo.msika

import future.keywords

# Msika — health marketplace / storefront-lane access policy.
#
# Msika is the marketplace DISCOVERY + transaction-INTENT lane. It never owns
# billing (Costa), payments (MusheX), stock (inventory/Dura), clinical workflow
# (PCT/OROS), comms (Khuluma), guidance (Nompilo) or feedback (Rito). This policy
# gates Msika's own actions: browse/search/detail, buy self vs dependant, the
# regulated-item gate, seller listing authoring (provider vs facility/org),
# publish, approve/reject/suspend, seller-centre, buyer/seller transaction views,
# cancel/refund, billing/payment/fulfilment status views, Rito link, Khuluma
# request, low-trust restriction, programme-sponsored management, and aggregate
# vs user-level reporting.
#
# Input contract (resolved by the BFF / Tshepo gate):
#   input.action                 one of msika_actions below
#   input.identity.health_id     caller Health ID (person anchor)
#   input.identity.identity_type "citizen" | "provider" | "operator" | "facility" | ...
#   input.assurance_loa          integer 1..4 (LoA)
#   input.subject_ref            person the transaction targets (self or dependant)
#   input.delegation.active      bool — caller has an active delegation for subject
#   input.listing.risk           "UNRESTRICTED" | "LOW_RISK" | "MODERATE_RISK" | "HIGH_RISK" | "REGULATED"
#   input.eligibility.met        bool — caller meets the regulated-item eligibility/licence gate
#   input.seller.role            "provider" | "facility" | "site" | "programme" | "vendor" | null
#   input.seller.verified        bool — seller storefront is VERIFIED (Varapi/Tuso/Indawo)
#   input.programme.role         bool — caller holds a lawful programme/admin role
#   input.report.aggregate_only  bool — request is for aggregate (non-identifying) data
#   input.txn.owner              bool — caller owns this transaction (buyer) or seller of it

default allow := false

msika_actions := {
	"msika.marketplace.view",
	"msika.search",
	"msika.listing.view",
	"msika.txn.create.self",
	"msika.txn.create.dependant",
	"msika.txn.view.buyer",
	"msika.txn.view.seller",
	"msika.txn.cancel",
	"msika.txn.refund",
	"msika.billing.status.view",
	"msika.payment.status.view",
	"msika.fulfilment.status.view",
	"msika.listing.create.provider",
	"msika.listing.create.facility",
	"msika.listing.publish",
	"msika.listing.approve",
	"msika.listing.reject",
	"msika.listing.suspend",
	"msika.seller.centre.view",
	"msika.feedback.link",
	"msika.khuluma.request",
	"msika.programme.manage",
	"msika.report.view",
}

# Msika must never expose owner-domain truth actions directly — defence in depth.
forbidden_actions := {
	"msika.stock.adjust",
	"msika.payment.capture",
	"msika.billing.write",
	"msika.clinical.order.write",
}

has_person_anchor if {
	input.identity.health_id != ""
}

is_self if {
	input.subject_ref == input.identity.health_id
}

is_self if {
	input.subject_ref == sprintf("Patient/%s", [input.identity.health_id])
}

# Subject defaults to self when a transaction is not subject-scoped.
is_self if {
	not input.subject_ref
}

effective_loa := loa if {
	is_number(input.assurance_loa)
	loa := input.assurance_loa
}

effective_loa := 1 if {
	not is_number(input.assurance_loa)
}

regulated_risk if {
	input.listing.risk in {"HIGH_RISK", "REGULATED"}
}

# ── Discovery: any authenticated person can browse/search/view detail ─────────────────
discovery_actions := {"msika.marketplace.view", "msika.search", "msika.listing.view"}

allow if {
	input.action in discovery_actions
	has_person_anchor
	not regulated_risk
}

# Viewing a regulated listing's detail is allowed (the friction is at checkout), but the
# caller must at least have a person anchor at LoA >= 2 (no anonymous regulated browsing).
allow if {
	input.action == "msika.listing.view"
	has_person_anchor
	regulated_risk
	effective_loa >= 2
}

# ── Buyer transaction intent: self ────────────────────────────────────────────────────
allow if {
	input.action == "msika.txn.create.self"
	has_person_anchor
	is_self
	not regulated_risk
}

# Regulated/high-risk self purchase requires eligibility/licence + elevated trust.
allow if {
	input.action == "msika.txn.create.self"
	has_person_anchor
	is_self
	regulated_risk
	input.eligibility.met == true
	effective_loa >= 3
}

# ── Buyer transaction intent: dependant (requires active delegation) ──────────────────
allow if {
	input.action == "msika.txn.create.dependant"
	has_person_anchor
	not is_self
	input.delegation.active == true
	not regulated_risk
}

allow if {
	input.action == "msika.txn.create.dependant"
	has_person_anchor
	not is_self
	input.delegation.active == true
	regulated_risk
	input.eligibility.met == true
	effective_loa >= 3
}

# ── Transaction views + lifecycle (buyer owns / seller owns) ──────────────────────────
allow if {
	input.action in {"msika.txn.view.buyer", "msika.txn.cancel", "msika.billing.status.view", "msika.payment.status.view", "msika.fulfilment.status.view"}
	has_person_anchor
	input.txn.owner == true
}

allow if {
	input.action == "msika.txn.view.seller"
	has_person_anchor
	input.txn.owner == true
	input.seller.role != null
}

# Refunds: the buyer who owns the transaction may request a refund (routed to the owner).
allow if {
	input.action == "msika.txn.refund"
	has_person_anchor
	input.txn.owner == true
}

# ── Seller listing authoring ──────────────────────────────────────────────────────────
allow if {
	input.action == "msika.listing.create.provider"
	has_person_anchor
	input.seller.role == "provider"
	input.seller.verified == true
}

allow if {
	input.action == "msika.listing.create.facility"
	has_person_anchor
	input.seller.role in {"facility", "site", "programme"}
	input.seller.verified == true
}

# Publishing a listing — seller must be verified; regulated listings additionally need elevated trust.
allow if {
	input.action == "msika.listing.publish"
	has_person_anchor
	input.seller.verified == true
	not regulated_risk
}

allow if {
	input.action == "msika.listing.publish"
	has_person_anchor
	input.seller.verified == true
	regulated_risk
	effective_loa >= 3
}

# ── Moderation / governance (operator/programme role, no admin bypass) ────────────────
allow if {
	input.action in {"msika.listing.approve", "msika.listing.reject", "msika.listing.suspend"}
	input.programme.role == true
}

# ── Seller centre ─────────────────────────────────────────────────────────────────────
allow if {
	input.action == "msika.seller.centre.view"
	has_person_anchor
	input.seller.role != null
}

# ── Rito feedback link + Khuluma update request: any authenticated person ─────────────
allow if {
	input.action in {"msika.feedback.link", "msika.khuluma.request"}
	has_person_anchor
}

# ── Programme management + reporting ──────────────────────────────────────────────────
allow if {
	input.action == "msika.programme.manage"
	input.programme.role == true
}

allow if {
	input.action == "msika.report.view"
	input.report.aggregate_only == true
	input.programme.role == true
}

allow if {
	input.action == "msika.report.view"
	input.report.aggregate_only == false
	input.programme.role == true
	effective_loa >= 3
}

# ── Low-trust restriction: transactional/seller actions need LoA >= 2 ─────────────────
low_trust_gated := {
	"msika.txn.create.self",
	"msika.txn.create.dependant",
	"msika.listing.create.provider",
	"msika.listing.create.facility",
	"msika.listing.publish",
}

# ── Machine-readable decision (mirrors Tshepo Decision shape) ─────────────────────────

deny_reasons contains "NO_PERSON_ANCHOR" if {
	input.action in msika_actions
	not has_person_anchor
}

deny_reasons contains "FORBIDDEN_OWNER_DOMAIN_ACTION" if {
	input.action in forbidden_actions
}

deny_reasons contains "NO_DELEGATION" if {
	input.action == "msika.txn.create.dependant"
	not is_self
	not input.delegation.active == true
}

deny_reasons contains "ELIGIBILITY_NOT_MET" if {
	input.action in {"msika.txn.create.self", "msika.txn.create.dependant"}
	regulated_risk
	not input.eligibility.met == true
}

deny_reasons contains "TRUST_TOO_LOW" if {
	input.action in low_trust_gated
	regulated_risk
	effective_loa < 3
}

deny_reasons contains "SELLER_NOT_VERIFIED" if {
	input.action in {"msika.listing.create.provider", "msika.listing.create.facility", "msika.listing.publish"}
	not input.seller.verified == true
}

deny_reasons contains "NO_PROGRAMME_ROLE" if {
	input.action in {"msika.listing.approve", "msika.listing.reject", "msika.listing.suspend", "msika.programme.manage"}
	not input.programme.role == true
}

deny_reasons contains "NOT_TXN_OWNER" if {
	input.action in {"msika.txn.view.buyer", "msika.txn.cancel", "msika.txn.refund", "msika.billing.status.view", "msika.payment.status.view", "msika.fulfilment.status.view"}
	not input.txn.owner == true
}

deny_reasons contains "USER_LEVEL_TRUST_TOO_LOW" if {
	input.action == "msika.report.view"
	input.report.aggregate_only == false
	input.programme.role == true
	effective_loa < 3
}

decision := {
	"allow": allow,
	"deny_reasons": sort([r | some r in deny_reasons]),
}
