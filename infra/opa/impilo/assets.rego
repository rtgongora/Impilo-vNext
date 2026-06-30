package impilo.assets

import future.keywords

# ─────────────────────────────────────────────────────────────────────────────
# WS#5 — Assets, Devices, Equipment & IoT authorization (asset-registry-service).
#
# Owns operational asset/equipment/device authorization. Boundary doctrine:
#   * Dura/inventory stock functions are NEVER exposed as asset functions.
#   * Facility (Tuso/Indawo), workforce (Vashandi), clinical record (Butano) are
#     referenced, not owned — cross-facility access is scope-gated.
#
# Input contract (subset):
#   input.action                 e.g. "assets.equipment.register"
#   input.identity.health_id     person anchor (required for any access)
#   input.identity.identity_type "citizen" | "provider" | "staff" | "system"
#   input.role_template          e.g. "asset_facility_admin", "asset_technician"
#   input.assurance_loa          integer LOA
#   input.actor_facility_id      facility the actor is operating in
#   input.target_facility_id     facility of the target asset
#   input.technician_assignment  truthy when the actor is the assigned technician
#   input.elevated_approval      truthy when an elevated approver is present
# ─────────────────────────────────────────────────────────────────────────────

default allow := false

# Anchor: every asset action needs a person anchor.
has_anchor if {
	input.identity.health_id != ""
	input.identity.health_id != null
}

# Citizens never operate the asset registry (operational-staff domain).
is_citizen if input.identity.identity_type == "citizen"

# Action classes ----------------------------------------------------------------

read_actions := {
	"assets.registry.view",
	"assets.facility.view",
	"assets.detail.view",
	"assets.iot.view",
	"assets.readiness.view",
	"assets.reports.aggregate",
}

write_actions := {
	"assets.equipment.register",
	"assets.equipment.edit_metadata",
	"assets.equipment.assign",
	"assets.equipment.transfer",
	"assets.equipment.confirm_receipt",
	"assets.fault.report",
	"assets.equipment.mark_unsafe",
	"assets.maintenance.create",
	"assets.maintenance.update",
	"assets.maintenance.complete",
	"assets.calibration.record",
	"assets.equipment.return_to_service",
	"assets.iot.resolve_alert",
	"assets.deployment.assign_kit",
	"assets.deployment.field",
	"assets.audit.conduct",
}

elevated_actions := {
	"assets.equipment.retire",
	"assets.equipment.dispose",
}

# Stock functions that must NOT be served by the asset domain (Dura/inventory own them).
stock_actions := {
	"inventory.stock.adjust",
	"inventory.stock.receive",
	"inventory.stock.issue",
	"assets.stock.adjust",
	"assets.stock.issue",
}

operational_role if {
	input.role_template in {
		"asset_facility_admin",
		"asset_biomedical_engineer",
		"asset_technician",
		"asset_field_officer",
		"asset_manager",
		"platform_admin",
	}
}

# ── DENY rules (evaluated first by virtue of default-false + explicit deny gate) ──

# Dura/stock functions are never exposed as asset functions.
deny if input.action in stock_actions

# Citizens / low-trust are denied the operational domain.
deny if {
	input.action in (read_actions | write_actions | elevated_actions)
	is_citizen
}

deny if {
	input.action in (write_actions | elevated_actions)
	input.assurance_loa < 2
}

# Cross-facility access requires an explicit aggregate/cross-facility scope.
deny if {
	input.action in write_actions
	input.target_facility_id != ""
	input.actor_facility_id != ""
	input.target_facility_id != input.actor_facility_id
	not input.cross_facility_scope
}

# Technician-scoped maintenance: a plain technician may only act on tasks assigned to them.
deny if {
	input.action in {"assets.maintenance.update", "assets.maintenance.complete", "assets.equipment.return_to_service"}
	input.role_template == "asset_technician"
	not input.technician_assignment
}

# Retire/dispose require an elevated approver.
deny if {
	input.action in elevated_actions
	not input.elevated_approval
}

# Linking a device to a clinical record requires clinical context (Butano owns the record).
deny if {
	input.action == "assets.iot.link_clinical"
	not input.clinical_context
}

# ── ALLOW rules ────────────────────────────────────────────────────────────────

allow if {
	has_anchor
	not is_citizen
	operational_role
	input.action in read_actions
	not denied
}

allow if {
	has_anchor
	not is_citizen
	operational_role
	input.action in write_actions
	input.assurance_loa >= 2
	not denied
}

allow if {
	has_anchor
	not is_citizen
	operational_role
	input.action in elevated_actions
	input.elevated_approval
	input.assurance_loa >= 2
	not denied
}

# Provider equipment-use: a provider may view/use equipment in their facility context.
allow if {
	has_anchor
	input.identity.identity_type == "provider"
	input.action == "assets.equipment.use"
	input.actor_facility_id != ""
	not denied
}

# Linking device to clinical record — allowed only with clinical context + operational role.
allow if {
	has_anchor
	operational_role
	input.action == "assets.iot.link_clinical"
	input.clinical_context
	not denied
}

denied if deny
