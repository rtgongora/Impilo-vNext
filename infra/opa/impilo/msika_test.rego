package impilo.msika

import future.keywords

citizen(action, subject) := {
	"action": action,
	"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
	"subject_ref": subject,
	"assurance_loa": 2,
}

# ── Discovery ─────────────────────────────────────────────────────────────────────────
test_marketplace_view_allowed if {
	allow with input as citizen("msika.marketplace.view", "CPID-1")
}

test_search_allowed if {
	allow with input as citizen("msika.search", "CPID-1")
}

test_listing_view_allowed if {
	allow with input as citizen("msika.listing.view", "CPID-1")
}

test_no_anchor_denied if {
	not allow with input as {
		"action": "msika.search",
		"identity": {"health_id": "", "identity_type": "citizen"},
	}
}

test_regulated_listing_view_denied_low_trust if {
	not allow with input as object.union(citizen("msika.listing.view", "CPID-1"), {"assurance_loa": 1, "listing": {"risk": "REGULATED"}})
}

test_regulated_listing_view_allowed_loa2 if {
	allow with input as object.union(citizen("msika.listing.view", "CPID-1"), {"listing": {"risk": "REGULATED"}})
}

# ── Buyer txn self ────────────────────────────────────────────────────────────────────
test_txn_self_lowrisk_allowed if {
	allow with input as object.union(citizen("msika.txn.create.self", "CPID-1"), {"listing": {"risk": "LOW_RISK"}})
}

test_txn_self_regulated_denied_without_eligibility if {
	not allow with input as object.union(citizen("msika.txn.create.self", "CPID-1"), {"assurance_loa": 3, "listing": {"risk": "REGULATED"}})
}

test_txn_self_regulated_allowed_with_eligibility_and_trust if {
	allow with input as object.union(citizen("msika.txn.create.self", "CPID-1"), {"assurance_loa": 3, "listing": {"risk": "REGULATED"}, "eligibility": {"met": true}})
}

test_txn_self_regulated_denied_low_trust if {
	not allow with input as object.union(citizen("msika.txn.create.self", "CPID-1"), {"assurance_loa": 2, "listing": {"risk": "REGULATED"}, "eligibility": {"met": true}})
}

# ── Buyer txn dependant ───────────────────────────────────────────────────────────────
test_txn_dependant_denied_without_delegation if {
	not allow with input as object.union(citizen("msika.txn.create.dependant", "CPID-CHILD"), {"listing": {"risk": "LOW_RISK"}})
}

test_txn_dependant_allowed_with_delegation if {
	allow with input as object.union(citizen("msika.txn.create.dependant", "CPID-CHILD"), {"listing": {"risk": "LOW_RISK"}, "delegation": {"active": true}})
}

# ── Txn views + lifecycle ─────────────────────────────────────────────────────────────
test_buyer_txn_view_allowed_when_owner if {
	allow with input as object.union(citizen("msika.txn.view.buyer", "CPID-1"), {"txn": {"owner": true}})
}

test_buyer_txn_view_denied_when_not_owner if {
	not allow with input as object.union(citizen("msika.txn.view.buyer", "CPID-1"), {"txn": {"owner": false}})
}

test_billing_status_view_allowed_owner if {
	allow with input as object.union(citizen("msika.billing.status.view", "CPID-1"), {"txn": {"owner": true}})
}

test_payment_status_view_allowed_owner if {
	allow with input as object.union(citizen("msika.payment.status.view", "CPID-1"), {"txn": {"owner": true}})
}

test_fulfilment_status_view_allowed_owner if {
	allow with input as object.union(citizen("msika.fulfilment.status.view", "CPID-1"), {"txn": {"owner": true}})
}

test_cancel_allowed_owner if {
	allow with input as object.union(citizen("msika.txn.cancel", "CPID-1"), {"txn": {"owner": true}})
}

test_refund_denied_when_not_owner if {
	not allow with input as object.union(citizen("msika.txn.refund", "CPID-1"), {"txn": {"owner": false}})
}

# ── Seller authoring ──────────────────────────────────────────────────────────────────
provider(action) := {
	"action": action,
	"identity": {"health_id": "PROV-1", "identity_type": "provider"},
	"assurance_loa": 3,
	"seller": {"role": "provider", "verified": true},
}

test_create_listing_provider_allowed if {
	allow with input as provider("msika.listing.create.provider")
}

test_create_listing_provider_denied_unverified if {
	not allow with input as object.union(provider("msika.listing.create.provider"), {"seller": {"role": "provider", "verified": false}})
}

test_create_listing_facility_allowed if {
	allow with input as {
		"action": "msika.listing.create.facility",
		"identity": {"health_id": "FAC-1", "identity_type": "facility"},
		"assurance_loa": 3,
		"seller": {"role": "facility", "verified": true},
	}
}

test_publish_lowrisk_allowed_verified if {
	allow with input as object.union(provider("msika.listing.publish"), {"listing": {"risk": "LOW_RISK"}})
}

test_publish_regulated_denied_low_trust if {
	not allow with input as object.union(provider("msika.listing.publish"), {"assurance_loa": 2, "listing": {"risk": "REGULATED"}})
}

test_publish_denied_unverified if {
	not allow with input as object.union(provider("msika.listing.publish"), {"seller": {"role": "provider", "verified": false}, "listing": {"risk": "LOW_RISK"}})
}

# ── Moderation ────────────────────────────────────────────────────────────────────────
operator(action) := {
	"action": action,
	"identity": {"health_id": "OP-1", "identity_type": "operator"},
	"assurance_loa": 3,
	"programme": {"role": true},
}

test_approve_allowed_with_programme_role if {
	allow with input as operator("msika.listing.approve")
}

test_reject_allowed_with_programme_role if {
	allow with input as operator("msika.listing.reject")
}

test_suspend_allowed_with_programme_role if {
	allow with input as operator("msika.listing.suspend")
}

test_approve_denied_without_programme_role if {
	not allow with input as object.union(operator("msika.listing.approve"), {"programme": {"role": false}})
}

# ── Seller centre ─────────────────────────────────────────────────────────────────────
test_seller_centre_view_allowed_for_seller if {
	allow with input as provider("msika.seller.centre.view")
}

test_seller_centre_view_denied_without_seller_role if {
	not allow with input as citizen("msika.seller.centre.view", "CPID-1")
}

# ── Rito + Khuluma ────────────────────────────────────────────────────────────────────
test_feedback_link_allowed if {
	allow with input as citizen("msika.feedback.link", "CPID-1")
}

test_khuluma_request_allowed if {
	allow with input as citizen("msika.khuluma.request", "CPID-1")
}

# ── Programme + reporting ─────────────────────────────────────────────────────────────
test_programme_manage_allowed if {
	allow with input as operator("msika.programme.manage")
}

test_aggregate_report_allowed if {
	allow with input as object.union(operator("msika.report.view"), {"assurance_loa": 2, "report": {"aggregate_only": true}})
}

test_user_level_report_denied_low_trust if {
	not allow with input as object.union(operator("msika.report.view"), {"assurance_loa": 2, "report": {"aggregate_only": false}})
}

test_user_level_report_allowed_high_trust if {
	allow with input as object.union(operator("msika.report.view"), {"assurance_loa": 3, "report": {"aggregate_only": false}})
}

# ── Forbidden owner-domain actions ────────────────────────────────────────────────────
test_stock_adjust_forbidden if {
	not allow with input as citizen("msika.stock.adjust", "CPID-1")
}

test_payment_capture_forbidden if {
	not allow with input as citizen("msika.payment.capture", "CPID-1")
}

# ── Decision surface ──────────────────────────────────────────────────────────────────
test_decision_surfaces_no_delegation if {
	d := decision with input as object.union(citizen("msika.txn.create.dependant", "CPID-CHILD"), {"listing": {"risk": "LOW_RISK"}})
	d.allow == false
	"NO_DELEGATION" in d.deny_reasons
}

test_decision_surfaces_eligibility_not_met if {
	d := decision with input as object.union(citizen("msika.txn.create.self", "CPID-1"), {"assurance_loa": 3, "listing": {"risk": "REGULATED"}})
	"ELIGIBILITY_NOT_MET" in d.deny_reasons
}

test_decision_surfaces_seller_not_verified if {
	d := decision with input as object.union(provider("msika.listing.publish"), {"seller": {"role": "provider", "verified": false}, "listing": {"risk": "LOW_RISK"}})
	"SELLER_NOT_VERIFIED" in d.deny_reasons
}

test_decision_surfaces_not_txn_owner if {
	d := decision with input as object.union(citizen("msika.txn.refund", "CPID-1"), {"txn": {"owner": false}})
	"NOT_TXN_OWNER" in d.deny_reasons
}

test_decision_surfaces_forbidden if {
	d := decision with input as citizen("msika.stock.adjust", "CPID-1")
	"FORBIDDEN_OWNER_DOMAIN_ACTION" in d.deny_reasons
}
