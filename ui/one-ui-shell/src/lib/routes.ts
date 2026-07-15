/**
 * Experience UI â€” Complete Route Registry
 *
 * 398 routes across canonical zones (see ZONES export).
 * Each route specifies: path, zone, layout, sidebar context, guard, page title, and nav label.
 *
 * Zones: auth, home, facility, workspace, shift, queue, ehr, admin, registry,
 * marketplace, finance, pharmacy, inventory, reports, settings,
 * wellness, caregiving, monitoring, discovery, lab, operations,
 * support, developer, and others derived from ROUTES.
 */

import { ADMINISTRATION_GOVERNANCE_ROUTES } from "./administration-governance/route-registry";

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
  // â”€â”€ Zone: Auth (4 pathways) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/bootstrap", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Platform Bootstrap", navLabel: "Bootstrap" },
  { path: "/auth/login", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Sign In", navLabel: "Sign In" },
  { path: "/auth/login/email", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Sign In with Email", navLabel: "Email Login" },
  { path: "/auth/login/provider-id", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Sign In with Provider ID", navLabel: "Provider ID Login" },
  { path: "/auth/login/biometric", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Biometric Verification", navLabel: "Biometric" },
  { path: "/auth/forgot-password", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Forgot Password", navLabel: "Forgot Password" },
  { path: "/auth/reset-password", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Reset Password", navLabel: "Reset Password" },
  { path: "/auth/mfa", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Multi-Factor Authentication", navLabel: "MFA" },
  { path: "/auth/logout", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Signing Out", navLabel: "Sign Out" },
  { path: "/auth", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Authentication", navLabel: "Auth" },
  { path: "/auth/register", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Create Account", navLabel: "Register" },
  { path: "/auth/register/assurance", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Identity Assurance", navLabel: "Assurance" },
  { path: "/auth/register/status", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Registration Status", navLabel: "Registration Status" },
  { path: "/auth/resolving", zone: "auth", layout: "auth", sidebar: "main", guard: "none", pageTitle: "Resolving Session", navLabel: "Resolving" },
  { path: "/auth/context-chooser", zone: "auth", layout: "auth", sidebar: "main", guard: "auth", pageTitle: "Choose Work Context", navLabel: "Context Chooser" },

  // ── Zone: Public L0 (guest entry, no login — G-CZO-02) ──────────────
  { path: "/welcome", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Welcome to Impilo", navLabel: "Welcome" },
  { path: "/welcome/find-care", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Find Care", navLabel: "Find Care" },
  { path: "/welcome/emergency", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Emergency & Public Health", navLabel: "Emergency" },
  { path: "/welcome/health-info", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Health Information", navLabel: "Health Info" },
  { path: "/welcome/accessibility", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Accessibility & Language", navLabel: "Accessibility" },

  // â”€â”€ Zone: Legal / Consent â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/privacy", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Privacy Policy", navLabel: "Privacy Policy" },
  { path: "/terms", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Terms of Use", navLabel: "Terms of Use" },
  { path: "/consent", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Review Policies", navLabel: "Consent" },
  { path: "/account-deletion", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Account Deletion", navLabel: "Account Deletion" },
  { path: "/privacy/app-stores", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "App Store Privacy", navLabel: "App Store Privacy" },

  // â”€â”€ Zone: Home â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  // â”€â”€ Zone: Clinical Hub â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/clinical", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Clinical Care", navLabel: "Clinical Hub", navZone: "work" },
  { path: "/core-transaction", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Core Transaction", navLabel: "Core Transaction", navZone: "work" },
  { path: "/core-transaction/[transactionId]", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Core Transaction Detail", navLabel: "Transaction Detail", navZone: "work" },
  { path: "/client-journey", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Client Journey", navLabel: "Client Journey", navZone: "life" },
  { path: "/provider-workspace", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Provider Workspace", navLabel: "Provider Workspace", navZone: "work" },
  { path: "/provider-workspace/wellness", zone: "wellness", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Wellness Workbench", navLabel: "Wellness workbench", navZone: "professional" },
  { path: "/provider-workspace/wellness/social", zone: "wellness", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Wellness-Social Workbench", navLabel: "Wellness-social workbench", navZone: "professional" },
  { path: "/platform-journey", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Platform Journey", navLabel: "Platform Journey", navZone: "professional" },

  // â”€â”€ Zone: Clinical Tools â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/clinical-tools", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Clinical Tools", navLabel: "Tools", navZone: "work" },
  { path: "/clinical-tools/rules", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Rules Engine", navLabel: "Rules Engine", navZone: "work" },
  { path: "/clinical-tools/forms", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Form Builder", navLabel: "Form Builder", navZone: "work" },
  { path: "/clinical/control-tower", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Control Tower", navLabel: "Control Tower", navZone: "work" },
  { path: "/clinical/dictation", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Voice Dictation", navLabel: "Dictation", navZone: "work" },
  { path: "/clinical/emergency", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "ED / Casualty", navLabel: "ED / Casualty", navZone: "work" },

  // Inpatient workspace (Wave 20 production readiness)
  { path: "/clinical/inpatient", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Inpatient Care", navLabel: "Inpatient", navZone: "work" },
  { path: "/clinical/inpatient/admissions", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Inpatient Admissions", navLabel: "Admissions", navZone: "work" },
  { path: "/clinical/inpatient/admissions/[admissionId]", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Inpatient Episode", navLabel: "Episode", navZone: "work" },
  { path: "/clinical/inpatient/ward-board", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Ward Board", navLabel: "Ward Board", navZone: "work" },
  { path: "/clinical/inpatient/nursing", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Nursing Workbench", navLabel: "Nursing", navZone: "work" },
  { path: "/clinical/inpatient/rounds", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Medical Rounds", navLabel: "Rounds", navZone: "work" },
  { path: "/clinical/inpatient/discharge/[admissionId]", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Inpatient Discharge", navLabel: "Discharge", navZone: "work" },
  { path: "/clinical/inpatient/escalations", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Deterioration Escalations", navLabel: "Escalations", navZone: "work" },
  { path: "/clinical/inpatient/discharge-board", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Discharge Board", navLabel: "Discharge Board", navZone: "work" },

  // Production Command Centre (Health OS discoverability)
  { path: "/production-command-centre", zone: "admin", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Production Command Centre", navLabel: "Command Centre", navZone: "professional" },
  { path: "/platform/all-features", zone: "admin", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "All Features", navLabel: "All Features", navZone: "professional" },
  { path: "/health-os/command-centre", zone: "admin", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Health OS Command Centre", navLabel: "Command Centre", navZone: "professional" },

  // Data & Intelligence Plane hub (Wave 20)
  { path: "/data-intelligence", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Data & Intelligence", navLabel: "Data & Intelligence", navZone: "professional" },
  { path: "/data-intelligence/quality", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Data Quality", navLabel: "Data Quality", navZone: "professional" },
  { path: "/data-intelligence/pipelines", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Data Pipelines", navLabel: "Pipelines", navZone: "professional" },
  { path: "/data-intelligence/integration", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Integration Monitor", navLabel: "Integration", navZone: "professional" },
  { path: "/data-intelligence/reports", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Reporting Hub", navLabel: "Reports Hub", navZone: "professional" },
  { path: "/data-intelligence/audit", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Audit Intelligence", navLabel: "Audit Intel", navZone: "professional" },

  // Ndila geospatial workspace
  { path: "/ndila", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Ndila Maps", navLabel: "Ndila", navZone: "work" },

  // â”€â”€ Zone: Public Health â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/public-health", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PUBLIC_HEALTH", pageTitle: "Public Health", navLabel: "Public Health", navZone: "professional" },
  { path: "/public-health/surveillance", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PUBLIC_HEALTH", pageTitle: "Surveillance", navLabel: "Surveillance", navZone: "professional" },
  { path: "/public-health/campaigns", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PUBLIC_HEALTH", pageTitle: "Campaigns", navLabel: "Campaigns", navZone: "professional" },
  { path: "/public-health/site-registry", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PUBLIC_HEALTH", pageTitle: "Site Registry", navLabel: "Site Registry", navZone: "professional" },
  { path: "/public-health/site-registry/[siteId]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PUBLIC_HEALTH", pageTitle: "Site Profile", navLabel: "Site Profile", navZone: "professional" },
  { path: "/public-health/oversight", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PUBLIC_HEALTH", pageTitle: "National oversight", navLabel: "Oversight", navZone: "professional" },

  // â”€â”€ Zone: Omnichannel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/omnichannel", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Omnichannel Hub", navLabel: "Omnichannel", navZone: "professional" },

  // â”€â”€ Zone: Coverage â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/coverage", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Coverage Operations", navLabel: "Coverage", navZone: "professional" },
  { path: "/coverage/enroll", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Enroll in Coverage", navLabel: "Enroll", navZone: "life" },
  { path: "/coverage/member", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Coverage", navLabel: "My Coverage", navZone: "life" },
  { path: "/coverage/contracts", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Provider Contracts", navLabel: "Contracts", navZone: "professional" },

  // â”€â”€ Zone: Identity Services â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/id-services", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Identity Services", navLabel: "ID Services", navZone: "professional" },
  { path: "/ai-governance", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "AI Governance", navLabel: "AI Governance", navZone: "professional" },
  { path: "/ai-governance/models/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "AI Model", navLabel: "AI Model", navZone: "professional" },
  { path: "/access", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Access Channels", navLabel: "Access", navZone: "professional" },

  // â”€â”€ Zone: Kiosk (public) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/kiosk", zone: "auth", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Self Check-In", navLabel: "Kiosk" },

  // â”€â”€ Zone: Home â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Home", navLabel: "Home", navZone: "life" },
  { path: "/home", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Home", navLabel: "Home", navZone: "life" },
  { path: "/home/notifications", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Notifications", navLabel: "Notifications", navZone: "life" },
  { path: "/home/profile", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Profile", navLabel: "Profile", navZone: "life" },
  { path: "/home/preferences", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Preferences", navLabel: "Preferences", navZone: "life" },
  { path: "/home/credentials", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Credentials & CPD", navLabel: "Credentials", navZone: "professional" },
  { path: "/home/referrals", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Referrals", navLabel: "Referrals", navZone: "life" },
  { path: "/home/medications", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Medications", navLabel: "Medications", navZone: "life" },
  { path: "/home/conditions", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Conditions", navLabel: "Conditions", navZone: "life" },
  { path: "/home/allergies", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Allergies", navLabel: "Allergies", navZone: "life" },
  { path: "/home/results", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Results", navLabel: "Results", navZone: "life" },
  { path: "/home/bookings", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Bookings", navLabel: "My Bookings", navZone: "life" },
  { path: "/home/bookings/new", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Book a Service", navLabel: "Book a Service", navZone: "life" },
  { path: "/home/bookings/[bookingId]", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Booking Details", navLabel: "Booking", navZone: "life" },
  { path: "/home/appointments", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Appointments", navLabel: "My Appointments", navZone: "life" },
  { path: "/home/appointments/[appointmentId]", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Appointment Details", navLabel: "Appointment", navZone: "life" },
  { path: "/citizen", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Citizen Services", navLabel: "Citizen Services", navZone: "life" },
  { path: "/citizen/my-care", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Care", navLabel: "My Care", navZone: "life" },
  { path: "/citizen/virtual-care", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Virtual Care Requests", navLabel: "Virtual Care", navZone: "life" },
  { path: "/citizen/virtual-care/request", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Request a Teleconsult", navLabel: "Request Teleconsult", navZone: "life" },
  { path: "/citizen/health-id/qr", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Health ID QR", navLabel: "Health ID QR", navZone: "life" },
  { path: "/citizen/health-id/request", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Request Health ID", navLabel: "Request Health ID", navZone: "life" },
  { path: "/citizen/id-recovery", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "ID Recovery", navLabel: "ID Recovery", navZone: "life" },
  { path: "/citizen/delegated-pickup", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Delegated Pickup", navLabel: "Delegated Pickup", navZone: "life" },
  { path: "/citizen/record-sharing", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Share My Record", navLabel: "Record sharing", navZone: "life" },
  { path: "/citizen/visit/[transactionId]", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Visit", navLabel: "My Visit", navZone: "life" },
  { path: "/citizen/inpatient/[admissionRef]", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Inpatient Stay", navLabel: "My Stay", navZone: "life" },
  // Unified Person Health Wallet — the person anchor experience (MY LIFE · MY HEALTH).
  { path: "/citizen/wallet", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Mushe Personal Health Wallet", navLabel: "Mushe Wallet", navZone: "life" },
  { path: "/citizen/wallet/identity", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Identity & Trust", navLabel: "Identity & Trust", navZone: "life" },
  { path: "/citizen/wallet/profile", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Profile & Corrections", navLabel: "Profile", navZone: "life" },
  { path: "/citizen/wallet/records", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Health Records", navLabel: "Health Records", navZone: "life" },
  { path: "/citizen/wallet/timeline", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Care Timeline", navLabel: "Care Timeline", navZone: "life" },
  { path: "/citizen/wallet/dependants", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Dependants & Proxy", navLabel: "Dependants", navZone: "life" },
  { path: "/citizen/wallet/payments", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Payments & Bills", navLabel: "Payments", navZone: "life" },
  { path: "/citizen/wallet/comms", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Communication Preferences", navLabel: "Comms Preferences", navZone: "life" },
  // E2-TRUST: four-block doctrine trust profile (identity/professional/employment/operational).
  { path: "/citizen/wallet/trust", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Trust Profile", navLabel: "Trust Profile", navZone: "life" },
  { path: "/verify/credential", zone: "home", layout: "app", sidebar: "main", guard: "none", pageTitle: "Verify Credential", navLabel: "Verify Credential", navZone: "life" },
  // HPA regulatory UX (Jul 2026): public facility-certificate verifier (guard mirrors /verify/credential)
  // + the practitioner's own PIC-nomination ledger.
  { path: "/verify/facility-certificate", zone: "home", layout: "app", sidebar: "main", guard: "none", pageTitle: "Verify Facility Certificate", navLabel: "Verify Facility Certificate", navZone: "life" },
  // Gateway W2 (public lane): verify a health professional by registration number (guard mirrors /verify/facility-certificate)
  { path: "/verify/practitioner", zone: "home", layout: "app", sidebar: "main", guard: "none", pageTitle: "Verify a Health Professional", navLabel: "Verify Health Professional", navZone: "life" },
  { path: "/professional/pic-nominations", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "PIC Nominations", navLabel: "PIC Nominations", navZone: "professional" },
  { path: "/share/claim", zone: "home", layout: "app", sidebar: "main", guard: "none", pageTitle: "Claim Shared Documents", navLabel: "Claim Shared Documents", navZone: "life" },
  { path: "/collaboration/access", zone: "home", layout: "minimal", sidebar: "main", guard: "none", pageTitle: "Provider collaboration access", navLabel: "Collaboration access", navZone: "life" },

  // â”€â”€ Zone: Facility Selection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/facility", zone: "facility", layout: "app", sidebar: "facility", guard: "auth", pageTitle: "Select Facility", navLabel: "Facilities" },
  { path: "/facility/[id]", zone: "facility", layout: "app", sidebar: "facility", guard: "auth", pageTitle: "Facility Details", navLabel: "Facility" },
  { path: "/facility/[id]/configuration", zone: "facility", layout: "app", sidebar: "facility", guard: "role", requiredRole: "ADMIN", pageTitle: "Facility Configuration", navLabel: "Configuration" },
  { path: "/facility/claim", zone: "facility", layout: "app", sidebar: "facility", guard: "auth", pageTitle: "Claim facility administration", navLabel: "Claim administration" },

  // â”€â”€ Zone: Workspace Selection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/workspace", zone: "workspace", layout: "app", sidebar: "workspace", guard: "facility", pageTitle: "Select Workspace", navLabel: "Workspaces" },
  { path: "/workspace/[id]", zone: "workspace", layout: "app", sidebar: "workspace", guard: "facility", pageTitle: "Workspace Details", navLabel: "Workspace" },

  // â”€â”€ Zone: Shift â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/shift", zone: "shift", layout: "app", sidebar: "shift", guard: "workspace", pageTitle: "Start Shift", navLabel: "Shift" },
  { path: "/shift/active", zone: "shift", layout: "app", sidebar: "shift", guard: "facility", pageTitle: "Active Shift", navLabel: "Active Shift" },
  { path: "/shift/handover", zone: "shift", layout: "app", sidebar: "shift", guard: "facility", pageTitle: "Shift Handover", navLabel: "Handover" },

  // â”€â”€ Zone: Scheduling (workspace guard; ORGANIZATION_ADMIN may pass with facility only â€” AuthGuardProvider)
  { path: "/scheduling", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Scheduling", navLabel: "Scheduling", navZone: "work" },
  { path: "/scheduling/roster", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Staff Roster", navLabel: "Roster", navZone: "work" },
  { path: "/scheduling/on-call", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "On-Call Schedule", navLabel: "On-Call", navZone: "work" },
  { path: "/scheduling/noticeboard", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Provider Noticeboard", navLabel: "Noticeboard", navZone: "work" },
  { path: "/scheduling/booking-requests", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Booking Requests", navLabel: "Booking Requests", navZone: "work" },
  { path: "/scheduling/today", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Today's Appointments", navLabel: "Today", navZone: "work" },
  { path: "/scheduling/bookings/config", zone: "queue", layout: "app", sidebar: "queue", guard: "workspace", pageTitle: "Booking Configuration", navLabel: "Booking Config", navZone: "work" },

  // â”€â”€ Zone: Communication â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/communication", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Khuluma — Communication Hub", navLabel: "Communication", navZone: "work" },
  { path: "/communication/secure-messaging", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Khuluma — Secure Messaging", navLabel: "Messaging", navZone: "work" },
  { path: "/communication/approvals", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Khuluma — Comms Approval Queue", navLabel: "Approvals", navZone: "work" },
  { path: "/communication/announcements", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Khuluma — Facility Announcements", navLabel: "Announcements", navZone: "work" },
  // Khuluma — Impilo Comms Hub (the comms orchestration umbrella): unified conversations, presence, calls, meetings.
  { path: "/work/comms", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Khuluma — Comms Hub", navLabel: "Khuluma", navZone: "work" },
  { path: "/my/comms", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Khuluma — Messages", navLabel: "Khuluma", navZone: "life" },

  // â”€â”€ Zone: Queue (Clinical) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/queue", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Patient Queue", navLabel: "Queue", navZone: "work" },
  { path: "/queue/triage", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Triage Queue", navLabel: "Triage", navZone: "work" },
  { path: "/queue/waiting", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Waiting Room", navLabel: "Waiting", navZone: "work" },
  { path: "/queue/search", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Patient Search", navLabel: "Search", navZone: "work" },
  { path: "/queue/walk-in", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Walk-in Registration", navLabel: "Walk-in", navZone: "work" },
  { path: "/queue/scheduled", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Scheduled Visits", navLabel: "Scheduled", navZone: "work" },
  { path: "/queue/incoming-referrals", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Incoming Referrals", navLabel: "Incoming Referrals", navZone: "work" },

  // â”€â”€ Zone: EHR (Clinical) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/ehr/[patientId]", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Patient Chart", navLabel: "Chart", navZone: "work" },
  { path: "/ehr/[patientId]/summary", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Patient Summary", navLabel: "Summary", navZone: "work" },
  { path: "/ehr/[patientId]/ips", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "International Patient Summary", navLabel: "IPS", navZone: "work" },
  { path: "/ehr/[patientId]/vitals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Vitals", navLabel: "Vitals", navZone: "work" },
  { path: "/ehr/[patientId]/maternity", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Maternity Monitoring", navLabel: "Maternity", navZone: "work" },
  { path: "/ehr/[patientId]/history", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Medical History", navLabel: "History", navZone: "work" },
  { path: "/ehr/[patientId]/conditions", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Conditions", navLabel: "Conditions", navZone: "work" },
  { path: "/ehr/[patientId]/medications", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Medications", navLabel: "Medications", navZone: "work" },
  { path: "/ehr/[patientId]/allergies", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Allergies", navLabel: "Allergies", navZone: "work" },
  { path: "/ehr/[patientId]/orders", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Orders", navLabel: "Orders", navZone: "work" },
  { path: "/ehr/[patientId]/results", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Results", navLabel: "Results", navZone: "work" },
  { path: "/ehr/[patientId]/notes", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Clinical Notes", navLabel: "Notes", navZone: "work" },
  { path: "/ehr/[patientId]/documents", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Documents", navLabel: "Documents", navZone: "work" },
  { path: "/ehr/[patientId]/encounters", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Encounters", navLabel: "Encounters", navZone: "work" },
  { path: "/ehr/[patientId]/encounter/[encounterId]", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Encounter", navLabel: "Encounter", navZone: "work" },
  { path: "/ehr/[patientId]/immunizations", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Immunizations", navLabel: "Immunizations", navZone: "work" },
  { path: "/ehr/[patientId]/consults", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Consults & Referrals", navLabel: "Consults", navZone: "work" },
  { path: "/ehr/[patientId]/referrals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Referrals", navLabel: "Referrals", navZone: "work" },
  { path: "/ehr/[patientId]/teleconsults", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Teleconsults", navLabel: "Teleconsults", navZone: "work" },
  { path: "/ehr/[patientId]/timeline", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Timeline", navLabel: "Timeline", navZone: "work" },
  { path: "/ehr/[patientId]/discharge", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Discharge", navLabel: "Discharge", navZone: "work" },
  { path: "/ehr/[patientId]/care-plans", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Care Plans", navLabel: "Care Plans", navZone: "work" },
  { path: "/ehr/[patientId]/procedures", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Procedures", navLabel: "Procedures", navZone: "work" },
  { path: "/ehr/[patientId]/growth-chart", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Growth Chart", navLabel: "Growth Chart", navZone: "work" },
  { path: "/ehr/[patientId]/family-history", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Family History", navLabel: "Family History", navZone: "work" },
  { path: "/ehr/[patientId]/social-history", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Social History", navLabel: "Social History", navZone: "work" },
  { path: "/ehr/[patientId]/functional-status", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Functional Status", navLabel: "Functional Status", navZone: "work" },
  { path: "/ehr/[patientId]/advance-directives", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Advance Directives", navLabel: "Advance Directives", navZone: "work" },
  { path: "/ehr/[patientId]/care-team", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Care Team", navLabel: "Care Team", navZone: "work" },
  { path: "/ehr/[patientId]/preferences/communications", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Communication Preferences", navLabel: "Communications", navZone: "work" },
  { path: "/ehr/[patientId]/goals", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Goals", navLabel: "Goals", navZone: "work" },
  { path: "/ehr/[patientId]/assessments", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Assessments", navLabel: "Assessments", navZone: "work" },
  { path: "/ehr/[patientId]/charts", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Ward Charts", navLabel: "Charts", navZone: "work" },
  { path: "/ehr/[patientId]/imaging", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Imaging", navLabel: "Imaging", navZone: "work" },
  { path: "/ehr/[patientId]/investigations", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "Investigations", navLabel: "Investigations", navZone: "work" },
  { path: "/ehr/[patientId]/imaging/viewer", zone: "ehr", layout: "ehr", sidebar: "ehr", guard: "facility", pageTitle: "DICOM Viewer", navLabel: "Viewer", navZone: "work" },

  // â”€â”€ Zone: Admin / TSHEPO Governance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/admin", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Administration", navLabel: "Admin", navZone: "professional" },
  { path: "/admin/users", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Worker & Provider Access", navLabel: "Worker Access", navZone: "professional" },
  { path: "/admin/comms-ops", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Comms Operations", navLabel: "Comms Ops", navZone: "professional" },
  { path: "/admin/users/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "User Details", navLabel: "User", navZone: "professional" },
  { path: "/admin/roles", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Role Management", navLabel: "Roles", navZone: "professional" },
  { path: "/admin/policies", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Policy Management", navLabel: "Policies", navZone: "professional" },
  { path: "/admin/audit", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Audit Trail", navLabel: "Audit", navZone: "professional" },
  { path: "/admin/audit/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Audit Entry", navLabel: "Audit Detail", navZone: "professional" },
  { path: "/admin/workforce-intake", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Workforce Intake", navLabel: "Workforce Intake", navZone: "professional" },
  { path: "/admin/facility-imports", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Facility Import Batches", navLabel: "Facility Imports", navZone: "professional" },
  { path: "/admin/facility-imports/[runId]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Facility Import Batch", navLabel: "Import Batch", navZone: "professional" },
  { path: "/admin/facility-imports/[runId]/review", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Facility Import Review", navLabel: "Import Review", navZone: "professional" },
  { path: "/admin/consent", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Consent Management", navLabel: "Consent", navZone: "professional" },
  { path: "/admin/devices", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Device Management", navLabel: "Devices", navZone: "professional" },
  { path: "/admin/keys", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Key Management", navLabel: "Keys", navZone: "professional" },
  { path: "/admin/federation", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Federation", navLabel: "Federation", navZone: "professional" },
  { path: "/admin/tenants", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Tenant Management", navLabel: "Tenants", navZone: "professional" },
  { path: "/admin/break-glass", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Break Glass Log", navLabel: "Break Glass", navZone: "professional" },
  { path: "/admin/beds", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Bed & Ward Admin", navLabel: "Beds", navZone: "professional" },
  { path: "/admin/queues", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Queue Configuration", navLabel: "Queues", navZone: "professional" },
  { path: "/admin/data-export", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Data Export", navLabel: "Data Export", navZone: "professional" },
  { path: "/admin/data-governance", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Data Governance", navLabel: "Data Governance", navZone: "professional" },
  { path: "/admin/clinical-curation", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Clinical Knowledge Curation", navLabel: "Clinical Curation", navZone: "professional" },
  { path: "/admin/system-monitor", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "System Monitor", navLabel: "System Monitor", navZone: "professional" },
  { path: "/admin/integration-status", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Integration Status", navLabel: "Integrations", navZone: "professional" },
  { path: "/admin/notifications/templates", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Notification Templates", navLabel: "Notification Templates", navZone: "professional" },
  { path: "/admin/integration-templates", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Integration Templates", navLabel: "Integration Templates", navZone: "professional" },
  { path: "/admin/sidecar-retirement", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Sidecar Retirement", navLabel: "Sidecar Retirement", navZone: "professional" },
  { path: "/dags", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Data Access Governance", navLabel: "DAGS", navZone: "professional" },
  { path: "/dags/policy", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Data Access Policy", navLabel: "DAGS Policy", navZone: "professional" },

  // â”€â”€ Administrative plane landings (operational context: registry_admin / organization_admin) â”€â”€
  { path: "/registry-admin", zone: "admin", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Registry Administration", navLabel: "Registry Admin", navZone: "professional" },
  { path: "/registry-admin/trust-console", zone: "admin", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Trust Console", navLabel: "Trust Console", navZone: "professional" },
  { path: "/registry-admin/activation-letter", zone: "admin", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Activation Letter", navLabel: "Activation Letter", navZone: "professional" },
  { path: "/registry-admin/fee-schedules", zone: "admin", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Regulatory fee schedule", navLabel: "Fee schedule", navZone: "professional" },
  { path: "/organization-admin", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Organization Administration", navLabel: "Org Admin", navZone: "professional" },
  { path: "/organization-admin/facility", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Facility Administration", navLabel: "Org Facility", navZone: "professional" },
  { path: "/organization-admin/staffing", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Staffing & Scheduling", navLabel: "Org Staffing", navZone: "professional" },
  { path: "/organization-admin/governance", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Organisations & governance", navLabel: "Governance", navZone: "professional" },
  { path: "/organization-admin/governance/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Organisation detail", navLabel: "Org detail", navZone: "professional" },

  // â”€â”€ Zone: Registry â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/registry/clients", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Client Registry", navLabel: "Client Registry", navZone: "professional" },
  { path: "/registry/clients/new", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "New Client Registration", navLabel: "New Client", navZone: "professional" },
  { path: "/registry/clients/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Client Identity Workspace", navLabel: "Client", navZone: "professional" },
  { path: "/registry/trust", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Trust & Federation", navLabel: "Trust", navZone: "professional" },
  { path: "/registry/mvumo", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Mvumo â€” Digital Consent", navLabel: "Mvumo", navZone: "professional" },
  { path: "/registry", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Registry Hub", navLabel: "Registry", navZone: "professional" },
  { path: "/registry/intake", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Registry Intake", navLabel: "Intake", navZone: "professional" },
  { path: "/registry/locality-review", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Locality gazetteer review", navLabel: "Locality review", navZone: "professional" },
  { path: "/registry/facility-lifecycle", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGULATORY_AUTHORITY", pageTitle: "Facility regulatory lifecycle", navLabel: "Facility lifecycle", navZone: "professional" },
  { path: "/registry/facility-lifecycle/[facilityId]", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGULATORY_AUTHORITY", pageTitle: "Facility regulatory file", navLabel: "Facility file", navZone: "professional" },
  { path: "/registry/providers", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Provider Registry", navLabel: "Providers", navZone: "professional" },
  { path: "/registry/providers/verification", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "ADMIN", pageTitle: "Provider Verification Queue", navLabel: "Verification", navZone: "professional" },
  { path: "/registry/providers/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Provider Profile", navLabel: "Provider", navZone: "professional" },
  { path: "/registry/provider-council/self-service", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Council self-service", navLabel: "Council self-service", navZone: "professional" },
  { path: "/registry/provider-council/council-workspace", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Council operations", navLabel: "Council ops", navZone: "professional" },
  { path: "/registry/facility-classification", zone: "registry", layout: "app", sidebar: "registry", guard: "role", requiredRole: "REGISTRY_ADMIN", pageTitle: "Facility classification reconciliation", navLabel: "Classification", navZone: "professional" },
  { path: "/registry/facilities", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Facility Registry", navLabel: "Facilities", navZone: "professional" },
  { path: "/registry/facilities/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Facility Profile", navLabel: "Facility", navZone: "professional" },
  { path: "/registry/terminology", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Terminology Browser", navLabel: "Terminology", navZone: "professional" },
  { path: "/registry/terminology/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Concept Details", navLabel: "Concept", navZone: "professional" },
  { path: "/registry/products", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Product Registry", navLabel: "Products", navZone: "professional" },
  { path: "/registry/products/[id]", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "Product Details", navLabel: "Product", navZone: "professional" },
  { path: "/ubomi", zone: "registry", layout: "app", sidebar: "registry", guard: "auth", pageTitle: "UBOMI Civil Registry", navLabel: "UBOMI", navZone: "professional" },

  // â”€â”€ Zone: Marketplace â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/marketplace", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Health Marketplace", navLabel: "Marketplace", navZone: "work" },
  { path: "/marketplace/catalog", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Service Catalog", navLabel: "Catalog", navZone: "work" },
  { path: "/marketplace/orders", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "My Orders", navLabel: "Orders", navZone: "work" },
  { path: "/marketplace/orders/[id]", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Order Details", navLabel: "Order", navZone: "work" },
  { path: "/marketplace/orders/[id]/pay", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Pay for Order", navLabel: "Pay", navZone: "work" },
  { path: "/marketplace/ops", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "MARKETPLACE_OPS", pageTitle: "Marketplace Operations", navLabel: "Marketplace Ops", navZone: "work" },
  { path: "/marketplace/vendor", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "VENDOR_FULFILMENT", pageTitle: "Vendor Fulfilment", navLabel: "Vendor Fulfilment", navZone: "work" },
  { path: "/marketplace/vendor/orders", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "VENDOR_FULFILMENT", pageTitle: "Vendor Orders", navLabel: "Vendor Orders", navZone: "work" },
  { path: "/marketplace/pickup", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "COMMERCE", pageTitle: "Pickup Handoff", navLabel: "Pickup", navZone: "work" },
  { path: "/marketplace/vendors", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Vendors", navLabel: "Vendors", navZone: "work" },
  { path: "/marketplace/bookings", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Bookings", navLabel: "Bookings", navZone: "work" },

  // Msika storefront lane (WS#4) — buyer storefront + seller centre + moderation
  { path: "/marketplace/store", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Marketplace Store", navLabel: "Store", navZone: "life" },
  { path: "/marketplace/store/search", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Search Listings", navLabel: "Search", navZone: "life" },
  { path: "/marketplace/store/listing/[id]", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Listing", navLabel: "Listing", navZone: "life" },
  { path: "/marketplace/store/activity", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "My Marketplace Activity", navLabel: "My Activity", navZone: "life" },
  { path: "/marketplace/establishment-guide", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Set Up Your Practice", navLabel: "Establishment Guide", navZone: "life" },
  { path: "/marketplace/seller", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "MARKETPLACE_SELL", pageTitle: "Seller Centre", navLabel: "Seller Centre", navZone: "work" },
  { path: "/marketplace/seller/listings", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "MARKETPLACE_SELL", pageTitle: "My Listings", navLabel: "My Listings", navZone: "work" },
  { path: "/marketplace/seller/listings/new", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "MARKETPLACE_SELL", pageTitle: "New Listing", navLabel: "New Listing", navZone: "work" },
  { path: "/marketplace/seller/moderation", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "MARKETPLACE_SELL", pageTitle: "Listing Moderation", navLabel: "Moderation", navZone: "work" },

  // â”€â”€ Zone: Finance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/finance", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Finance Dashboard", navLabel: "Finance", navZone: "work" },
  { path: "/finance/claims", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Claims", navLabel: "Claims", navZone: "work" },
  { path: "/finance/claims/[id]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Claim Details", navLabel: "Claim", navZone: "work" },
  { path: "/finance/billing", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Billing", navLabel: "Billing", navZone: "work" },
  { path: "/finance/billing/[id]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Bill Details", navLabel: "Bill", navZone: "work" },
  { path: "/finance/payments", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Payments", navLabel: "Payments", navZone: "work" },
  { path: "/finance/msika-governance", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "MSIKA_GOVERNANCE", pageTitle: "MSIKA Governance", navLabel: "MSIKA Governance", navZone: "work" },
  { path: "/finance/ledger", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Ledger", navLabel: "Ledger", navZone: "work" },
  { path: "/finance/workspace", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Finance Workspace", navLabel: "Workspace", navZone: "work" },
  { path: "/finance/settlements", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Settlements", navLabel: "Settlements", navZone: "work" },
  { path: "/finance/remittances", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Remittances", navLabel: "Remittances", navZone: "work" },
  { path: "/finance/reconciliation", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Reconciliation", navLabel: "Reconciliation", navZone: "work" },
  { path: "/finance/bank-reconciliation", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Bank Reconciliation", navLabel: "Bank reconciliation", navZone: "work" },
  { path: "/finance/failed-money-events", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Failed Money Events", navLabel: "Failed money events", navZone: "work" },
  { path: "/finance/refunds", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Refunds", navLabel: "Refunds", navZone: "work" },
  { path: "/finance/payer-ops", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Payer Operations", navLabel: "Payer Ops", navZone: "work" },
  { path: "/finance/payer-claims", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Payer Claims Queue", navLabel: "Payer Claims", navZone: "work" },
  { path: "/finance/payer-claims/[claimId]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "PAYER_OPS", pageTitle: "Payer Claim", navLabel: "Payer Claim", navZone: "work" },
  { path: "/finance/tariffs", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Tariff Management", navLabel: "Tariffs", navZone: "work" },
  { path: "/finance/costa", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "COSTA hub", navLabel: "COSTA", navZone: "work" },
  { path: "/finance/costa/encounter/[encounterId]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "COSTA encounter timeline", navLabel: "Encounter timeline", navZone: "work" },
  { path: "/finance/service-access", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Service access decisions", navLabel: "Service access", navZone: "work" },
  { path: "/finance/mushex-platform", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "MusheX platform admin", navLabel: "MusheX platform", navZone: "work" },
  { path: "/finance/mushex-platform/wallets/[walletId]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Custodial wallet", navLabel: "Custodial wallet", navZone: "work" },
  { path: "/finance/mushex-platform/remittance/[transferId]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Remittance transfer", navLabel: "Remittance transfer", navZone: "work" },
  { path: "/finance/mushex-platform/cards/[cardId]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Card profile", navLabel: "Card profile", navZone: "work" },
  { path: "/finance/mushex-platform/reversals/[reversalId]", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Reversal record", navLabel: "Reversal record", navZone: "work" },
  { path: "/finance/commerce-integrations", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Commerce & Payer Stack", navLabel: "Commerce Integrations", navZone: "work" },
  { path: "/finance/reports", zone: "finance", layout: "app", sidebar: "finance", guard: "role", requiredRole: "FINANCE", pageTitle: "Financial reports", navLabel: "Financial reports", navZone: "work" },
  { path: "/finance/my-account", zone: "finance", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Healthcare Account", navLabel: "My healthcare costs", navZone: "life" },
  { path: "/wallet", zone: "finance", layout: "app", sidebar: "finance", guard: "auth", pageTitle: "MusheX Wallet", navLabel: "MusheX Wallet", navZone: "life" },
  { path: "/wallet/deposit", zone: "finance", layout: "app", sidebar: "finance", guard: "auth", pageTitle: "Deposit", navLabel: "Deposit", navZone: "life" },
  { path: "/wallet/send", zone: "finance", layout: "app", sidebar: "finance", guard: "auth", pageTitle: "Send Money", navLabel: "Send", navZone: "life" },
  { path: "/wallet/transactions", zone: "finance", layout: "app", sidebar: "finance", guard: "auth", pageTitle: "Transactions", navLabel: "Transactions", navZone: "life" },
  { path: "/wallet/cards", zone: "finance", layout: "app", sidebar: "finance", guard: "auth", pageTitle: "Cards", navLabel: "Cards", navZone: "life" },
  { path: "/wallet/merchant", zone: "finance", layout: "app", sidebar: "finance", guard: "auth", pageTitle: "Merchant", navLabel: "Merchant", navZone: "life" },

  // â”€â”€ Zone: Beds & Wards â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/beds", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Bed Management", navLabel: "Beds", navZone: "work" },

  // â”€â”€ Zone: Pharmacy â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/pharmacy", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Pharmacy Dashboard", navLabel: "Pharmacy", navZone: "work" },
  { path: "/pharmacy/dispense", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Dispensing", navLabel: "Dispense", navZone: "work" },
  { path: "/pharmacy/stock", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Stock Management", navLabel: "Stock", navZone: "work" },
  { path: "/pharmacy/prescriptions", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Prescriptions", navLabel: "Prescriptions", navZone: "work" },
  { path: "/pharmacy/transaction-journey", zone: "pharmacy", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Rx Transaction Journey", navLabel: "Rx Journey", navZone: "work" },

  // â”€â”€ Zone: Inventory â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/inventory", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Inventory Dashboard", navLabel: "Inventory", navZone: "work" },
  { path: "/inventory/movements", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Stock Movements", navLabel: "Movements", navZone: "work" },
  { path: "/inventory/counts", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Stock Counts", navLabel: "Counts", navZone: "work" },
  { path: "/inventory/requisitions", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Requisitions", navLabel: "Requisitions", navZone: "work" },
  { path: "/inventory/stock-management", zone: "inventory", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Stock Management", navLabel: "Stock Management", navZone: "work" },

  // â”€â”€ Zone: Enterprise Resource Plane (inventory + procurement + finance fusion) â”€â”€
  { path: "/enterprise", zone: "enterprise", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Enterprise Resources", navLabel: "Enterprise", navZone: "work" },
  { path: "/enterprise/warehousing", zone: "enterprise", layout: "app", sidebar: "queue", guard: "role", requiredRole: "ADMIN", pageTitle: "Warehousing & distribution", navLabel: "Warehousing", navZone: "work" },
  { path: "/enterprise/fleet", zone: "enterprise", layout: "app", sidebar: "queue", guard: "role", requiredRole: "ADMIN", pageTitle: "Fleet & logistics", navLabel: "Fleet", navZone: "work" },
  { path: "/enterprise/charge-sheet", zone: "enterprise", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Charge sheet", navLabel: "Charge sheet", navZone: "work" },
  { path: "/enterprise/oversight", zone: "enterprise", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "National Enterprise Oversight", navLabel: "Oversight", navZone: "work" },

  // â”€â”€ Zone: Institutional ERP (COSTA-aligned back office) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/erp", zone: "enterprise", layout: "app", sidebar: "queue", guard: "role", requiredRole: "FINANCE", pageTitle: "Institutional ERP", navLabel: "ERP", navZone: "work" },
  { path: "/erp/gl", zone: "enterprise", layout: "app", sidebar: "queue", guard: "role", requiredRole: "FINANCE", pageTitle: "General ledger", navLabel: "GL", navZone: "work" },
  { path: "/erp/hr", zone: "enterprise", layout: "app", sidebar: "queue", guard: "role", requiredRole: "FINANCE", pageTitle: "HR & payroll", navLabel: "HR", navZone: "work" },
  { path: "/erp/procurement", zone: "enterprise", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Procurement", navLabel: "Procurement", navZone: "work" },
  { path: "/erp/assets", zone: "enterprise", layout: "app", sidebar: "queue", guard: "role", requiredRole: "FINANCE", pageTitle: "Fixed assets", navLabel: "Fixed assets", navZone: "work" },

  // â”€â”€ Zone: Reports â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  {
    path: "/workspace/aggregate",
    zone: "reports",
    layout: "app",
    sidebar: "admin",
    guard: "auth",
    pageTitle: "Aggregate oversight",
    navLabel: "Aggregate workspace",
    navZone: "professional",
  },
  { path: "/reports", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Reports", navLabel: "Reports", navZone: "professional" },
  { path: "/reports/facility", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Facility Reports", navLabel: "Facility Reports", navZone: "professional" },
  { path: "/reports/clinical", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Clinical Reports", navLabel: "Clinical Reports", navZone: "professional" },
  { path: "/reports/operational", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Operational Reports", navLabel: "Operational Reports", navZone: "professional" },
  { path: "/reports/custom", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Custom Reports", navLabel: "Custom Reports", navZone: "professional" },
  { path: "/reports/[id]", zone: "reports", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "Report Details", navLabel: "Report", navZone: "professional" },

  // â”€â”€ Zone: Settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/settings", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Settings", navLabel: "Settings", navZone: "professional" },
  { path: "/settings/account", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Account Settings", navLabel: "Account", navZone: "professional" },
  { path: "/settings/security", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Security Settings", navLabel: "Security", navZone: "professional" },
  { path: "/settings/notifications", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Notification Preferences", navLabel: "Notifications", navZone: "professional" },
  { path: "/settings/display", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Display Settings", navLabel: "Display", navZone: "professional" },
  { path: "/settings/integrations", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Integrations", navLabel: "Integrations", navZone: "professional" },
  { path: "/settings/privacy", zone: "settings", layout: "app", sidebar: "settings", guard: "auth", pageTitle: "Privacy & Data", navLabel: "Privacy & Data", navZone: "professional" },
  { path: "/telemedicine", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Telemedicine Hub", navLabel: "Telemedicine", navZone: "work" },
  { path: "/telemedicine/new", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "New Teleconsultation", navLabel: "New Teleconsult", navZone: "work" },
  { path: "/telemedicine/session/[sessionId]", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Teleconsult Session", navLabel: "Session", navZone: "work" },
  { path: "/telemedicine/analytics", zone: "queue", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Telemedicine Analytics", navLabel: "Telemedicine Analytics", navZone: "work" },
  { path: "/work/telemedicine/worklist", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Specialist Worklist", navLabel: "Specialist Worklist", navZone: "work" },
  { path: "/work/telemedicine/groups", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Clinical Groups", navLabel: "Clinical Groups", navZone: "work" },
  { path: "/work/telemedicine/virtual-hospitals", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Virtual Hospitals", navLabel: "Virtual Hospitals", navZone: "work" },
  { path: "/work/telemedicine/virtual-hospitals/[id]", zone: "queue", layout: "app", sidebar: "queue", guard: "auth", pageTitle: "Virtual Hospital", navLabel: "Virtual Hospital", navZone: "work" },

  // â”€â”€ Zone: Provider Activation (Health OS Â§6) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/provider/activate", zone: "auth", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Activate Provider Role", navLabel: "Provider Activation" },
  { path: "/provider/status", zone: "auth", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Provider Status", navLabel: "Provider Status" },
  // E2-TRUST: provider self-service claim / recovery journey (page already exists — nav discoverability).
  { path: "/citizen/provider-claim", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Claim Provider Profile", navLabel: "Provider Claim", navZone: "professional" },
  { path: "/citizen/provider-claim/status", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Provider Access Requests", navLabel: "Provider Access Requests", navZone: "professional" },
  { path: "/facility/[id]/mode", zone: "facility", layout: "app", sidebar: "facility", guard: "auth", pageTitle: "Facility Mode", navLabel: "Facility Mode" },

  // â”€â”€ Zone: Wellness (Health OS Â§2 â€” prevention, self-care, fitness) â”€
  { path: "/wellness", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Hub", navLabel: "Wellness", navZone: "life" },
  { path: "/wellness/dashboard", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Dashboard", navLabel: "Dashboard", navZone: "life" },
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
  { path: "/wellness/plans", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Plans & Journeys", navLabel: "Journeys", navZone: "life" },
  { path: "/wellness/care", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Connect to Care", navLabel: "Care linkage", navZone: "life" },
  { path: "/wellness/commodities", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Programme commodities (Dura)", navLabel: "Commodities (Dura)", navZone: "life" },
  { path: "/wellness/community", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Community", navLabel: "Community", navZone: "life" },
  { path: "/wellness/assessment", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Assessment", navLabel: "Assessment", navZone: "life" },
  { path: "/wellness/timeline", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Timeline", navLabel: "Timeline", navZone: "life" },
  { path: "/wellness/reminders", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Preventive Reminders", navLabel: "Reminders", navZone: "life" },
  { path: "/wellness/follow-ups", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Follow-ups", navLabel: "Follow-ups", navZone: "life" },
  { path: "/wellness/insights", zone: "wellness", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Wellness Insights", navLabel: "Insights", navZone: "professional" },
  { path: "/wellness/settings/consent", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Consent", navLabel: "Consent", navZone: "life" },
  { path: "/wellness/social/feed", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Community Feed", navLabel: "Feed", navZone: "life" },
  { path: "/wellness/social/reels", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Reels", navLabel: "Reels", navZone: "life" },
  { path: "/wellness/social/posts/[id]", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Post", navLabel: "Post", navZone: "life" },
  { path: "/wellness/social/saved", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Saved", navLabel: "Saved", navZone: "life" },
  { path: "/wellness/social/my-activity", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Activity", navLabel: "My activity", navZone: "life" },
  { path: "/wellness/social/groups", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Groups", navLabel: "Groups", navZone: "life" },
  { path: "/wellness/social/groups/new", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "New Group", navLabel: "New group", navZone: "life" },
  { path: "/wellness/social/groups/[id]", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Group", navLabel: "Group", navZone: "life" },
  { path: "/wellness/social/communities", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Communities", navLabel: "Communities", navZone: "life" },
  { path: "/wellness/social/communities/[id]", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Community", navLabel: "Community", navZone: "life" },
  { path: "/wellness/social/challenges", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Wellness Challenges", navLabel: "Challenges", navZone: "life" },
  { path: "/wellness/social/challenges/[id]", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Challenge", navLabel: "Challenge", navZone: "life" },
  { path: "/wellness/social/notifications", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Social Notifications", navLabel: "Notifications", navZone: "life" },
  { path: "/wellness/social/moderation", zone: "wellness", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Social Moderation", navLabel: "Moderation", navZone: "professional" },
  { path: "/wellness/social/programme-dashboard", zone: "wellness", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Programme Engagement", navLabel: "Programme engagement", navZone: "professional" },
  { path: "/social", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Social Timeline", navLabel: "Social", navZone: "life" },
  { path: "/social/drafts", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Draft Posts", navLabel: "Drafts", navZone: "life" },
  { path: "/social/saved", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Saved Posts", navLabel: "Saved", navZone: "life" },
  { path: "/social/moderation", zone: "wellness", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Social Moderation", navLabel: "Moderation", navZone: "professional" },
  { path: "/communities", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Communities", navLabel: "Communities", navZone: "life" },
  { path: "/communities/[id]", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Community", navLabel: "Community", navZone: "life" },
  { path: "/pages", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Pages", navLabel: "Pages", navZone: "life" },
  { path: "/pages/[id]", zone: "wellness", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Page", navLabel: "Page", navZone: "life" },

  // â”€â”€ Zone: Caregiving (Health OS Â§4 â€” delegated care, family) â”€â”€â”€â”€â”€â”€â”€
  { path: "/caregiving", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Caregiving Hub", navLabel: "Caregiving", navZone: "life" },
  { path: "/caregiving/dependants", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Dependants", navLabel: "Dependants", navZone: "life" },
  { path: "/caregiving/delegation", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Care Delegation", navLabel: "Delegation", navZone: "life" },
  { path: "/caregiving/tasks", zone: "caregiving", layout: "app", sidebar: "main", guard: "role", requiredRole: "CAREGIVER", pageTitle: "Care Tasks", navLabel: "Tasks", navZone: "life" },
  { path: "/caregiving/notifications", zone: "caregiving", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Care Alerts", navLabel: "Alerts", navZone: "life" },

  // â”€â”€ Zone: Remote Monitoring (Health OS Â§2 â€” devices, chronic care) â”€
  { path: "/monitoring", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Remote Monitoring", navLabel: "Monitoring", navZone: "life" },
  { path: "/monitoring/devices", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Devices", navLabel: "Devices", navZone: "life" },
  { path: "/monitoring/readings", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Readings & Trends", navLabel: "Readings", navZone: "life" },
  { path: "/monitoring/alerts", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Monitoring Alerts", navLabel: "Alerts", navZone: "life" },
  { path: "/monitoring/care-plans", zone: "monitoring", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Chronic Care Plans", navLabel: "Care Plans", navZone: "life" },
  { path: "/monitoring/provider-dashboard", zone: "monitoring", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Patient Monitoring Dashboard", navLabel: "Monitoring Dashboard", navZone: "work" },

  // â”€â”€ Zone: Service Discovery (Health OS Â§2 â€” find providers, facilities, services) â”€
  { path: "/discover", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Find Services", navLabel: "Discover", navZone: "life" },
  { path: "/discover/providers", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Find a Provider", navLabel: "Providers", navZone: "life" },
  { path: "/discover/facilities", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Find a Facility", navLabel: "Facilities", navZone: "life" },
  { path: "/discover/services", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Browse Services", navLabel: "Services", navZone: "life" },
  { path: "/discover/virtual-care", zone: "discovery", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Virtual Care", navLabel: "Virtual Care", navZone: "life" },

  // â”€â”€ Zone: Laboratory (absorbs oros-web sidecar) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/lab", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Laboratory", navLabel: "Lab", navZone: "work" },
  { path: "/lab/worklist", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Lab Worklist", navLabel: "Worklist", navZone: "work" },
  { path: "/imaging/worklist", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Imaging Worklist", navLabel: "Imaging Worklist", navZone: "work" },
  { path: "/diagnostics/orders", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Diagnostics Orders", navLabel: "Diagnostics Orders", navZone: "work" },
  { path: "/diagnostics/orders/new", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Create Diagnostic Order", navLabel: "New Order", navZone: "work" },
  { path: "/diagnostics/orders/route", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Route Order", navLabel: "Route Order", navZone: "work" },
  { path: "/diagnostics/results-inbox", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Results Inbox", navLabel: "Results Inbox", navZone: "work" },
  { path: "/diagnostics/critical-queue", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Critical Results", navLabel: "Critical Results", navZone: "work" },
  { path: "/diagnostics/worklist", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Imaging Worklist", navLabel: "Imaging Worklist", navZone: "work" },
  { path: "/diagnostics/lab-worklist", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Lab Worklist", navLabel: "Lab Worklist", navZone: "work" },
  { path: "/diagnostics/procedure-worklist", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Procedure Worklist", navLabel: "Procedure Worklist", navZone: "work" },
  { path: "/diagnostics/reporting", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Report Authoring", navLabel: "Reporting", navZone: "work" },
  { path: "/diagnostics/intake/qr", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Claim Order QR", navLabel: "QR Claim", navZone: "work" },
  { path: "/operations/diagnostics-reconciliation", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Diagnostics Reconciliation", navLabel: "Diagnostics Reconciliation", navZone: "work" },
  { path: "/admin/integrations", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Integration Status", navLabel: "Integrations", navZone: "professional" },
  { path: "/admin/diagnostics-catalogue", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Diagnostics Catalogue", navLabel: "Diagnostics Catalogue", navZone: "professional" },
  { path: "/imaging/facility", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Facility Imaging Dashboard", navLabel: "Imaging Ops", navZone: "work" },
  { path: "/lab/results", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Results Review", navLabel: "Results", navZone: "work" },
  { path: "/lab/catalog", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Test Catalog", navLabel: "Catalog", navZone: "work" },
  { path: "/lab/reconciliation", zone: "lab", layout: "app", sidebar: "queue", guard: "facility", pageTitle: "Lab Reconciliation", navLabel: "Reconciliation", navZone: "work" },

  // â”€â”€ Zone: Operations (absorbs ops-console sidecar) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/operations", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Operations", navLabel: "Operations", navZone: "professional" },
  { path: "/operations/facility-operations", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Facility Operations", navLabel: "Facility Operations", navZone: "professional" },
  { path: "/operations/facility-operations/district-view", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "District View", navLabel: "District View", navZone: "professional" },
  { path: "/operations/facility-operations/patient-flow", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Patient Flow", navLabel: "Patient Flow", navZone: "professional" },
  { path: "/operations/facility-operations/resources", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Resource Operations", navLabel: "Resource Ops", navZone: "professional" },
  { path: "/operations/workflows", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Workflow Orchestration", navLabel: "Workflows", navZone: "professional" },
  { path: "/operations/workflows/[instanceId]", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Workflow Instance", navLabel: "Workflow Instance", navZone: "professional" },
  { path: "/operations/dispatch", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Dispatch Operations", navLabel: "Dispatch", navZone: "professional" },
  { path: "/operations/dispatch/[taskId]", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Dispatch Task", navLabel: "Dispatch Task", navZone: "professional" },
  { path: "/operations/vito", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Identity Operations", navLabel: "Identity Ops", navZone: "professional" },
  { path: "/operations/vito/registration", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Client Registration", navLabel: "Registration", navZone: "professional" },
  { path: "/operations/vito/registration/new", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "New Registration", navLabel: "New Registration", navZone: "professional" },
  { path: "/operations/vito/issuance", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Issuance Queue", navLabel: "Issuance Queue", navZone: "professional" },
  { path: "/operations/vito/issuance/[requestId]", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Issuance Request", navLabel: "Issuance Request", navZone: "professional" },
  { path: "/operations/vito/cards", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Smart Cards", navLabel: "Smart Cards", navZone: "professional" },
  { path: "/operations/vito/cards/pickup", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Card Pickup", navLabel: "Card Pickup", navZone: "professional" },
  { path: "/operations/vito/match", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Match Review", navLabel: "Match Review", navZone: "professional" },
  { path: "/operations/vito/dedup", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Deduplication", navLabel: "Deduplication", navZone: "professional" },
  { path: "/operations/vito/print", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Print & Slips", navLabel: "Print & Slips", navZone: "professional" },
  { path: "/operations/vito/patient-shares", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Patient Shares", navLabel: "Patient Shares", navZone: "professional" },
  { path: "/operations/vito/internal-search", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Internal Search", navLabel: "Internal Search", navZone: "professional" },
  { path: "/operations/vito/biometrics", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Biometrics", navLabel: "Biometrics", navZone: "professional" },
  { path: "/operations/vito/recovery", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Recovery & SHS", navLabel: "Recovery & SHS", navZone: "professional" },
  { path: "/operations/vito/registry-admin", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Registry Admin", navLabel: "Registry Admin", navZone: "professional" },
  { path: "/operations/butano", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "SHR Operations", navLabel: "SHR Ops", navZone: "professional" },
  { path: "/operations/assets", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Asset Management", navLabel: "Assets", navZone: "professional" },
  { path: "/operations/equipment", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Equipment Management", navLabel: "Equipment", navZone: "professional" },
  { path: "/operations/equipment/[equipmentId]", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Equipment Detail", navLabel: "Equipment Detail", navZone: "professional" },
  { path: "/operations/equipment/maintenance", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Maintenance", navLabel: "Maintenance", navZone: "professional" },
  { path: "/operations/equipment/calibration", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Calibration", navLabel: "Calibration", navZone: "professional" },
  { path: "/operations/equipment/readiness", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Service Readiness", navLabel: "Readiness", navZone: "professional" },
  { path: "/operations/equipment/iot", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "IoT & Device Status", navLabel: "IoT Status", navZone: "professional" },
  { path: "/operations/equipment/deployment", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Deployment Kits", navLabel: "Deployment Kits", navZone: "professional" },
  { path: "/operations/equipment/audit", zone: "operations", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Asset Audit", navLabel: "Asset Audit", navZone: "professional" },

  // â”€â”€ Zone: Support (absorbs support-console sidecar) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/support", zone: "support", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Support", navLabel: "Support", navZone: "life" },
  { path: "/support/tickets", zone: "support", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Support Tickets", navLabel: "Tickets", navZone: "life" },
  { path: "/support/knowledge-base", zone: "support", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Knowledge Base", navLabel: "Help", navZone: "life" },

  // â”€â”€ Zone: Developer Portal (absorbs developer-console sidecar) â”€â”€â”€â”€â”€
  { path: "/developer", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Developer Portal", navLabel: "Developer", navZone: "professional" },
  { path: "/developer/api-catalog", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "API Catalog", navLabel: "API Catalog", navZone: "professional" },
  { path: "/developer/clients", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Client Registration", navLabel: "Clients", navZone: "professional" },
  { path: "/developer/sandbox", zone: "developer", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ADMIN", pageTitle: "Sandbox", navLabel: "Sandbox", navZone: "professional" },

  // â”€â”€ Home: Documents (absorbs self-service/my-documents sidecar) â”€â”€â”€â”€
  { path: "/home/documents", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Documents", navLabel: "Documents", navZone: "life" },

  // â”€â”€ Marketplace: Cart & Substitutions (absorbs msika-flow-portal sidecar) â”€â”€
  { path: "/marketplace/cart", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "auth", pageTitle: "Shopping Cart", navLabel: "Cart", navZone: "work" },
  { path: "/marketplace/substitutions", zone: "marketplace", layout: "app", sidebar: "marketplace", guard: "role", requiredRole: "MARKETPLACE_OPS", pageTitle: "Substitutions", navLabel: "Substitutions", navZone: "work" },

  // â”€â”€ Zone: Experience shell (OS-like utilities) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/shell/file-manager", zone: "shell", layout: "app", sidebar: "main", guard: "auth", pageTitle: "File manager", navLabel: "Files", navZone: "life" },
  { path: "/shell/task-manager", zone: "shell", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Task manager", navLabel: "Tasks", navZone: "life" },

  // â”€â”€ Zone: Intelligent Experience (Health OS Â§2a, Â§16a) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  { path: "/ask", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Ask", navLabel: "Ask", navZone: "life" },
  {
    path: "/intelligence",
    zone: "intelligent",
    layout: "app",
    sidebar: "main",
    guard: "auth",
    pageTitle: "Health Intelligence",
    navLabel: "Intelligence",
    navZone: "life",
  },
  { path: "/search", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Search", navLabel: "Search", navZone: "life" },
  { path: "/nompilo", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Nompilo", navLabel: "Nompilo", navZone: "life" },
  { path: "/guidance", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Guidance", navLabel: "Guidance", navZone: "life" },
  { path: "/guidance/reminders", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Reminders & Prompts", navLabel: "Reminders", navZone: "life" },
  { path: "/guidance/education", zone: "intelligent", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Health Education", navLabel: "Education", navZone: "life" },

  // â”€â”€ Zone: Impilo Fundo (workforce learning workspace â€” backed by services/learning-service) â”€â”€
  // Note: sidebar uses "main" because `SidebarContext` does not include a "professional" variant;
  // this mirrors `/home/credentials`, which is also in navZone: "professional".
  { path: "/learning", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Impilo Fundo", navLabel: "Impilo Fundo", navZone: "professional" },
  { path: "/learning/catalog", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Impilo Fundo Catalogue", navLabel: "Catalogue", navZone: "professional" },
  // Phase 6B (May 2026) â€” native course-detail surface backed by Phase 5B /v11/courses/{id}/structure.
  { path: "/learning/courses/[courseId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Impilo Fundo Course", navLabel: "Course" },
  { path: "/learning/my-learning", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Learning", navLabel: "My Learning", navZone: "professional" },
  { path: "/learning/enrolments/[enrolmentId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Enrolment Player", navLabel: "Enrolment" },
  { path: "/learning/enrolments/[enrolmentId]/lessons/[lessonId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Lesson Player", navLabel: "Lesson" },
  { path: "/learning/pathways", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Learning Pathways", navLabel: "Pathways", navZone: "professional" },
  { path: "/learning/pathways/[pathwayId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Pathway Detail", navLabel: "Pathway" },
  { path: "/learning/record", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Learning Record", navLabel: "Transcript", navZone: "professional" },
  { path: "/learning/assessments/[assessmentId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Assessment", navLabel: "Assessment" },
  { path: "/learning/assessments/[assessmentId]/attempt", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Assessment Attempt", navLabel: "Attempt" },
  { path: "/learning/attempts/[attemptId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Attempt Result", navLabel: "Attempt Result" },
  { path: "/learning/certificates", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Certificates", navLabel: "Certificates", navZone: "professional" },
  { path: "/learning/certificates/[certificateId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Certificate Detail", navLabel: "Certificate" },
  { path: "/learning/cpd", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "CPD Evidence", navLabel: "CPD Evidence", navZone: "professional" },
  { path: "/learning/reports", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Learning Reports", navLabel: "Reports", navZone: "professional" },
  { path: "/learning/reports/cohorts", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Cohort Report", navLabel: "Cohorts" },
  { path: "/learning/reports/courses", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Course Report", navLabel: "Courses" },
  { path: "/learning/reports/overdue", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Overdue Learning", navLabel: "Overdue" },
  { path: "/learning/reports/assessments", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Assessment Report", navLabel: "Assessments" },
  { path: "/learning/studio", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Fundo Studio", navLabel: "Studio", navZone: "professional" },
  { path: "/learning/studio/courses", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Courses", navLabel: "Studio Courses" },
  { path: "/learning/studio/courses/new", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio New Course", navLabel: "Studio New Course" },
  { path: "/learning/studio/courses/[courseId]", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Course Detail", navLabel: "Studio Course Detail" },
  { path: "/learning/studio/courses/[courseId]/builder", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Course Builder", navLabel: "Course Builder" },
  { path: "/learning/studio/library", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Library", navLabel: "Studio Library" },
  { path: "/learning/studio/media", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Media", navLabel: "Studio Media" },
  { path: "/learning/studio/media/recordings", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Media Recordings", navLabel: "Recordings" },
  { path: "/learning/studio/media/scripts", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Media Scripts", navLabel: "Scripts" },
  { path: "/learning/studio/media/voiceovers", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Media Voiceovers", navLabel: "Voiceovers" },
  { path: "/learning/studio/media/[mediaId]", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Media Asset", navLabel: "Media Asset" },
  { path: "/learning/studio/assessments", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Assessments", navLabel: "Studio Assessments" },
  { path: "/learning/studio/surveys", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Surveys", navLabel: "Studio Surveys" },
  { path: "/learning/studio/ai", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio AI", navLabel: "Studio AI" },
  { path: "/learning/studio/publish", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Publish", navLabel: "Publish" },
  { path: "/learning/studio/analytics", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Studio Analytics", navLabel: "Studio Analytics" },
  { path: "/learning/library", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Fundo Library", navLabel: "Library", navZone: "professional" },
  { path: "/learning/library/resources", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Library Resources", navLabel: "Resources" },
  { path: "/learning/library/uploads", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Library Uploads", navLabel: "Uploads" },
  { path: "/learning/library/[resourceId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Library Resource Detail", navLabel: "Resource Detail" },
  { path: "/learning/notifications", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Learning Notifications", navLabel: "Notifications", navZone: "professional" },
  { path: "/learning/surveys/[surveyId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Learning Survey", navLabel: "Survey" },
  { path: "/learning/surveys/[surveyId]/respond", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Respond to Survey", navLabel: "Respond Survey" },
  { path: "/learning/feedback/course/[courseId]", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Course Feedback", navLabel: "Course Feedback" },
  { path: "/learning/admin", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Fundo Admin", navLabel: "Fundo Admin", navZone: "professional" },
  { path: "/learning/admin/courses", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Admin Courses", navLabel: "Admin Courses" },
  { path: "/learning/admin/courses/new", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "New Course", navLabel: "New Course" },
  { path: "/learning/admin/courses/[courseId]/edit", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Edit Course", navLabel: "Edit Course" },
  { path: "/learning/admin/pathways", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Admin Pathways", navLabel: "Admin Pathways" },
  { path: "/learning/admin/pathways/new", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "New Pathway", navLabel: "New Pathway" },
  { path: "/learning/admin/pathways/[pathwayId]/edit", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Edit Pathway", navLabel: "Edit Pathway" },
  { path: "/learning/admin/assessments", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Admin Assessments", navLabel: "Admin Assessments" },
  { path: "/learning/admin/assessments/new", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "New Assessment", navLabel: "New Assessment" },
  { path: "/learning/admin/assessments/[assessmentId]/edit", zone: "professional", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN_OR_HIE", pageTitle: "Edit Assessment", navLabel: "Edit Assessment" },

  // â”€â”€ Zone: Nhume â€” Dispatch, Delivery, Fleet Tracking & Last-Mile Logistics â”€â”€
  // Nhume is the logistics nervous system of Impilo. Routes are mounted under
  // /nhume so that dispatchers, facility runners, programme managers, fleet
  // operators, autonomous-platform operators and citizens can all reach the
  // surfaces they are authorised for. Backend: services/nhume-service.
  { path: "/nhume", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Nhume Logistics", navLabel: "Nhume", navZone: "work" },
  { path: "/nhume/dashboard", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Nhume Operations Dashboard", navLabel: "Operations Dashboard", navZone: "work" },
  { path: "/nhume/deliveries", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Nhume Deliveries", navLabel: "Deliveries", navZone: "work" },
  { path: "/nhume/deliveries/new", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "New Delivery Request", navLabel: "New Delivery", navZone: "work" },
  { path: "/nhume/deliveries/[deliveryId]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Delivery Detail", navLabel: "Delivery", navZone: "work" },
  { path: "/nhume/dispatcher", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Nhume Dispatcher Console", navLabel: "Dispatcher Console", navZone: "work" },
  { path: "/nhume/map", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Fleet Tracking Map", navLabel: "Fleet Map", navZone: "work" },
  { path: "/nhume/courier", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Courier / Driver Console", navLabel: "Courier Console", navZone: "work" },
  { path: "/nhume/inbound", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Inbound & Handover", navLabel: "Inbound & Handover", navZone: "work" },
  { path: "/nhume/fleet", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Fleet & Asset Management", navLabel: "Fleet & Assets", navZone: "work" },
  { path: "/nhume/fleet/[assetId]", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Fleet Asset", navLabel: "Asset Detail", navZone: "work" },
  { path: "/nhume/couriers", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Drivers & Couriers", navLabel: "Drivers & Couriers", navZone: "work" },
  { path: "/nhume/couriers/[courierId]", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Courier Profile", navLabel: "Courier Profile", navZone: "work" },
  { path: "/nhume/policies", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Delivery Policies", navLabel: "Delivery Policies", navZone: "work" },
  { path: "/nhume/autonomous", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Autonomous Delivery", navLabel: "Autonomous", navZone: "work" },
  { path: "/nhume/analytics", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Nhume Analytics", navLabel: "Analytics", navZone: "work" },
  { path: "/nhume/custody/[deliveryId]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Chain of Custody", navLabel: "Chain of Custody", navZone: "work" },
  // Citizen-facing track page (privacy-safe, role-aware).
  { path: "/nhume/track/[deliveryId]", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Track Delivery", navLabel: "Track Delivery", navZone: "life" },

  // ── Zone: Madi — Blood Donation, Transfusion & Haemovigilance ──
  // Backend: services/madi-service at /internal/v1/madi
  { path: "/madi", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Madi Blood Services", navLabel: "Madi", navZone: "work" },
  { path: "/madi/donor", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Donor Hub", navLabel: "Blood Donation", navZone: "life" },
  { path: "/madi/donor/register", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Become a Donor", navLabel: "Register as Donor", navZone: "life" },
  { path: "/madi/donor/profile", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Donor Profile", navLabel: "Donor Profile", navZone: "life" },
  { path: "/madi/donor/screening", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Donor Screening", navLabel: "Screening", navZone: "life" },
  { path: "/madi/donor/drives", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Donation Drives Near Me", navLabel: "Nearby Drives", navZone: "life" },
  { path: "/madi/donor/history", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Donation History", navLabel: "Donation History", navZone: "life" },
  { path: "/madi/donor/feedback", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Donor Feedback", navLabel: "Donor Feedback", navZone: "life" },
  { path: "/madi/donor/preferences", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Donor Preferences", navLabel: "Donor Preferences", navZone: "life" },
  { path: "/madi/drives", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Donation Drives", navLabel: "Donation Drives", navZone: "work" },
  { path: "/madi/drives/new", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "New Donation Drive", navLabel: "New Drive", navZone: "work" },
  { path: "/madi/drives/[driveId]", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Drive Detail", navLabel: "Drive", navZone: "work" },
  { path: "/madi/blood-bank", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Local Blood Bank", navLabel: "Blood Bank", navZone: "work" },
  { path: "/madi/blood-bank/orders", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Blood Bank Orders", navLabel: "Bank Orders", navZone: "work" },
  { path: "/madi/blood-bank/stock", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Blood Stock", navLabel: "Blood Stock", navZone: "work" },
  { path: "/madi/blood-bank/crossmatch", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Crossmatch", navLabel: "Crossmatch", navZone: "work" },
  { path: "/madi/blood-bank/issue", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Issue Blood", navLabel: "Issue Blood", navZone: "work" },
  { path: "/madi/blood-bank/fridges", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Blood Fridge Monitoring", navLabel: "Fridge IoT", navZone: "work" },
  { path: "/madi/central-bank", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "Central Blood Bank", navLabel: "Central Bank", navZone: "work" },
  { path: "/madi/orders", zone: "queue", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Order Blood", navLabel: "Order Blood", navZone: "work" },
  { path: "/madi/orders/[orderId]", zone: "queue", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Blood Order Detail", navLabel: "Order Detail", navZone: "work" },
  { path: "/madi/transfusion", zone: "queue", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Record Transfusion", navLabel: "Transfusion", navZone: "work" },
  { path: "/madi/transfusion/[episodeId]", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Transfusion Episode", navLabel: "Transfusion Episode", navZone: "work" },
  { path: "/madi/haemovigilance", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Haemovigilance", navLabel: "Haemovigilance", navZone: "work" },
  { path: "/madi/haemovigilance/national", zone: "operations", layout: "app", sidebar: "main", guard: "role", requiredRole: "ADMIN", pageTitle: "National Haemovigilance", navLabel: "National Haemovigilance", navZone: "work" },
  { path: "/madi/dashboard", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Madi Dashboard", navLabel: "Madi Dashboard", navZone: "work" },
  { path: "/madi/processing", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Blood Processing", navLabel: "Processing", navZone: "work" },
  { path: "/madi/logistics", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Blood Logistics", navLabel: "Blood Logistics", navZone: "work" },

  // ── Zone: Impilo Live (services/live-service at /internal/v1/live) ──
  { path: "/live", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Impilo Live", navLabel: "Impilo Live", navZone: "work" },
  { path: "/live/manage", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Live Event Management", navLabel: "Manage Events", navZone: "work" },
  { path: "/live/admin", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Impilo Live Administration", navLabel: "Live Admin", navZone: "work" },
  { path: "/live/create", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Create Live Event", navLabel: "Create Event", navZone: "work" },
  { path: "/live/discover", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Discover Live Events", navLabel: "Live Health Talks", navZone: "life" },
  { path: "/live/saved", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Saved Live Events", navLabel: "Saved Events", navZone: "life" },
  { path: "/live/my-events", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Live Events", navLabel: "My Events", navZone: "life" },
  { path: "/live/replays", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Live Event Replays", navLabel: "Replays", navZone: "life" },
  { path: "/live/cpd", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Live CPD", navLabel: "Live CPD", navZone: "work" },
  { path: "/live/certificates", zone: "professional", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Live Certificates", navLabel: "Live Certificates", navZone: "work" },
  { path: "/live/event/[eventId]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Live Event Detail", navLabel: "Event", navZone: "work" },
  { path: "/live/event/[eventId]/room", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Live Room", navLabel: "Live Room", navZone: "work" },
  { path: "/live/event/[eventId]/replay", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Event Replay", navLabel: "Replay", navZone: "life" },
  { path: "/live/event/[eventId]/analytics", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Live Analytics", navLabel: "Analytics", navZone: "work" },

  // ── Phase-E2 Governance spine (platform-origin console + org onboarding) ──
  // Appended block (E2-GOV-UI). Role-gated via direct hasRole fallback in matchesRequiredRole.
  { path: "/platform-origin", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PLATFORM_ORIGIN_ADMINISTRATOR", pageTitle: "Platform Origin", navLabel: "Platform Origin", navZone: "professional" },
  { path: "/platform-origin/[id]", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "PLATFORM_ORIGIN_ADMINISTRATOR", pageTitle: "Country Operation", navLabel: "Country Operation", navZone: "professional" },
  { path: "/organization-admin/onboarding", zone: "admin", layout: "app", sidebar: "admin", guard: "role", requiredRole: "ORGANIZATION_ADMIN", pageTitle: "Organization Onboarding", navLabel: "Org Onboarding", navZone: "professional" },

  // Trust & Access Administration (Jun 2026): 90 routes under /work/** driven by Session Experience Contract.
  ...ADMINISTRATION_GOVERNANCE_ROUTES,

  // ── Zone: Rito — Quality, Safety & Client Voice ──
  { path: "/rito", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Rito — Quality & Safety", navLabel: "Rito", navZone: "professional" },
  { path: "/rito/cases", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Case Worklist", navLabel: "Cases", navZone: "professional" },
  { path: "/rito/cases/[caseId]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Case Detail", navLabel: "Case", navZone: "professional" },
  { path: "/rito/quality-signals", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Quality Signals", navLabel: "Signals", navZone: "professional" },
  { path: "/rito/audits", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Quality Audits", navLabel: "Audits", navZone: "professional" },
  { path: "/rito/audits/[auditId]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Audit Detail", navLabel: "Audit", navZone: "professional" },
  { path: "/rito/improvement", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Improvement — CAPA & QI", navLabel: "Improvement", navZone: "professional" },
  { path: "/rito/surveys", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Experience Surveys", navLabel: "Surveys", navZone: "professional" },
  { path: "/my-life/feedback", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "My Feedback", navLabel: "Feedback & Safety", navZone: "life" },
  { path: "/my-life/feedback/new", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Share Feedback", navLabel: "Share Feedback", navZone: "life" },
  { path: "/my-life/feedback/[caseId]", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Track Feedback", navLabel: "Track", navZone: "life" },
  { path: "/work/facility/rito", zone: "facility", layout: "app", sidebar: "facility", guard: "facility", pageTitle: "Facility Quality & Safety", navLabel: "Quality & Safety", navZone: "work" },
  { path: "/work/above-site/rito", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Above-Site Quality & Safety", navLabel: "Quality Oversight", navZone: "work" },
  { path: "/work/dura", zone: "operations", layout: "app", sidebar: "main", guard: "facility", pageTitle: "Dura — Stock & Supply", navLabel: "Dura Stock", navZone: "work" },
  { path: "/work/patient-safety", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Patient Safety — Pharmacovigilance", navLabel: "Patient Safety", navZone: "work" },
  { path: "/work/patient-safety/new", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "New Safety Report", navLabel: "New Report", navZone: "work" },
  { path: "/work/patient-safety/reports/[reportId]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Safety Report", navLabel: "Report", navZone: "work" },
  { path: "/work/patient-safety/cases/[caseId]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Safety Case", navLabel: "Case", navZone: "work" },
  { path: "/work/patient-safety/mcaz", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "MCAZ Workbench", navLabel: "MCAZ", navZone: "work" },
  // ── Zone: Daidzai — Emergency, Disaster & Public-Health Response Command (WS#7) ──
  // Citizen emergency surfaces (low-friction, life-safety) + provider command surfaces.
  { path: "/emergency", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Emergency", navLabel: "Emergency", navZone: "life" },
  { path: "/emergency/sos", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Send SOS", navLabel: "Send SOS", navZone: "life" },
  { path: "/emergency/services", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Nearest Services", navLabel: "Nearest Services", navZone: "life" },
  { path: "/emergency/track", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Track Emergency", navLabel: "Track", navZone: "life" },
  { path: "/emergency/track/[id]", zone: "home", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Track Emergency", navLabel: "Track", navZone: "life" },
  { path: "/work/daidzai", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Emergency Command", navLabel: "Emergency Command", navZone: "work" },
  { path: "/work/daidzai/dispatch", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Dispatch Console", navLabel: "Dispatch", navZone: "work" },
  { path: "/work/daidzai/missions", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Mission Tracking", navLabel: "Missions", navZone: "work" },
  { path: "/work/daidzai/disasters", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Disaster Command", navLabel: "Disasters", navZone: "work" },
  // Sorting desk — pre-triage visit-type sort (G12).
  { path: "/work/sorting-desk", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Sorting Desk", navLabel: "Sorting Desk", navZone: "work" },
  // WS#8 — Death & Post-Death Pathway (provider/facility surfaces).
  { path: "/work/clinical/death-cases", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Death Cases", navLabel: "Death Cases", navZone: "work" },
  { path: "/work/clinical/death-cases/[id]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Death Case", navLabel: "Death Case", navZone: "work" },
  { path: "/work/clinical/mortality-certification", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Mortality Certification", navLabel: "Mortality Certification", navZone: "work" },
  { path: "/work/mortuary", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Mortuary", navLabel: "Mortuary", navZone: "work" },
  { path: "/work/crvs/death-registration", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Death Registration", navLabel: "Death Registration", navZone: "work" },

  // WS#6 — Theatre & Perioperative Depth (provider theatre surfaces).
  { path: "/work/clinical/theatre", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Theatre", navLabel: "Theatre", navZone: "work" },
  { path: "/work/clinical/theatre/[id]", zone: "operations", layout: "app", sidebar: "main", guard: "auth", pageTitle: "Theatre Case", navLabel: "Theatre Case", navZone: "work" },
];

// Total route count assertion.
//
// Phase 1 Impilo Fundo registration added 2 routes (/learning, /learning/catalog).
// The previously-declared constant of 264 was already stale by 1 against the actual
// ROUTES array before this change (true pre-edit count was 265), so the new total
// is 267. Track this constant whenever ROUTES is extended.
//
// Stage 3.4C housekeeping (May 2026): added `/finance/costa` (Stage 3.2 / G-1)
// and `/finance/mushex-platform` (Stage 3.3 / G-2) to the canonical route
// registry so that breadcrumbs, the role-guard, and the taskbar/title resolver
// all see them. New total was 269.
// Phase 4 (May 2026): added `/finance/mushex-platform/wallets/[walletId]`
// (read-only custodial wallet detail drill-down). New total is 270.
// Phase 5 (May 2026): added `/finance/costa/encounter/[encounterId]`
// (read-only COSTA encounter timeline). New total is 271.
// Phase 6B (May 2026): added `/learning/courses/[courseId]`
// (native Impilo Fundo course-detail page over Phase 5B course structure
// endpoint). New total is 272.
// Phase 4 follow-on (May 2026): added three MusheX per-record detail pages
// (`/finance/mushex-platform/remittance/[transferId]`,
// `/finance/mushex-platform/cards/[cardId]`,
// `/finance/mushex-platform/reversals/[reversalId]`). New total is 275.
// Phase 6 (May 2026): added `/finance/remittances` as a canonical
// read-only remittance hub over the existing coverage remittance feed.
// New total is 276.
// Additional upstream route registrations on this branch increase the current
// canonical total to 326.
// Nhume (May 2026): 17 new routes under /nhume for the Dispatch, Delivery,
// Fleet Tracking and Last-Mile Logistics service (services/nhume-service).
// Combined with upstream additions on this branch the canonical total is 370.
// GAP-010 post-merge (2026-05-28): registered 21 shadow routes (social, wallet, communication hub,
// ubomi, communities/pages, extended auth) so guards and breadcrumbs apply.
// Madi (Jun 2026): 26 routes under /madi for blood donation, transfusion &
// haemovigilance (services/madi-service). New canonical total is 452.
// Impilo Live (Jun 2026): 14 routes under /live incl. /live/admin (services/live-service).
// Trust & Access Administration (Jun 2026): 90 contract-governed routes under /work/** (administration-governance scaffold).
// Vashandi (Jun 2026): 10 routes under /work/vashandi for operational workforce UI. New canonical total is 589.
// Person Health Wallet (Jun 2026): 8 routes under /citizen/wallet for the unified person anchor
// experience (overview, identity, profile, records, timeline, dependants, payments, comms).
// Realigned the count constant to the actual extracted route total (it had drifted behind earlier waves).
// Dura (Jun 2026): native sovereign stock brain — +1 net-new route (/dura) merged from the Dura workstream.
// IATG Phase-E2 UI (Jul 2026): +6 net-new routes — trust profile + provider-claim nav (E2-TRUST +2),
// facility claim (E2-FACILITY +1), platform-origin console x2 + org-onboarding (E2-GOV +3).
// IATG Trust Console (Jul 2026): +1 net-new route — /registry-admin/trust-console (unified governance queues).
// Activation letter (Jul 2026): +1 — /registry-admin/activation-letter (printable onboarding letter).
// Workforce Intake (Jul 2026): +1 net-new route — /admin/workforce-intake staged staff bootstrap wizard.
// Nhume inbound (Jul 2026): +1 net-new route — /nhume/inbound receiving & handover surface.
// HPA regulatory UX (Jul 2026): +2 net-new routes — /professional/pic-nominations (practitioner
// PIC-nomination ledger) and /verify/facility-certificate (public certificate verifier).
// Requirement sourcing (Jul 2026): +1 — /marketplace/establishment-guide (HPA requirement →
// marketplace supplier guide for prospective practice founders).
// Msika completion wave M7 (Jul 2026): +1 — /marketplace/orders/[id]/pay (real MusheX money leg).
// Same wave re-gated marketplace personas: seller*→MARKETPLACE_SELL, ops+substitutions→MARKETPLACE_OPS,
// vendor*→VENDOR_FULFILMENT, cart+orders/[id](+pay)→auth (citizens can buy); COMMERCE membership untouched.
// Gateway W2 (Jul 2026): +2 — /verify/practitioner (public health-professional register verifier)
// and /welcome/health-info (public citizen health-information pillar).
// Simba wellness integration (Jul 2026): merged the wellness-social + assessment/timeline/reminders
// route block from claude/simba-wellness-completion-7c1a2f (assessment, timeline, reminders,
// settings/consent, provider-workspace/wellness, and the /wellness/social/** surfaces). Total was 722.
// Simba wellness UI-completion (Jul 2026, Phase B1): registered the previously-unregistered wellness
// surfaces so all are guarded/nav-governed — citizen assessment/timeline/reminders/follow-ups/insights/
// consent + social feed/reels/posts/[id]/saved/my-activity + provider-workspace/wellness(/social).
// +13 routes. New total is 735.
// Money-stack UI closure (Jul 2026): +2 so far — /finance/bank-reconciliation (MU3) and
// /finance/failed-money-events (MU4). MU6 adds its finance route in the following commit.
export const EXPECTED_ROUTE_COUNT = 737;
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

/** Breadcrumb trail from route registry â€” skips unknown path prefixes (e.g. partial /ehr). */
export function buildBreadcrumbTrail(pathname: string): { href: string; label: string }[] {
  const trail: { href: string; label: string }[] = [{ href: "/home", label: "Home" }];
  const normalized = pathname === "/" || pathname === "" ? "/home" : pathname;
  const parts = normalized.split("/").filter(Boolean);
  if (parts.length === 0) return trail;

  for (let i = 1; i <= parts.length; i++) {
    const prefix = `/${parts.slice(0, i).join("/")}`;
    const def = matchRouteDefinition(prefix);
    if (!def) continue;
    const prev = trail[trail.length - 1];
    if (prev?.href === prefix) continue;
    if (trail.length === 1 && trail[0].href === "/home" && prefix === "/home") continue;
    trail.push({ href: prefix, label: def.navLabel });
  }
  return trail;
}
