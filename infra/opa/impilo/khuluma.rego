package impilo.khuluma

# Khuluma — Impilo Comms Hub authorization policy.
# Governs who may converse, call, and link comms to clinical objects. Native in-app realtime
# is the default surface; clinical content must never leave over an external/insecure channel.
# Evaluated by the Tshepo PDP at the edge (ext_authz); the experience-bff KhulumaAccessPolicyService
# is a defense-in-depth pre-check that mirrors these rules.

import future.keywords.in

default allow := false

# Actions Khuluma governs.
comms_actions := {
    "comms.conversation.create",
    "comms.message.send",
    "comms.call.start",
    "comms.presence.update",
    "comms.conversation.link_patient",
}

# ── Denies (override allow) ─────────────────────────────────────────────────

# Suspended / offboarded organisations lose comms immediately.
deny if {
    input.organisation.lifecycle_status in {"suspended", "offboarded", "blacklisted", "revoked"}
}

# Provider (non-citizen) comms requires an active organisation assignment (regulated capacity).
deny if {
    input.action in comms_actions
    input.identity.identity_type != "citizen"
    not input.organisation.active_organisation_assignment
}

# Linking a conversation to a patient is a regulated clinical action — clinical provider only.
deny if {
    input.action == "comms.conversation.link_patient"
    not is_clinical_provider
}

# Clinical content must never go over an external/insecure transport (native realtime only).
deny if {
    input.action == "comms.message.send"
    input.message.contains_clinical_content
    input.channel.transport == "external"
    not input.channel.secure
}

# Vendor / marketplace organisations cannot participate in patient-linked clinical comms.
deny if {
    input.action in {"comms.message.send", "comms.call.start"}
    input.conversation.linked_to_patient
    input.organisation.organisation_type in {
        "software_vendor",
        "api_integration_partner",
        "marketplace_organisation",
        "supplier",
        "manufacturer",
    }
}

# ── Allows ──────────────────────────────────────────────────────────────────

# Citizens may use core comms within their own person context.
allow if {
    input.action in {"comms.conversation.create", "comms.message.send", "comms.call.start", "comms.presence.update"}
    input.identity.identity_type == "citizen"
}

# Providers with an active organisation assignment may use core comms.
allow if {
    input.action in {"comms.conversation.create", "comms.message.send", "comms.call.start", "comms.presence.update"}
    input.identity.identity_type != "citizen"
    input.organisation.active_organisation_assignment
}

# Clinical providers may bind a conversation to a patient record.
allow if {
    input.action == "comms.conversation.link_patient"
    is_clinical_provider
    input.organisation.active_organisation_assignment
}

# ── Helpers ─────────────────────────────────────────────────────────────────

is_clinical_provider if {
    input.identity.identity_type != "citizen"
    input.role in {"CLINICIAN", "NURSE", "DOCTOR", "PROVIDER_CLINICAL", "FACILITY_ADMIN"}
}
