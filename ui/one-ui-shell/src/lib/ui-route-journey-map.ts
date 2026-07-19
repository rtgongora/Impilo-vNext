export type JourneyGroup =
  | "PERSON"
  | "PROVIDER"
  | "PLATFORM"
  | "CROSS_CUTTING"
  | "DOMAIN_SPECIFIC";

export interface JourneyRouteGroup {
  key: string;
  journey: JourneyGroup;
  label: string;
  routes: string[];
}

export interface RoleNavigationItem {
  label: string;
  route: string;
  aliases?: string[];
}

export const ROUTE_GROUPS: JourneyRouteGroup[] = [
  {
    key: "person",
    journey: "PERSON",
    label: "Person Journey",
    routes: [
      "/citizen",
      "/client-journey",
      "/discover",
      "/kiosk",
      "/marketplace",
      "/scheduling",
      "/telemedicine",
      "/wallet",
      "/wellness",
      "/share/claim",
      "/consent",
      "/privacy",
    ],
  },
  {
    key: "provider",
    journey: "PROVIDER",
    label: "Provider Journey",
    routes: [
      "/provider-workspace",
      "/provider/activate",
      "/clinical",
      "/clinical/inpatient",
      "/clinical-tools",
      "/ehr",
      "/queue",
      "/lab",
      "/pharmacy",
      "/shift",
      "/workspace",
      "/learning",
      "/guidance",
    ],
  },
  {
    key: "platform",
    journey: "PLATFORM",
    label: "Platform Journey",
    routes: [
      "/platform-journey",
      "/operations",
      "/monitoring",
      "/reports",
      "/intelligence",
      "/data-intelligence",
      "/production-command-centre",
      "/finance",
      "/enterprise",
      "/erp",
      "/coverage",
      "/registry-admin",
      "/organization-admin",
      "/public-health",
      "/omnichannel",
      "/ai-governance",
      "/developer",
      "/admin",
    ],
  },
  {
    key: "cross-cutting",
    journey: "CROSS_CUTTING",
    label: "Cross-cutting",
    routes: ["/ask", "/search", "/support", "/core-transaction", "/settings", "/auth", "/home", "/shell", "/ndila", "/nhume"],
  },
];

export const CLIENT_NAVIGATION: RoleNavigationItem[] = [
  { label: "Home", route: "/home" },
  { label: "Find Care", route: "/discover", aliases: ["/client-journey"] },
  { label: "My Visit", route: "/client-journey" },
  { label: "Book a service", route: "/home/bookings/new" },
  { label: "My Bookings", route: "/home/bookings" },
  { label: "Appointments", route: "/home/appointments", aliases: ["/scheduling"] },
  { label: "Results", route: "/monitoring/readings" },
  { label: "Medicines", route: "/home/medications", aliases: ["/pharmacy/prescriptions"] },
  { label: "Payments", route: "/wallet" },
  { label: "Wellness", route: "/wellness" },
  { label: "Feedback", route: "/support" },
  { label: "Ask Nompilo", route: "/ask" },
];

export const PROVIDER_NAVIGATION: RoleNavigationItem[] = [
  { label: "Start Duty", route: "/provider/activate", aliases: ["/shift"] },
  { label: "My Work", route: "/provider-workspace", aliases: ["/workspace"] },
  { label: "Queues", route: "/queue" },
  { label: "Patient Context", route: "/ehr" },
  { label: "Encounters", route: "/clinical" },
  { label: "Orders", route: "/lab/worklist", aliases: ["/lab", "/pharmacy"] },
  { label: "Results", route: "/lab/results" },
  { label: "Fundo", route: "/learning" },
  { label: "Support", route: "/support" },
  { label: "Ask Nompilo", route: "/ask" },
];

export const MANAGER_NAVIGATION: RoleNavigationItem[] = [
  { label: "Command Centre", route: "/production-command-centre", aliases: ["/health-os/command-centre", "/operations", "/platform-journey"] },
  { label: "Transactions", route: "/core-transaction" },
  { label: "Facilities", route: "/registry/facilities" },
  { label: "Services", route: "/registry/products" },
  { label: "Providers", route: "/registry/providers" },
  { label: "Workforce", route: "/work/vashandi", aliases: ["/work/vashandi/assignments", "/work/vashandi/rosters"] },
  { label: "Queues", route: "/operations/facility-operations" },
  { label: "Payments", route: "/finance/payments" },
  { label: "Claims", route: "/finance/claims" },
  { label: "Reports", route: "/reports" },
  { label: "Audit", route: "/admin/audit" },
  { label: "Sync", route: "/operations/butano" },
  { label: "Support", route: "/support" },
  { label: "Ask Nompilo", route: "/ask" },
];

export function classifyRouteJourney(pathname: string): JourneyGroup {
  for (const group of ROUTE_GROUPS) {
    const match = group.routes.some(
      (route) => pathname === route || pathname.startsWith(`${route}/`) || (route === "/ehr" && pathname.startsWith("/ehr/")),
    );
    if (match) {
      return group.journey;
    }
  }
  return "DOMAIN_SPECIFIC";
}

