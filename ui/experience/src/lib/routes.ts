/**
 * Experience UI — Complete Route Registry
 *
 * 226 routes across 26 zones.
 * Each route specifies: path, zone, layout, sidebar context, guard, page title, and nav label.
 *
 * Zones: auth, home, facility, workspace, shift, queue, ehr, admin, registry,
 * marketplace, finance, pharmacy, inventory, reports, settings,
 * wellness, caregiving, monitoring, discovery, lab, operations,
 * support, developer
 */

export type LayoutVariant = "app" | "ehr" | "auth" | "minimal";
export type SidebarContext =
  | "main"
  | "facility"
  | "workspace"
  | "shift"
  | "queue"
  | "ehr"
  | "admin"
  | "registry"
  | "marketplace"
  | "finance"
  | "settings";

export type GuardType = "none" | "auth" | "facility" | "workspace" | "shift" | "role" | "provider";

export interface RouteDefinition {
  path: string;
  zone: string;
  layout: LayoutVariant;
  sidebar: SidebarContext;
  guard: GuardType;
  requiredRole?: string;
  pageTitle: string;
  navLabel: string;
  navZone?: "work" | "professional" | "life";
}

export const ROUTES: RouteDefinition[] = [
  // ── Zone: Auth (4 pathways) ─────────────────────────────────────
  { path: "/auth/login", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Sign In", navLabel: "Sign In" },
  { path: "/auth/login/email", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Sign In with Email", navLabel: "Email Login" },
  { path: "/auth/login/provider-id", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Sign In with Provider ID", navLabel: "Provider ID Login" },
  { path: "/auth/login/biometric", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Biometric Verification", navLabel: "Biometric" },
  { path: "/auth/forgot-password", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Forgot Password", navLabel: "Forgot Password" },
  { path: "/auth/reset-password", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Reset Password", navLabel: "Reset Password" },
  { path: "/auth/mfa", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Multi-Factor Authentication", navLabel: "MFA" },
  { path: "/auth/logout", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Signing Out", navLabel: "Sign Out" },

  // ── Zone: Home ──────────────────────────────────────────────────
  // ── Zone: Clinical Hub ──────────────────────────────────────────
  { path: "/clinical", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Clinical Care", navLabel: "Clinical Hub", navZone: "work" },

  // ── Zone: Clinical Tools ────────────────────────────────────────
  { path: "/clinical-tools", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Clinical Tools", navLabel: "Tools", navZone: "work" },
  { path: "/clinical-tools/rules", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Rules Engine", navLabel: "Rules Engine", navZone: "work" },
  { path: "/clinical-tools/forms", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Form Builder", navLabel: "Form Builder", navZone: "work" },
  { path: "/clinical/control-tower", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Control Tower", navLabel: "Control Tower", navZone: "work" },
  { path: "/clinical/dictation", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Voice Dictation", navLabel: "Dictation", navZone: "work" },
  { path: "/clinical/emergency", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "ED / Casualty", navLabel: "ED / Casualty", navZone: "work" },

  // ── Zone: Public Health ─────────────────────────────────────────
  { path: "/public-health", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PUBLIC_HEALTH", pageTitle: "Public Health", navLabel: "Public Health", navZone: "professional" },

  // ── Zone: Omnichannel ───────────────────────────────────────────
  { path: "/omnichannel", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Omnichannel Hub", navLabel: "Omnichannel", navZone: "professional" },

  // ── Zone: Coverage ──────────────────────────────────────────────
  { path: "/coverage", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Coverage Operations", navLabel: "Coverage", navZone: "professional" },

  // ── Zone: Identity Services ──────────────────────────────────────
  { path: "/id-services", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Identity Services", navLabel: "ID Services", navZone: "professional" },
  { path: "/ai-governance", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "AI Governance", navLabel: "AI Governance", navZone: "professional" },
  { path: "/ai-governance/models/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "AI Model", navLabel: "AI Model", navZone: "professional" },
  { path: "/access", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Access Channels", navLabel: "Access", navZone: "professional" },

  // ── Zone: Kiosk (public) ─────────────────────────────────────────
  { path: "/kiosk", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Self Check-In", navLabel: "Kiosk" },

  // ── Zone: Home ──────────────────────────────────────────────────
  { path: "/", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Home", navLabel: "Home", navZone: "life" },
  { path: "/home", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Home", navLabel: "Home", navZone: "life" },
  { path: "/home/notifications", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Notifications", navLabel: "Notifications", navZone: "life" },
  { path: "/home/profile", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Profile", navLabel: "Profile", navZone: "life" },
  { path: "/home/preferences", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Preferences", navLabel: "Preferences", navZone: "life" },
  { path: "/home/credentials", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Credentials & CPD", navLabel: "Credentials", navZone: "professional" },
  { path: "/home/medications", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Medications", navLabel: "Medications", navZone: "life" },
  { path: "/citizen", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Citizen Services", navLabel: "Citizen Services", navZone: "life" },
  { path: "/citizen/health-id/qr", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Health ID QR", navLabel: "Health ID QR", navZone: "life" },
  { path: "/citizen/health-id/request", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Request Health ID", navLabel: "Request Health ID", navZone: "life" },
  { path: "/citizen/id-recovery", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "ID Recovery", navLabel: "ID Recovery", navZone: "life" },
  { path: "/citizen/delegated-pickup", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Delegated Pickup", navLabel: "Delegated Pickup", navZone: "life" },
  { path: "/verify/credential", zone: "home", layout: "app", sidebar: "main", guard: "none", pageTitle: "Verify Credential", navLabel: "Verify Credential", navZone: "life" },
  { path: "/share/claim", zone: "home", layout: "app", sidebar: "main", guard: "none", pageTitle: "Claim Shared Documents", navLabel: "Claim Shared Documents", navZone: "life" },

  // ── Zone: Facility Selection ────────────────────────────────────
  { path: "/facility", zone: "facility", layout: "app", sidebar: "facility", guard: "auth", pageTitle: "Select Facility", navLabel: "Facilities" },
  { path: "/facility/[id]", zone: "facility", layout: "app", sidebar: "facility", guard: "auth", pageTitle: "Facility Details", navLabel: "Facility" },

  // ── Zone: Workspace Selection ───────────────────────────────────
  { path: "/workspace", zone: "workspace", layout: "app", sidebar: "workspace", guard: "facility", pageTitle: "Select Workspace", navLabel: "Workspaces" },
  { path: "/workspace/[id]", zone: "workspace", layout: "app", sidebar: "workspace", guard: "facility", pageTitle: "Workspace Details", navLabel: "Workspace" },

  // ── Zone: Shift ─────────────────────────────────────────────────
  { path: "/shift", zone: "shift", layout: "app", sidebar: "shift", guard: "workspace", pageTitle: "Start Shift", navLabel: "Shift" },
  { path: "/shift/active", zone: "shift", layout: "app", sidebar: "shift", guard: "shift", pageTitle: "Active Shift", navLabel: "Active Shift" },
  { path: "/shift/handover", zone: "shift", layout: "app", sidebar: "shift", guard: "shift", pageTitle: "Shift Handover", navLabel: "Handover" },

  // ── Zone: Scheduling (workspace guard; ORGANIZATION_ADMIN may pass with facility only — AuthGuardProvider)
  { path: "/scheduling", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Scheduling", navLabel: "Scheduling", navZone: "work" },
  { path: "/scheduling/roster", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Staff Roster", navLabel: "Roster", navZone: "work" },
  { path: "/scheduling/on-call", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "On-Call Schedule", navLabel: "On-Call", navZone: "work" },
  { path: "/scheduling/noticeboard", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Provider Noticeboard", navLabel: "Noticeboard", navZone: "work" },

  // ── Zone: Communication ────────────────────────────────────────
  { path: "/communication/secure-messaging", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Secure Messaging", navLabel: "Messaging", navZone: "work" },

  // ── Zone: Queue (Clinical) ──────────────────────────────────────
  { path: "/queue", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Patient Queue", navLabel: "Queue", navZone: "work" },
  { path: "/queue/triage", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Triage Queue", navLabel: "Triage", navZone: "work" },
  { path: "/queue/waiting", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Waiting Room", navLabel: "Waiting", navZone: "work" },
  { path: "/queue/search", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Patient Search", navLabel: "Search", navZone: "work" },
  { path: "/queue/walk-in", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Walk-in Registration", navLabel: "Walk-in", navZone: "work" },
  { path: "/queue/scheduled", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Scheduled Visits", navLabel: "Scheduled", navZone: "work" },
  { path: "/queue/incoming-referrals", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Incoming Referrals", navLabel: "Incoming Referrals", navZone: "work" },

  // ── Zone: EHR (Clinical) ────────────────────────────────────────
  { path: "/ehr/[patientId]", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Patient Chart", navLabel: "Chart", navZone: "work" },
  { path: "/ehr/[patientId]/summary", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Patient Summary", navLabel: "Summary", navZone: "work" },
  { path: "/ehr/[patientId]/ips", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "International Patient Summary", navLabel: "IPS", navZone: "work" },
  { path: "/ehr/[patientId]/vitals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Vitals", navLabel: "Vitals", navZone: "work" },
  { path: "/ehr/[patientId]/maternity", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Maternity Monitoring", navLabel: "Maternity", navZone: "work" },
  { path: "/ehr/[patientId]/history", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Medical History", navLabel: "History", navZone: "work" },
  { path: "/ehr/[patientId]/conditions", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Conditions", navLabel: "Conditions", navZone: "work" },
  { path: "/ehr/[patientId]/medications", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Medications", navLabel: "Medications", navZone: "work" },
  { path: "/ehr/[patientId]/allergies", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Allergies", navLabel: "Allergies", navZone: "work" },
  { path: "/ehr/[patientId]/orders", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Orders", navLabel: "Orders", navZone: "work" },
  { path: "/ehr/[patientId]/results", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Results", navLabel: "Results", navZone: "work" },
  { path: "/ehr/[patientId]/notes", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Clinical Notes", navLabel: "Notes", navZone: "work" },
  { path: "/ehr/[patientId]/documents", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Documents", navLabel: "Documents", navZone: "work" },
  { path: "/ehr/[patientId]/encounters", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Encounters", navLabel: "Encounters", navZone: "work" },
  { path: "/ehr/[patientId]/encounter/[encounterId]", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Encounter", navLabel: "Encounter", navZone: "work" },
  { path: "/ehr/[patientId]/immunizations", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Immunizations", navLabel: "Immunizations", navZone: "work" },
  { path: "/ehr/[patientId]/consults", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Consults & Referrals", navLabel: "Consults", navZone: "work" },
  { path: "/ehr/[patientId]/referrals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Referrals", navLabel: "Referrals", navZone: "work" },
  { path: "/ehr/[patientId]/teleconsults", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Teleconsults", navLabel: "Teleconsults", navZone: "work" },
  { path: "/ehr/[patientId]/timeline", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Timeline", navLabel: "Timeline", navZone: "work" },
  { path: "/ehr/[patientId]/discharge", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Discharge", navLabel: "Discharge", navZone: "work" },
  { path: "/ehr/[patientId]/care-plans", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Care Plans", navLabel: "Care Plans", navZone: "work" },
  { path: "/ehr/[patientId]/procedures", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Procedures", navLabel: "Procedures", navZone: "work" },
  { path: "/ehr/[patientId]/growth-chart", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Growth Chart", navLabel: "Growth Chart", navZone: "work" },
  { path: "/ehr/[patientId]/family-history", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Family History", navLabel: "Family History", navZone: "work" },
  { path: "/ehr/[patientId]/social-history", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Social History", navLabel: "Social History", navZone: "work" },
  { path: "/ehr/[patientId]/functional-status", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Functional Status", navLabel: "Functional Status", navZone: "work" },
  { path: "/ehr/[patientId]/advance-directives", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Advance Directives", navLabel: "Advance Directives", navZone: "work" },
  { path: "/ehr/[patientId]/care-team", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Care Team", navLabel: "Care Team", navZone: "work" },
  { path: "/ehr/[patientId]/goals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Goals", navLabel: "Goals", navZone: "work" },
  { path: "/ehr/[patientId]/assessments", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Assessments", navLabel: "Assessments", navZone: "work" },
  { path: "/ehr/[patientId]/charts", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Ward Charts", navLabel: "Charts", navZone: "work" },

  // ── Zone: Admin / TSHEPO Governance ─────────────────────────────
  { path: "/admin", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Administration", navLabel: "Admin", navZone: "professional" },
  { path: "/admin/users", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "User Management", navLabel: "Users", navZone: "professional" },
  { path: "/admin/users/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "User Details", navLabel: "User", navZone: "professional" },
  { path: "/admin/roles", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Role Management", navLabel: "Roles", navZone: "professional" },
  { path: "/admin/policies", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Policy Management", navLabel: "Policies", navZone: "professional" },
  { path: "/admin/audit", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Audit Trail", navLabel: "Audit", navZone: "professional" },
  { path: "/admin/audit/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Audit Entry", navLabel: "Audit Detail", navZone: "professional" },
  { path: "/admin/consent", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Consent Management", navLabel: "Consent", navZone: "professional" },
  { path: "/admin/devices", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Device Management", navLabel: "Devices", navZone: "professional" },
  { path: "/admin/keys", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Key Management", navLabel: "Keys", navZone: "professional" },
  { path: "/admin/federation", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Federation", navLabel: "Federation", navZone: "professional" },
  { path: "/admin/tenants", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Tenant Management", navLabel: "Tenants", navZone: "professional" },
  { path: "/admin/break-glass", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Break Glass Log", navLabel: "Break Glass", navZone: "professional" },
  { path: "/admin/beds", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Bed & Ward Admin", navLabel: "Beds", navZone: "professional" },
  { path: "/admin/queues", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Queue Configuration", navLabel: "Queues", navZone: "professional" },
  { path: "/admin/data-export", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Data Export", navLabel: "Data Export", navZone: "professional" },
  { path: "/admin/clinical-curation", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Clinical Knowledge Curation", navLabel: "Clinical Curation", navZone: "professional" },
  { path: "/admin/system-monitor", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "System Monitor", navLabel: "System Monitor", navZone: "professional" },
  { path: "/admin/integration-status", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Integration Status", navLabel: "Integrations", navZone: "professional" },
  { path: "/admin/sidecar-retirement", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Sidecar Retirement", navLabel: "Sidecar Retirement", navZone: "professional" },

  // ── Administrative plane landings (operational context: registry_admin / organization_admin) ──
  { path: "/registry-admin", zone: "admin", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Registry Administration", navLabel: "Registry Admin", navZone: "professional" },
  { path: "/organization-admin", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Organization Administration", navLabel: "Org Admin", navZone: "professional" },
  { path: "/organization-admin/facility", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Facility Administration", navLabel: "Org Facility", navZone: "professional" },
  { path: "/organization-admin/staffing", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Staffing & Scheduling", navLabel: "Org Staffing", navZone: "professional" },

  // ── Zone: Registry ──────────────────────────────────────────────
  { path: "/registry/clients", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Client Registry", navLabel: "Client Registry", navZone: "professional" },
  { path: "/registry/trust", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Trust & Federation", navLabel: "Trust", navZone: "professional" },
  { path: "/registry", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Registry Hub", navLabel: "Registry", navZone: "professional" },
  { path: "/registry/providers", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Provider Registry", navLabel: "Providers", navZone: "professional" },
  { path: "/registry/providers/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Provider Profile", navLabel: "Provider", navZone: "professional" },
  { path: "/registry/facilities", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Facility Registry", navLabel: "Facilities", navZone: "professional" },
  { path: "/registry/facilities/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Facility Profile", navLabel: "Facility", navZone: "professional" },
  { path: "/registry/terminology", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Terminology Browser", navLabel: "Terminology", navZone: "professional" },
  { path: "/registry/terminology/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Concept Details", navLabel: "Concept", navZone: "professional" },
  { path: "/registry/products", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Product Registry", navLabel: "Products", navZone: "professional" },
  { path: "/registry/products/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Product Details", navLabel: "Product", navZone: "professional" },

  // ── Zone: Marketplace ───────────────────────────────────────────
  { path: "/marketplace", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Health Marketplace", navLabel: "Marketplace", navZone: "work" },
  { path: "/marketplace/catalog", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Service Catalog", navLabel: "Catalog", navZone: "work" },
  { path: "/marketplace/orders", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "My Orders", navLabel: "Orders", navZone: "work" },
  { path: "/marketplace/orders/[id]", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Order Details", navLabel: "Order", navZone: "work" },
  { path: "/marketplace/ops", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Marketplace Operations", navLabel: "Marketplace Ops", navZone: "work" },
  { path: "/marketplace/vendor", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Vendor Fulfilment", navLabel: "Vendor Fulfilment", navZone: "work" },
  { path: "/marketplace/vendor/orders", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Vendor Orders", navLabel: "Vendor Orders", navZone: "work" },
  { path: "/marketplace/pickup", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Pickup Handoff", navLabel: "Pickup", navZone: "work" },
  { path: "/marketplace/vendors", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Vendors", navLabel: "Vendors", navZone: "work" },
  { path: "/marketplace/bookings", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Bookings", navLabel: "Bookings", navZone: "work" },

  // ── Zone: Finance ───────────────────────────────────────────────
  { path: "/finance", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Finance Dashboard", navLabel: "Finance", navZone: "work" },
  { path: "/finance/claims", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Claims", navLabel: "Claims", navZone: "work" },
  { path: "/finance/claims/[id]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Claim Details", navLabel: "Claim", navZone: "work" },
  { path: "/finance/billing", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Billing", navLabel: "Billing", navZone: "work" },
  { path: "/finance/billing/[id]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Bill Details", navLabel: "Bill", navZone: "work" },
  { path: "/finance/payments", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Payments", navLabel: "Payments", navZone: "work" },
  { path: "/finance/msika-governance", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "MSIKA_GOVERNANCE", pageTitle: "MSIKA Governance", navLabel: "MSIKA Governance", navZone: "work" },
  { path: "/finance/ledger", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Ledger", navLabel: "Ledger", navZone: "work" },
  { path: "/finance/settlements", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Settlements", navLabel: "Settlements", navZone: "work" },
  { path: "/finance/reconciliation", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Reconciliation", navLabel: "Reconciliation", navZone: "work" },
  { path: "/finance/refunds", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Refunds", navLabel: "Refunds", navZone: "work" },
  { path: "/finance/payer-ops", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Payer Operations", navLabel: "Payer Ops", navZone: "work" },
  { path: "/finance/payer-claims", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Payer Claims Queue", navLabel: "Payer Claims", navZone: "work" },
  { path: "/finance/payer-claims/[claimId]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Payer Claim", navLabel: "Payer Claim", navZone: "work" },
  { path: "/finance/tariffs", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Tariff Management", navLabel: "Tariffs", navZone: "work" },
  { path: "/finance/commerce-integrations", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Commerce & Payer Stack", navLabel: "Commerce Integrations", navZone: "work" },
  { path: "/finance/my-account", zone: "finance", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Healthcare Account", navLabel: "My healthcare costs", navZone: "life" },

  // ── Zone: Beds & Wards ──────────────────────────────────────────
  { path: "/beds", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Bed Management", navLabel: "Beds", navZone: "work" },

  // ── Zone: Pharmacy ──────────────────────────────────────────────
  { path: "/pharmacy", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Pharmacy Dashboard", navLabel: "Pharmacy", navZone: "work" },
  { path: "/pharmacy/dispense", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Dispensing", navLabel: "Dispense", navZone: "work" },
  { path: "/pharmacy/stock", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Stock Management", navLabel: "Stock", navZone: "work" },
  { path: "/pharmacy/prescriptions", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Prescriptions", navLabel: "Prescriptions", navZone: "work" },

  // ── Zone: Inventory ─────────────────────────────────────────────
  { path: "/inventory", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Inventory Dashboard", navLabel: "Inventory", navZone: "work" },
  { path: "/inventory/movements", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Stock Movements", navLabel: "Movements", navZone: "work" },
  { path: "/inventory/counts", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Stock Counts", navLabel: "Counts", navZone: "work" },
  { path: "/inventory/requisitions", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Requisitions", navLabel: "Requisitions", navZone: "work" },
  { path: "/inventory/stock-management", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Stock Management", navLabel: "Stock Management", navZone: "work" },

  // ── Zone: Reports ───────────────────────────────────────────────
  { path: "/reports", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Reports", navLabel: "Reports", navZone: "professional" },
  { path: "/reports/facility", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Facility Reports", navLabel: "Facility Reports", navZone: "professional" },
  { path: "/reports/clinical", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Clinical Reports", navLabel: "Clinical Reports", navZone: "professional" },
  { path: "/reports/operational", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Operational Reports", navLabel: "Operational Reports", navZone: "professional" },
  { path: "/reports/custom", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Custom Reports", navLabel: "Custom Reports", navZone: "professional" },
  { path: "/reports/[id]", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Report Details", navLabel: "Report", navZone: "professional" },

  // ── Zone: Settings ──────────────────────────────────────────────
  { path: "/settings", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Settings", navLabel: "Settings", navZone: "professional" },
  { path: "/settings/account", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Account Settings", navLabel: "Account", navZone: "professional" },
  { path: "/settings/security", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Security Settings", navLabel: "Security", navZone: "professional" },
  { path: "/settings/notifications", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Notification Preferences", navLabel: "Notifications", navZone: "professional" },
  { path: "/settings/display", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Display Settings", navLabel: "Display", navZone: "professional" },
  { path: "/settings/integrations", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Integrations", navLabel: "Integrations", navZone: "professional" },
  { path: "/telemedicine", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Telemedicine Hub", navLabel: "Telemedicine", navZone: "work" },
  { path: "/telemedicine/session/[sessionId]", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Telemedicine Session", navLabel: "Session", navZone: "work" },

  // ── Zone: Provider Activation (Health OS §6) ───────────────────────
  { path: "/provider/activate", zone: "auth", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Activate Provider Role", navLabel: "Provider Activation" },

  // ── Zone: Wellness (Health OS §2 — prevention, self-care, fitness) ─
  { path: "/wellness", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Hub", navLabel: "Wellness", navZone: "life" },
  { path: "/wellness/goals", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Health Goals", navLabel: "Goals", navZone: "life" },
  { path: "/wellness/programs", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Prevention Programs", navLabel: "Programs", navZone: "life" },
  { path: "/wellness/screenings", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Screening Schedule", navLabel: "Screenings", navZone: "life" },
  { path: "/wellness/activity", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Activity & Fitness", navLabel: "Activity", navZone: "life" },
  { path: "/wellness/connect", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Health Connect ingest", navLabel: "HC ingest", navZone: "life" },
  { path: "/wellness/diet", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Diet & Nutrition", navLabel: "Diet", navZone: "life" },
  { path: "/wellness/sleep", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Sleep & Recovery", navLabel: "Sleep", navZone: "life" },
  { path: "/wellness/clubs", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Clubs & Communities", navLabel: "Clubs", navZone: "life" },
  { path: "/wellness/challenges", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Challenges", navLabel: "Challenges", navZone: "life" },
  { path: "/wellness/routes", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Routes & Places", navLabel: "Routes", navZone: "life" },
  { path: "/wellness/coaching", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Coaching & Habits", navLabel: "Coaching", navZone: "life" },
  { path: "/wellness/community", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Community", navLabel: "Community", navZone: "life" },

  // ── Zone: Caregiving (Health OS §4 — delegated care, family) ───────
  { path: "/caregiving", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Caregiving Hub", navLabel: "Caregiving", navZone: "life" },
  { path: "/caregiving/dependants", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Dependants", navLabel: "Dependants", navZone: "life" },
  { path: "/caregiving/delegation", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Care Delegation", navLabel: "Delegation", navZone: "life" },
  { path: "/caregiving/tasks", zone: "caregiving", layout: "app", sidebar: "main", guard: "role", requiredRole: "CAREGIVER", pageTitle: "Care Tasks", navLabel: "Tasks", navZone: "life" },
  { path: "/caregiving/notifications", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Care Alerts", navLabel: "Alerts", navZone: "life" },

  // ── Zone: Remote Monitoring (Health OS §2 — devices, chronic care) ─
  { path: "/monitoring", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Remote Monitoring", navLabel: "Monitoring", navZone: "life" },
  { path: "/monitoring/devices", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Devices", navLabel: "Devices", navZone: "life" },
  { path: "/monitoring/readings", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Readings & Trends", navLabel: "Readings", navZone: "life" },
  { path: "/monitoring/alerts", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Monitoring Alerts", navLabel: "Alerts", navZone: "life" },
  { path: "/monitoring/care-plans", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Chronic Care Plans", navLabel: "Care Plans", navZone: "life" },
  { path: "/monitoring/provider-dashboard", zone: "monitoring", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Patient Monitoring Dashboard", navLabel: "Monitoring Dashboard", navZone: "work" },

  // ── Zone: Service Discovery (Health OS §2 — find providers, facilities, services) ─
  { path: "/discover", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Find Services", navLabel: "Discover", navZone: "life" },
  { path: "/discover/providers", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Find a Provider", navLabel: "Providers", navZone: "life" },
  { path: "/discover/facilities", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Find a Facility", navLabel: "Facilities", navZone: "life" },
  { path: "/discover/services", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Browse Services", navLabel: "Services", navZone: "life" },

  // ── Zone: Laboratory (absorbs oros-web sidecar) ────────────────────
  { path: "/lab", zone: "lab", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Laboratory", navLabel: "Lab", navZone: "work" },
  { path: "/lab/worklist", zone: "lab", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Lab Worklist", navLabel: "Worklist", navZone: "work" },
  { path: "/lab/results", zone: "lab", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Results Review", navLabel: "Results", navZone: "work" },
  { path: "/lab/catalog", zone: "lab", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Test Catalog", navLabel: "Catalog", navZone: "work" },
  { path: "/lab/reconciliation", zone: "lab", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Lab Reconciliation", navLabel: "Reconciliation", navZone: "work" },

  // ── Zone: Operations (absorbs ops-console sidecar) ─────────────────
  { path: "/operations", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Operations", navLabel: "Operations", navZone: "professional" },
  { path: "/operations/vito", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Identity Operations", navLabel: "Identity Ops", navZone: "professional" },
  { path: "/operations/butano", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "SHR Operations", navLabel: "SHR Ops", navZone: "professional" },
  { path: "/operations/assets", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Asset Management", navLabel: "Assets", navZone: "professional" },
  { path: "/operations/equipment", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Equipment Management", navLabel: "Equipment", navZone: "professional" },

  // ── Zone: Support (absorbs support-console sidecar) ────────────────
  { path: "/support", zone: "support", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Support", navLabel: "Support", navZone: "life" },
  { path: "/support/tickets", zone: "support", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Support Tickets", navLabel: "Tickets", navZone: "life" },
  { path: "/support/knowledge-base", zone: "support", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Knowledge Base", navLabel: "Help", navZone: "life" },

  // ── Zone: Developer Portal (absorbs developer-console sidecar) ─────
  { path: "/developer", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Developer Portal", navLabel: "Developer", navZone: "professional" },
  { path: "/developer/api-catalog", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "API Catalog", navLabel: "API Catalog", navZone: "professional" },
  { path: "/developer/clients", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Client Registration", navLabel: "Clients", navZone: "professional" },
  { path: "/developer/sandbox", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Sandbox", navLabel: "Sandbox", navZone: "professional" },

  // ── Home: Documents (absorbs self-service/my-documents sidecar) ────
  { path: "/home/documents", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Documents", navLabel: "Documents", navZone: "life" },

  // ── Marketplace: Cart & Substitutions (absorbs msika-flow-portal sidecar) ──
  { path: "/marketplace/cart", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Shopping Cart", navLabel: "Cart", navZone: "work" },
  { path: "/marketplace/substitutions", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Substitutions", navLabel: "Substitutions", navZone: "work" },

  // ── Zone: Intelligent Experience (Health OS §2a, §16a) ─────────────
  { path: "/ask", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Ask", navLabel: "Ask", navZone: "life" },
  { path: "/search", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Search", navLabel: "Search", navZone: "life" },
  { path: "/guidance", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Guidance", navLabel: "Guidance", navZone: "life" },
  { path: "/guidance/reminders", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Reminders & Prompts", navLabel: "Reminders", navZone: "life" },
  { path: "/guidance/education", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Health Education", navLabel: "Education", navZone: "life" },
];

// Total route count assertion
export const EXPECTED_ROUTE_COUNT = 226;
export const ROUTE_COUNT = ROUTES.length;

// Zone summary
export const ZONES = [...new Set(ROUTES.map((r) => r.zone))];
export const ZONE_COUNT = ZONES.length;

export function matchRouteDefinition(pathname: string): RouteDefinition | null {
  for (const route of ROUTES) {
    const pattern = route.path
      .replace(/\[(\w+)\]/g, "[^/]+")
      .replace(/\//g, "\\/");
    const regex = new RegExp(`^${pattern}$`);
    if (regex.test(pathname)) {
      return route;
    }
  }

  return null;
}
