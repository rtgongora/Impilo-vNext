/**
 * Production Command Centre tile registry — Health OS discoverability map.
 */

export type CommandCentreMaturity = "live" | "partial" | "not_wired" | "blocked";
export type CommandCentrePriority = "P0" | "P1" | "P2";

export interface CommandCentreTile {
  id: string;
  plane: string;
  title: string;
  description: string;
  href: string;
  demoAction?: string;
  maturity: CommandCentreMaturity;
  dataMode: "live" | "seed" | "partial" | "blocked" | "not_wired";
  priority: CommandCentrePriority;
  relatedWorkflows?: string[];
  knownGaps?: string;
  /** Keywords matched against integration-hub route rows for live health chips */
  integrationKeywords?: string[];
}

export interface CommandCentreSection {
  id: string;
  title: string;
  description: string;
  tiles: CommandCentreTile[];
}

export const COMMAND_CENTRE_SECTIONS: CommandCentreSection[] = [
  {
    id: "backbone",
    title: "Core backbone",
    description: "Registry, trust, and sovereign identity spine",
    tiles: [
      { id: "registries", plane: "Registry", title: "Core registries overview", description: "Vito, Varapi, Tuso, Indawo hub", href: "/registry", maturity: "partial", dataMode: "live", priority: "P0", demoAction: "Search client or provider" },
      { id: "vito", plane: "Registry", title: "Vito client registry", description: "MPI search, registration, Health ID", href: "/registry/clients", maturity: "partial", dataMode: "live", priority: "P0", relatedWorkflows: ["Clinical", "Rx", "Wellness"], integrationKeywords: ["vito", "identity", "client"] },
      { id: "varapi", plane: "Registry", title: "Varapi provider registry", description: "Practitioner directory and verification", href: "/registry/providers", maturity: "partial", dataMode: "live", priority: "P0", integrationKeywords: ["varapi", "provider"] },
      { id: "tuso", plane: "Registry", title: "Tuso facility registry", description: "Facilities, wards, services", href: "/registry/facilities", maturity: "partial", dataMode: "live", priority: "P0", integrationKeywords: ["tuso", "facility"] },
      { id: "indawo", plane: "Public Health", title: "Indawo site registry", description: "Public health premises and sites", href: "/public-health/site-registry", maturity: "partial", dataMode: "live", priority: "P0", integrationKeywords: ["indawo", "site"] },
      { id: "tshepo", plane: "Trust", title: "Tshepo trust layer", description: "Policies, consent, audit", href: "/registry/trust", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "butano", plane: "Clinical", title: "Butano shared health record", description: "Patient chart and clinical summaries", href: "/queue/search", maturity: "live", dataMode: "live", priority: "P0", demoAction: "Open patient chart" },
      { id: "zibo", plane: "Registry", title: "Zibo terminology", description: "Code systems and terminology browser", href: "/registry/terminology", maturity: "partial", dataMode: "live", priority: "P1" },
      { id: "ubomi", plane: "Registry", title: "Ubomi CRVS", description: "Birth/death civil registration interface", href: "/ubomi", maturity: "partial", dataMode: "partial", priority: "P0", knownGaps: "Live CRVS adapter may be partial" },
    ],
  },
  {
    id: "nompilo",
    title: "Intelligent experience",
    description: "Nompilo AI companion and orchestration",
    tiles: [
      { id: "nompilo-ask", plane: "Experience", title: "Nompilo assistant", description: "Global guidance and search", href: "/ask", maturity: "partial", dataMode: "live", priority: "P0", demoAction: "Ask Nompilo for a workflow" },
      { id: "core-tx", plane: "Experience", title: "Core transactions", description: "Composed transaction feed and journey", href: "/core-transaction", maturity: "partial", dataMode: "live", priority: "P0" },
    ],
  },
  {
    id: "clinical",
    title: "Clinical plane",
    description: "Outpatient, inpatient, emergency, and clinical operations",
    tiles: [
      { id: "clinical-hub", plane: "Clinical", title: "Clinical services overview", description: "Clinical care module launcher", href: "/clinical", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "outpatient", plane: "Clinical", title: "Outpatient / queue", description: "Queue, triage, encounters", href: "/queue", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "inpatient", plane: "Clinical", title: "Inpatient workspace", description: "Admissions, ward board, rounds, discharge", href: "/clinical/inpatient", maturity: "partial", dataMode: "live", priority: "P0", demoAction: "Open admission list", integrationKeywords: ["inpatient"] },
      { id: "emergency", plane: "Clinical", title: "ED / casualty", description: "Emergency activations", href: "/clinical/emergency", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "mental-health", plane: "Clinical", title: "Mental health", description: "Psychiatric emergency referral acceptance queue", href: "/work/mental-health", maturity: "partial", dataMode: "live", priority: "P1" },
      { id: "beds", plane: "Clinical", title: "Wards & beds", description: "Bed board and capacity", href: "/beds", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "control-tower", plane: "Clinical", title: "Clinical control tower", description: "Facility operations dashboard", href: "/clinical/control-tower", maturity: "partial", dataMode: "live", priority: "P1" },
      { id: "pharmacy", plane: "Clinical", title: "Rx / pharmacy", description: "Prescriptions and dispensing", href: "/pharmacy", maturity: "partial", dataMode: "live", priority: "P0", demoAction: "Open Rx transaction journey", relatedWorkflows: ["Rx journey"], integrationKeywords: ["pharmacy", "prescription"] },
      { id: "rx-journey", plane: "Clinical", title: "Rx transaction journey", description: "Rx → MusheX → Nhume stepper", href: "/pharmacy/transaction-journey", maturity: "partial", dataMode: "live", priority: "P0", demoAction: "Walk the 9-step path" },
      { id: "lab", plane: "Clinical", title: "Orders & results", description: "Lab worklist and results", href: "/lab", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "imaging", plane: "Clinical", title: "PACS / imaging", description: "Imaging studies and DICOM viewer", href: "/queue/search", maturity: "partial", dataMode: "partial", priority: "P0", knownGaps: "Open patient chart → Imaging tab" },
    ],
  },
  {
    id: "wellness",
    title: "Wellness plane",
    description: "Preventive care, communities, and citizen engagement",
    tiles: [
      { id: "wellness-hub", plane: "Wellness", title: "Wellness overview", description: "Goals, screening, communities", href: "/wellness", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "wellness-goals", plane: "Wellness", title: "Health goals", description: "Lifestyle and preventive goals", href: "/wellness/goals", maturity: "partial", dataMode: "live", priority: "P1" },
      { id: "wellness-clubs", plane: "Wellness", title: "Communities & clubs", description: "Groups and wellness communities", href: "/wellness/clubs", maturity: "partial", dataMode: "live", priority: "P1" },
    ],
  },
  {
    id: "transactions",
    title: "Transaction & service layer",
    description: "Learning, documents, marketplace, telemedicine, finance, maps, logistics",
    tiles: [
      { id: "fundo", plane: "Enterprise", title: "Fundo learning", description: "Courses, studio, assessments — LMS and learning service", href: "/learning", maturity: "partial", dataMode: "live", priority: "P0", integrationKeywords: ["learning", "fundo"] },
      { id: "documents", plane: "Integration", title: "Documents", description: "Document management workflows", href: "/home/documents", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "marketplace", plane: "Enterprise", title: "Marketplace / Msika", description: "Product and service discovery", href: "/marketplace", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "telemedicine", plane: "Clinical", title: "Telemedicine", description: "Scheduling and session records", href: "/telemedicine", maturity: "partial", dataMode: "live", priority: "P0", knownGaps: "RTC media blocked" },
      { id: "finance", plane: "Finance", title: "Finance rail", description: "Billing, claims, tariffs", href: "/finance", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "mushex", plane: "Finance", title: "MusheX payments", description: "Payment orchestration via finance BFF", href: "/finance/mushex-platform", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "ndila", plane: "Data", title: "Ndila maps", description: "Geospatial intelligence workspace", href: "/ndila", maturity: "partial", dataMode: "partial", priority: "P0", integrationKeywords: ["ndila", "geospatial"] },
      { id: "nhume", plane: "Enterprise", title: "Nhume dispatch", description: "Delivery and logistics operations", href: "/nhume", maturity: "partial", dataMode: "live", priority: "P0", integrationKeywords: ["nhume", "dispatch"] },
      { id: "public-health", plane: "Public Health", title: "Public health", description: "Surveillance, campaigns, reporting", href: "/public-health", maturity: "partial", dataMode: "live", priority: "P0", integrationKeywords: ["public-health", "surveillance"] },
    ],
  },
  {
    id: "enterprise",
    title: "Enterprise resource plane",
    description: "HR, inventory, fleet, procurement, and facility operations",
    tiles: [
      { id: "enterprise-hub", plane: "Enterprise", title: "Enterprise overview", description: "Resource and operations dashboard", href: "/enterprise", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "staffing", plane: "Enterprise", title: "Staffing & rosters", description: "Shifts and workforce scheduling", href: "/organization-admin/staffing", maturity: "partial", dataMode: "live", priority: "P1" },
      { id: "inventory", plane: "Enterprise", title: "Inventory & stock", description: "Stock levels and movements", href: "/inventory", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "erp", plane: "Enterprise", title: "ERP / procurement", description: "GL, HR, procurement workflows", href: "/erp", maturity: "partial", dataMode: "live", priority: "P1" },
      { id: "operations", plane: "Enterprise", title: "Operations hub", description: "Workflows and dispatch operator feeds", href: "/operations", maturity: "partial", dataMode: "live", priority: "P0" },
    ],
  },
  {
    id: "data-intelligence",
    title: "Data & intelligence plane",
    description: "Data quality, pipelines, reporting, analytics, audit, governance",
    tiles: [
      { id: "data-intel-hub", plane: "Data", title: "Data & intelligence overview", description: "Unified data and intelligence workspace", href: "/data-intelligence", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "data-quality", plane: "Data", title: "Data quality", description: "Completeness and validation views", href: "/data-intelligence/quality", maturity: "partial", dataMode: "partial", priority: "P0" },
      { id: "integration", plane: "Data", title: "Integration monitor", description: "Adapter and sync status", href: "/data-intelligence/integration", maturity: "partial", dataMode: "live", priority: "P0", integrationKeywords: ["integration", "hub"] },
      { id: "reports", plane: "Data", title: "Reporting", description: "Standard and facility reports", href: "/reports", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "intelligence", plane: "Data", title: "Health intelligence", description: "Fused search and copilot presets", href: "/intelligence", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "audit", plane: "Trust", title: "Audit intelligence", description: "Transaction and access audit trails", href: "/data-intelligence/audit", maturity: "partial", dataMode: "live", priority: "P0" },
      { id: "governance", plane: "Trust", title: "Data governance", description: "Access and governance policies", href: "/admin/data-governance", maturity: "partial", dataMode: "live", priority: "P1" },
      { id: "platform-monitor", plane: "Platform", title: "Platform journey monitor", description: "Core transaction operator telemetry", href: "/platform-journey", maturity: "partial", dataMode: "live", priority: "P1" },
    ],
  },
];

export function filterCommandCentreSections(query: string, priority?: CommandCentrePriority): CommandCentreSection[] {
  const q = query.trim().toLowerCase();
  return COMMAND_CENTRE_SECTIONS.map((section) => ({
    ...section,
    tiles: section.tiles.filter((tile) => {
      if (priority && tile.priority !== priority) return false;
      if (!q) return true;
      return (
        tile.title.toLowerCase().includes(q) ||
        tile.description.toLowerCase().includes(q) ||
        tile.plane.toLowerCase().includes(q)
      );
    }),
  })).filter((section) => section.tiles.length > 0);
}
