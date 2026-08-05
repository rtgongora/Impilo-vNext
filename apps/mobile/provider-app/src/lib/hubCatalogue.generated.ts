/**
 * GENERATED FILE — DO NOT EDIT.
 *
 * The provider app's offline hub layout, projected from the one catalogue that serves these hubs
 * (ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts) with each section's role
 * requirement resolved from the route registry (ui/one-ui-shell/src/lib/routes.ts).
 *
 * Online, the experience BFF withholds sections the caller's roles cannot open. Offline there is
 * no BFF to ask, so the app filters this list itself — see src/lib/hubReachability.ts. Without
 * the requiredRole carried here it could not, and the offline layout would go on offering every
 * section to every role, each one a tap that leaves the app and lands on /home.
 *
 * Regenerate with `npm run generate:route-guards` in ui/one-ui-shell.
 */

export type GeneratedHubSection = {
  id: string;
  title: string;
  web_path: string;
  hint?: string;
  /** Role group or literal role the route demands; absent when it refuses no role. */
  requiredRole?: string;
};

/** Role group -> the concrete realm roles that satisfy it, already expanded. */
export const HUB_ROLE_GROUPS: Record<string, string[]> = {
  "ADMIN": [
    "SYSTEM_ADMIN",
    "FACILITY_ADMIN",
    "DEVELOPER",
    "SUPER_ADMIN"
  ],
  "REGISTRY_ADMIN": [
    "SYSTEM_ADMIN",
    "HIE_ADMIN",
    "SUPER_ADMIN"
  ],
  "ORGANIZATION_ADMIN": [
    "SYSTEM_ADMIN",
    "FACILITY_ADMIN",
    "DEVELOPER",
    "FINANCE",
    "SUPER_ADMIN"
  ],
  "PUBLIC_HEALTH": [
    "PUBLIC_HEALTH_OFFICER",
    "ENV_HEALTH",
    "CHW",
    "FACILITY_ADMIN",
    "SYSTEM_ADMIN",
    "DEVELOPER",
    "SUPER_ADMIN"
  ],
  "ADMIN_OR_HIE": [
    "SYSTEM_ADMIN",
    "HIE_ADMIN",
    "FACILITY_ADMIN",
    "DEVELOPER",
    "SUPER_ADMIN"
  ],
  "CLINICAL": [
    "CLINICIAN",
    "NURSE",
    "FACILITY_ADMIN",
    "SYSTEM_ADMIN",
    "DEVELOPER",
    "SUPER_ADMIN"
  ]
};

export type HubKey =
  | "admin-registry"
  | "ops-reports"
  | "developer"
  | "professional-settings"
  | "professional-channels";

export const HUB_FALLBACK_SECTIONS: Record<HubKey, GeneratedHubSection[]> = {
  "admin-registry": [
    {
      "id": "admin",
      "title": "Administration",
      "web_path": "/admin",
      "hint": "Users, roles, policies, and platform configuration.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "registry",
      "title": "Registry Hub",
      "web_path": "/registry",
      "hint": "Patient and provider registry entry points."
    },
    {
      "id": "registry_admin",
      "title": "Registry Administration",
      "web_path": "/registry-admin",
      "hint": "Registry configuration and governance.",
      "requiredRole": "REGISTRY_ADMIN"
    },
    {
      "id": "organization_admin",
      "title": "Organization Administration",
      "web_path": "/organization-admin",
      "hint": "Sites, cadres, and org structure.",
      "requiredRole": "ORGANIZATION_ADMIN"
    },
    {
      "id": "public_health",
      "title": "Public Health",
      "web_path": "/public-health",
      "hint": "Programmes, surveillance, and campaigns.",
      "requiredRole": "PUBLIC_HEALTH"
    },
    {
      "id": "id_services",
      "title": "Identity Services",
      "web_path": "/id-services",
      "hint": "Identity proofing and credential services.",
      "requiredRole": "ADMIN_OR_HIE"
    },
    {
      "id": "access",
      "title": "Access Channels",
      "web_path": "/access",
      "hint": "Kiosk, landline, and alternate access paths.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "ai_governance",
      "title": "AI Governance",
      "web_path": "/ai-governance",
      "hint": "Model registry, safety, and audit controls.",
      "requiredRole": "ADMIN"
    }
  ],
  "ops-reports": [
    {
      "id": "operations",
      "title": "Operations",
      "web_path": "/operations",
      "hint": "Day-to-day facility and platform operations.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "operations_vito",
      "title": "Identity Operations",
      "web_path": "/operations/vito",
      "hint": "VITO / identity exchange operations.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "operations_butano",
      "title": "SHR Operations",
      "web_path": "/operations/butano",
      "hint": "Shared health record connectivity and operations.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "operations_assets",
      "title": "Asset Management",
      "web_path": "/operations/assets",
      "hint": "Track and maintain physical assets.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "operations_equipment",
      "title": "Equipment Management",
      "web_path": "/operations/equipment",
      "hint": "Devices, maintenance, and calibration.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "reports",
      "title": "Reports",
      "web_path": "/reports",
      "hint": "Reporting home and saved views."
    },
    {
      "id": "reports_facility",
      "title": "Facility Reports",
      "web_path": "/reports/facility",
      "hint": "Utilization, throughput, and site KPIs."
    },
    {
      "id": "reports_clinical",
      "title": "Clinical Reports",
      "web_path": "/reports/clinical",
      "hint": "Clinical quality and outcomes summaries."
    },
    {
      "id": "reports_operational",
      "title": "Operational Reports",
      "web_path": "/reports/operational",
      "hint": "Ops dashboards and SLAs."
    },
    {
      "id": "reports_custom",
      "title": "Custom Reports",
      "web_path": "/reports/custom",
      "hint": "User-defined report definitions."
    },
    {
      "id": "reports_detail",
      "title": "Report Details",
      "web_path": "/reports/[id]",
      "hint": "Drill into a single report run or export."
    }
  ],
  "developer": [
    {
      "id": "developer",
      "title": "Developer Portal",
      "web_path": "/developer",
      "hint": "Integrator landing and documentation.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "developer_api_catalog",
      "title": "API Catalog",
      "web_path": "/developer/api-catalog",
      "hint": "Browse versioned APIs and schemas.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "developer_clients",
      "title": "Client Registration",
      "web_path": "/developer/clients",
      "hint": "Register OAuth clients and callbacks.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "developer_sandbox",
      "title": "Sandbox",
      "web_path": "/developer/sandbox",
      "hint": "Test tenants and sample data access.",
      "requiredRole": "ADMIN"
    }
  ],
  "professional-settings": [
    {
      "id": "settings",
      "title": "Settings",
      "web_path": "/settings",
      "hint": "Professional preferences overview."
    },
    {
      "id": "settings_account",
      "title": "Account Settings",
      "web_path": "/settings/account",
      "hint": "Profile, language, and sign-in identity."
    },
    {
      "id": "settings_security",
      "title": "Security Settings",
      "web_path": "/settings/security",
      "hint": "MFA, sessions, and device posture."
    },
    {
      "id": "settings_notifications",
      "title": "Notification Preferences",
      "web_path": "/settings/notifications",
      "hint": "Channels and alert rules."
    },
    {
      "id": "settings_display",
      "title": "Display Settings",
      "web_path": "/settings/display",
      "hint": "Density, contrast, and accessibility."
    },
    {
      "id": "settings_integrations",
      "title": "Integrations",
      "web_path": "/settings/integrations",
      "hint": "Connected apps and API tokens."
    },
    {
      "id": "settings_privacy",
      "title": "Privacy & Data",
      "web_path": "/settings/privacy",
      "hint": "Retention, export, and consent mirrors."
    }
  ],
  "professional-channels": [
    {
      "id": "omnichannel",
      "title": "Omnichannel Hub",
      "web_path": "/omnichannel",
      "hint": "Queues, messaging, and channel routing.",
      "requiredRole": "ADMIN"
    },
    {
      "id": "coverage",
      "title": "Coverage Operations",
      "web_path": "/ruvimbo/provider",
      "hint": "Schemes, eligibility, and verification.",
      "requiredRole": "CLINICAL"
    },
    {
      "id": "home_credentials",
      "title": "Credentials & CPD",
      "web_path": "/home/credentials",
      "hint": "Professional licenses and learning credits."
    },
    {
      "id": "ph_surveillance",
      "title": "Surveillance",
      "web_path": "/public-health/surveillance",
      "hint": "Signals, case lines, and indicators.",
      "requiredRole": "PUBLIC_HEALTH"
    },
    {
      "id": "ph_campaigns",
      "title": "Campaigns",
      "web_path": "/public-health/campaigns",
      "hint": "Immunisation and outreach waves.",
      "requiredRole": "PUBLIC_HEALTH"
    },
    {
      "id": "ph_site_registry",
      "title": "Site Registry",
      "web_path": "/public-health/site-registry",
      "hint": "Community sites and outreach anchors.",
      "requiredRole": "PUBLIC_HEALTH"
    },
    {
      "id": "ph_site_profile",
      "title": "Site Profile",
      "web_path": "/public-health/site-registry/[siteId]",
      "hint": "Single-site programme detail.",
      "requiredRole": "PUBLIC_HEALTH"
    },
    {
      "id": "ph_field_tasks",
      "title": "Field Tasks (native)",
      "web_path": "/tools/ph-field",
      "hint": "Mobile task board with lifecycle transitions."
    }
  ]
};
