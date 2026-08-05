import { NextResponse } from "next/server";

type HubSection = { id: string; title: string; web_path: string; hint?: string | null };

function sectionsFor(hub: string): HubSection[] | null {
  switch (hub) {
    case "admin-registry":
      return [
        { id: "admin", title: "Administration", web_path: "/admin", hint: "Users, roles, policies, and platform configuration." },
        { id: "registry", title: "Registry Hub", web_path: "/registry", hint: "Patient and provider registry entry points." },
        { id: "registry_admin", title: "Registry Administration", web_path: "/registry-admin", hint: "Registry configuration and governance." },
        { id: "organization_admin", title: "Organization Administration", web_path: "/organization-admin", hint: "Sites, cadres, and org structure." },
        { id: "public_health", title: "Public Health", web_path: "/public-health", hint: "Programmes, surveillance, and campaigns." },
        { id: "id_services", title: "Identity Services", web_path: "/id-services", hint: "Identity proofing and credential services." },
        { id: "access", title: "Access Channels", web_path: "/access", hint: "Kiosk, landline, and alternate access paths." },
        { id: "ai_governance", title: "AI Governance", web_path: "/ai-governance", hint: "Model registry, safety, and audit controls." },
      ];
    case "ops-reports":
      return [
        { id: "operations", title: "Operations", web_path: "/operations", hint: "Day-to-day facility and platform operations." },
        { id: "operations_vito", title: "Identity Operations", web_path: "/operations/vito", hint: "VITO / identity exchange operations." },
        { id: "operations_butano", title: "SHR Operations", web_path: "/operations/butano", hint: "Shared health record connectivity and operations." },
        { id: "operations_assets", title: "Asset Management", web_path: "/operations/assets", hint: "Track and maintain physical assets." },
        { id: "operations_equipment", title: "Equipment Management", web_path: "/operations/equipment", hint: "Devices, maintenance, and calibration." },
        { id: "reports", title: "Reports", web_path: "/reports", hint: "Reporting home and saved views." },
        { id: "reports_facility", title: "Facility Reports", web_path: "/reports/facility", hint: "Utilization, throughput, and site KPIs." },
        { id: "reports_clinical", title: "Clinical Reports", web_path: "/reports/clinical", hint: "Clinical quality and outcomes summaries." },
        { id: "reports_operational", title: "Operational Reports", web_path: "/reports/operational", hint: "Ops dashboards and SLAs." },
        { id: "reports_custom", title: "Custom Reports", web_path: "/reports/custom", hint: "User-defined report definitions." },
        { id: "reports_detail", title: "Report Details", web_path: "/reports/[id]", hint: "Drill into a single report run or export." },
      ];
    case "developer":
      return [
        { id: "developer", title: "Developer Portal", web_path: "/developer", hint: "Integrator landing and documentation." },
        { id: "developer_api_catalog", title: "API Catalog", web_path: "/developer/api-catalog", hint: "Browse versioned APIs and schemas." },
        { id: "developer_clients", title: "Client Registration", web_path: "/developer/clients", hint: "Register OAuth clients and callbacks." },
        { id: "developer_sandbox", title: "Sandbox", web_path: "/developer/sandbox", hint: "Test tenants and sample data access." },
      ];
    case "professional-settings":
      return [
        { id: "settings", title: "Settings", web_path: "/settings", hint: "Professional preferences overview." },
        { id: "settings_account", title: "Account Settings", web_path: "/settings/account", hint: "Profile, language, and sign-in identity." },
        { id: "settings_security", title: "Security Settings", web_path: "/settings/security", hint: "MFA, sessions, and device posture." },
        { id: "settings_notifications", title: "Notification Preferences", web_path: "/settings/notifications", hint: "Channels and alert rules." },
        { id: "settings_display", title: "Display Settings", web_path: "/settings/display", hint: "Density, contrast, and accessibility." },
        { id: "settings_integrations", title: "Integrations", web_path: "/settings/integrations", hint: "Connected apps and API tokens." },
        { id: "settings_privacy", title: "Privacy & Data", web_path: "/settings/privacy", hint: "Retention, export, and consent mirrors." },
      ];
    case "professional-channels":
      return [
        { id: "omnichannel", title: "Omnichannel Hub", web_path: "/omnichannel", hint: "Queues, messaging, and channel routing." },
        // /coverage admits only ADMIN; this hub is handed to providers. Ruvimbo Provider is
        // the face their roles actually open (role group CLINICAL).
        { id: "coverage", title: "Coverage Operations", web_path: "/ruvimbo/provider", hint: "Schemes, eligibility, and verification." },
        { id: "home_credentials", title: "Credentials & CPD", web_path: "/home/credentials", hint: "Professional licenses and learning credits." },
        { id: "ph_surveillance", title: "Surveillance", web_path: "/public-health/surveillance", hint: "Signals, case lines, and indicators." },
        { id: "ph_campaigns", title: "Campaigns", web_path: "/public-health/campaigns", hint: "Immunisation and outreach waves." },
        { id: "ph_site_registry", title: "Site Registry", web_path: "/public-health/site-registry", hint: "Community sites and outreach anchors." },
        { id: "ph_site_profile", title: "Site Profile", web_path: "/public-health/site-registry/[siteId]", hint: "Single-site programme detail." },
        // Present so that this handler — newly reachable, see next.config.mjs — serves exactly what
        // the BFF stub has always served. Without it, fixing the shadowing would have silently
        // dropped a section from the hub.
        //
        // It is carried forward as-is, NOT endorsed: /tools/ph-field is in no web route registry
        // (there are no /tools routes at all), and ProfessionalHubBody opens web_path in a browser,
        // so this resolves to nothing. The real screen is native — PublicHealthFieldTasksScreen,
        // already reachable in-app via the Outreach tabs and Clinical Tools. Fixing that means
        // letting a section target a native destination instead of a web_path, which is a contract
        // change, not a link correction; tracked separately.
        { id: "ph_field_tasks", title: "Field Tasks (native)", web_path: "/tools/ph-field", hint: "Mobile task board with lifecycle transitions." },
      ];
    default:
      return null;
  }
}

export async function GET(_: Request, { params }: { params: Promise<{ hub: string }> }) {
  const { hub } = await params;
  const sections = sectionsFor(hub);
  if (!sections) {
    return NextResponse.json({ error: "unknown hub" }, { status: 404 });
  }
  return NextResponse.json({ hub, sections });
}

