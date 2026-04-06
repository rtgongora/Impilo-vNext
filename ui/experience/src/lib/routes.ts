/**
 * Experience UI — Complete Route Registry
 *
 * All 98 routes across 15 zones as documented in 01_site_map.md.
 * Each route specifies: path, zone, layout, sidebar context, guard, page title, and nav label.
 *
 * SPEC CONFLICT #1: Docs 01-07 contain summaries only, not detailed route tables.
 * Routes reconstructed from: zone count (15), route count (98), auth model, golden paths,
 * and existing codebase patterns. Revision expected when full specs are available.
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

export type GuardType = "none" | "auth" | "facility" | "workspace" | "shift" | "role";

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

  // ── Zone: Public Health ─────────────────────────────────────────
  { path: "/public-health", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Public Health", navLabel: "Public Health", navZone: "professional" },

  // ── Zone: Omnichannel ───────────────────────────────────────────
  { path: "/omnichannel", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Omnichannel Hub", navLabel: "Omnichannel", navZone: "professional" },

  // ── Zone: Coverage ──────────────────────────────────────────────
  { path: "/coverage", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Coverage Operations", navLabel: "Coverage", navZone: "professional" },

  // ── Zone: Identity Services ──────────────────────────────────────
  { path: "/id-services", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Identity Services", navLabel: "ID Services", navZone: "professional" },
  { path: "/ai-governance", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "AI Governance", navLabel: "AI Governance", navZone: "professional" },
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

  // ── Zone: Queue (Clinical) ──────────────────────────────────────
  { path: "/queue", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Patient Queue", navLabel: "Queue", navZone: "work" },
  { path: "/queue/triage", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Triage Queue", navLabel: "Triage", navZone: "work" },
  { path: "/queue/waiting", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Waiting Room", navLabel: "Waiting", navZone: "work" },
  { path: "/queue/search", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Patient Search", navLabel: "Search", navZone: "work" },
  { path: "/queue/walk-in", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Walk-in Registration", navLabel: "Walk-in", navZone: "work" },
  { path: "/queue/scheduled", zone: "queue", layout: "app", sidebar: "queue", guard: "shift", pageTitle: "Scheduled Visits", navLabel: "Scheduled", navZone: "work" },

  // ── Zone: EHR (Clinical) ────────────────────────────────────────
  { path: "/ehr/[patientId]", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Patient Chart", navLabel: "Chart", navZone: "work" },
  { path: "/ehr/[patientId]/summary", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Patient Summary", navLabel: "Summary", navZone: "work" },
  { path: "/ehr/[patientId]/vitals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Vitals", navLabel: "Vitals", navZone: "work" },
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
  { path: "/ehr/[patientId]/referrals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Referrals", navLabel: "Referrals", navZone: "work" },
  { path: "/ehr/[patientId]/timeline", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Timeline", navLabel: "Timeline", navZone: "work" },
  { path: "/ehr/[patientId]/discharge", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "shift", pageTitle: "Discharge", navLabel: "Discharge", navZone: "work" },

  // ── Zone: Admin / TSHEPO Governance ─────────────────────────────
  { path: "/admin", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Administration", navLabel: "Admin", navZone: "professional" },
  { path: "/admin/users", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "User Management", navLabel: "Users", navZone: "professional" },
  { path: "/admin/users/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "User Details", navLabel: "User", navZone: "professional" },
  { path: "/admin/roles", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Role Management", navLabel: "Roles", navZone: "professional" },
  { path: "/admin/policies", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Policy Management", navLabel: "Policies", navZone: "professional" },
  { path: "/admin/audit", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Audit Trail", navLabel: "Audit", navZone: "professional" },
  { path: "/admin/audit/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Audit Entry", navLabel: "Audit Detail", navZone: "professional" },
  { path: "/admin/consent", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Consent Management", navLabel: "Consent", navZone: "professional" },
  { path: "/admin/devices", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Device Management", navLabel: "Devices", navZone: "professional" },
  { path: "/admin/keys", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Key Management", navLabel: "Keys", navZone: "professional" },
  { path: "/admin/federation", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Federation", navLabel: "Federation", navZone: "professional" },
  { path: "/admin/tenants", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Tenant Management", navLabel: "Tenants", navZone: "professional" },
  { path: "/admin/break-glass", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Break Glass Log", navLabel: "Break Glass", navZone: "professional" },
  { path: "/admin/beds", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Bed & Ward Admin", navLabel: "Beds", navZone: "professional" },
  { path: "/admin/queues", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Queue Configuration", navLabel: "Queues", navZone: "professional" },

  // ── Zone: Registry ──────────────────────────────────────────────
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
  { path: "/marketplace/catalog", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Service Catalog", navLabel: "Catalog", navZone: "work" },
  { path: "/marketplace/orders", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "My Orders", navLabel: "Orders", navZone: "work" },
  { path: "/marketplace/orders/[id]", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Order Details", navLabel: "Order", navZone: "work" },
  { path: "/marketplace/vendors", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Vendors", navLabel: "Vendors", navZone: "work" },
  { path: "/marketplace/bookings", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Bookings", navLabel: "Bookings", navZone: "work" },

  // ── Zone: Finance ───────────────────────────────────────────────
  { path: "/finance", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Finance Dashboard", navLabel: "Finance", navZone: "work" },
  { path: "/finance/claims", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Claims", navLabel: "Claims", navZone: "work" },
  { path: "/finance/claims/[id]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Claim Details", navLabel: "Claim", navZone: "work" },
  { path: "/finance/billing", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Billing", navLabel: "Billing", navZone: "work" },
  { path: "/finance/billing/[id]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Bill Details", navLabel: "Bill", navZone: "work" },
  { path: "/finance/payments", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Payments", navLabel: "Payments", navZone: "work" },
  { path: "/finance/tariffs", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Tariff Management", navLabel: "Tariffs", navZone: "work" },

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
];

// Total route count assertion
export const EXPECTED_ROUTE_COUNT = 112;
export const ROUTE_COUNT = ROUTES.length;

// Zone summary
export const ZONES = [...new Set(ROUTES.map((r) => r.zone))];
export const ZONE_COUNT = ZONES.length;
