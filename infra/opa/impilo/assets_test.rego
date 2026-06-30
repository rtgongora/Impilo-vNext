package impilo.assets

import future.keywords

# Base: an asset_facility_admin operating in FAC-1 at LOA 2.
admin(action) := {
	"action": action,
	"identity": {"health_id": "HID-1", "identity_type": "staff"},
	"role_template": "asset_facility_admin",
	"assurance_loa": 2,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
}

technician(action) := {
	"action": action,
	"identity": {"health_id": "HID-T", "identity_type": "staff"},
	"role_template": "asset_technician",
	"assurance_loa": 2,
	"actor_facility_id": "FAC-1",
	"target_facility_id": "FAC-1",
}

# ── Read surfaces ──────────────────────────────────────────────────────────────
test_view_registry_allowed if { allow with input as admin("assets.registry.view") }
test_view_facility_allowed if { allow with input as admin("assets.facility.view") }
test_view_detail_allowed if { allow with input as admin("assets.detail.view") }
test_view_iot_allowed if { allow with input as admin("assets.iot.view") }
test_view_readiness_allowed if { allow with input as admin("assets.readiness.view") }
test_aggregate_reports_allowed if { allow with input as admin("assets.reports.aggregate") }

# ── Write surfaces ─────────────────────────────────────────────────────────────
test_register_allowed if { allow with input as admin("assets.equipment.register") }
test_edit_metadata_allowed if { allow with input as admin("assets.equipment.edit_metadata") }
test_assign_allowed if { allow with input as admin("assets.equipment.assign") }
test_transfer_allowed if { allow with input as admin("assets.equipment.transfer") }
test_confirm_receipt_allowed if { allow with input as admin("assets.equipment.confirm_receipt") }
test_report_fault_allowed if { allow with input as admin("assets.fault.report") }
test_mark_unsafe_allowed if { allow with input as admin("assets.equipment.mark_unsafe") }
test_maintenance_create_allowed if { allow with input as admin("assets.maintenance.create") }
test_record_calibration_allowed if { allow with input as admin("assets.calibration.record") }
test_assign_kit_allowed if { allow with input as admin("assets.deployment.assign_kit") }
test_field_deployment_allowed if { allow with input as admin("assets.deployment.field") }
test_asset_audit_allowed if { allow with input as admin("assets.audit.conduct") }

# ── Retire/dispose require elevated approval ───────────────────────────────────
test_retire_denied_without_approval if {
	not allow with input as admin("assets.equipment.retire")
}
test_retire_allowed_with_approval if {
	allow with input as object.union(admin("assets.equipment.retire"), {"elevated_approval": true})
}
test_dispose_denied_without_approval if {
	not allow with input as admin("assets.equipment.dispose")
}

# ── Citizen / low-trust DENY ───────────────────────────────────────────────────
test_citizen_view_denied if {
	not allow with input as {
		"action": "assets.registry.view",
		"identity": {"health_id": "CPID-1", "identity_type": "citizen"},
		"role_template": "asset_facility_admin",
		"assurance_loa": 3,
	}
}
test_low_trust_write_denied if {
	not allow with input as object.union(admin("assets.equipment.register"), {"assurance_loa": 1})
}
test_no_anchor_denied if {
	not allow with input as {"action": "assets.registry.view", "identity": {"health_id": "", "identity_type": "staff"}, "role_template": "asset_facility_admin", "assurance_loa": 2}
}

# ── Dura stock functions NOT exposed as asset functions ────────────────────────
test_stock_adjust_denied if {
	not allow with input as object.union(admin("inventory.stock.adjust"), {})
}
test_asset_stock_alias_denied if {
	not allow with input as object.union(admin("assets.stock.issue"), {})
}

# ── Cross-facility scoped ──────────────────────────────────────────────────────
test_cross_facility_denied_without_scope if {
	not allow with input as object.union(admin("assets.equipment.transfer"), {"target_facility_id": "FAC-2"})
}
test_cross_facility_allowed_with_scope if {
	allow with input as object.union(admin("assets.equipment.transfer"), {"target_facility_id": "FAC-2", "cross_facility_scope": true})
}

# ── Technician requires assignment ─────────────────────────────────────────────
test_technician_update_denied_without_assignment if {
	not allow with input as technician("assets.maintenance.update")
}
test_technician_update_allowed_with_assignment if {
	allow with input as object.union(technician("assets.maintenance.update"), {"technician_assignment": true})
}
test_technician_return_to_service_denied_without_assignment if {
	not allow with input as technician("assets.equipment.return_to_service")
}

# ── Facility-admin by context (operational role gate) ──────────────────────────
test_non_operational_role_denied if {
	not allow with input as object.union(admin("assets.equipment.register"), {"role_template": "citizen_basic"})
}

# ── Provider equipment-use by context ──────────────────────────────────────────
test_provider_equipment_use_allowed if {
	allow with input as {
		"action": "assets.equipment.use",
		"identity": {"health_id": "PRV-1", "identity_type": "provider"},
		"assurance_loa": 2,
		"actor_facility_id": "FAC-1",
	}
}
test_provider_equipment_use_denied_without_facility if {
	not allow with input as {
		"action": "assets.equipment.use",
		"identity": {"health_id": "PRV-1", "identity_type": "provider"},
		"assurance_loa": 2,
		"actor_facility_id": "",
	}
}

# ── Resolve IoT alert ──────────────────────────────────────────────────────────
test_resolve_alert_allowed if { allow with input as admin("assets.iot.resolve_alert") }

# ── Link device to clinical record needs clinical context ──────────────────────
test_link_clinical_denied_without_context if {
	not allow with input as admin("assets.iot.link_clinical")
}
test_link_clinical_allowed_with_context if {
	allow with input as object.union(admin("assets.iot.link_clinical"), {"clinical_context": true})
}
