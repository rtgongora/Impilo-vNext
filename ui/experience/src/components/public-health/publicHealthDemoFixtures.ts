/**
 * Demonstration rows for Public Health UI (Lovable / impilo-structure parity).
 * Not persisted — replace with BFF responses when inspections, complaints, and weekly IDSR APIs exist.
 */

export type DemoInspection = {
  id: string;
  site: string;
  siteType: string;
  type: string;
  inspector: string;
  date: string;
  status: "completed" | "scheduled" | "in_progress";
  score: number | null;
  findings: number;
  critical: number;
  followUp: string | null;
};

export const DEMO_INSPECTIONS: DemoInspection[] = [
  {
    id: "INS-001",
    site: "Mbare Musika Market",
    siteType: "Market",
    type: "Routine Food Safety",
    inspector: "J. Moyo",
    date: "2026-04-08",
    status: "completed",
    score: 72,
    findings: 3,
    critical: 0,
    followUp: "2026-05-08",
  },
  {
    id: "INS-002",
    site: "Eastlea Swimming Pool",
    siteType: "Recreation",
    type: "Water Quality",
    inspector: "T. Ndlovu",
    date: "2026-04-09",
    status: "scheduled",
    score: null,
    findings: 0,
    critical: 0,
    followUp: null,
  },
  {
    id: "INS-003",
    site: "Southerton Abattoir",
    siteType: "Abattoir",
    type: "Meat Safety",
    inspector: "P. Chirwa",
    date: "2026-04-07",
    status: "completed",
    score: 45,
    findings: 7,
    critical: 2,
    followUp: "2026-04-14",
  },
  {
    id: "INS-004",
    site: "Corner Bakery — Avondale",
    siteType: "Food Premises",
    type: "Food Premises",
    inspector: "S. Makoni",
    date: "2026-04-10",
    status: "in_progress",
    score: null,
    findings: 1,
    critical: 0,
    followUp: null,
  },
  {
    id: "INS-005",
    site: "Glen Norah Borehole #3",
    siteType: "Water Point",
    type: "Water Source",
    inspector: "R. Dube",
    date: "2026-04-06",
    status: "completed",
    score: 88,
    findings: 1,
    critical: 0,
    followUp: null,
  },
];

export type DemoEnforcement = {
  id: string;
  site: string;
  violation: string;
  severity: string;
  action: string;
  issued: string;
  deadline: string;
  status: string;
};

export const DEMO_ENFORCEMENT: DemoEnforcement[] = [
  {
    id: "ENF-001",
    site: "Southerton Abattoir",
    violation: "Unsanitary slaughter conditions",
    severity: "critical",
    action: "Closure Notice",
    issued: "2026-04-07",
    deadline: "2026-04-14",
    status: "pending_compliance",
  },
  {
    id: "ENF-002",
    site: "Happy Foods Takeaway",
    violation: "Expired food storage",
    severity: "high",
    action: "Improvement Notice",
    issued: "2026-03-28",
    deadline: "2026-04-11",
    status: "complied",
  },
];

export type DemoComplaint = {
  id: string;
  type: string;
  category: string;
  location: string;
  status: string;
  reported: string;
  reporter: string;
  priority: string;
  assignedTo: string;
  daysOpen: number;
};

export const DEMO_COMPLAINTS: DemoComplaint[] = [
  {
    id: "CMP-001",
    type: "Noise nuisance",
    category: "Environmental",
    location: "Borrowdale",
    status: "investigating",
    reported: "2026-04-08",
    reporter: "Citizen",
    priority: "medium",
    assignedTo: "T. Moyo",
    daysOpen: 2,
  },
  {
    id: "CMP-002",
    type: "Illegal dumping",
    category: "Waste",
    location: "Highfield",
    status: "enforcement_pending",
    reported: "2026-04-05",
    reporter: "Ward councillor",
    priority: "high",
    assignedTo: "J. Moyo",
    daysOpen: 5,
  },
  {
    id: "CMP-003",
    type: "Expired food products",
    category: "Food safety",
    location: "CBD shop",
    status: "resolved",
    reported: "2026-04-01",
    reporter: "Anonymous",
    priority: "high",
    assignedTo: "S. Makoni",
    daysOpen: 0,
  },
  {
    id: "CMP-004",
    type: "Water contamination",
    category: "Water",
    location: "Glen Norah borehole",
    status: "investigating",
    reported: "2026-04-09",
    reporter: "Community leader",
    priority: "critical",
    assignedTo: "R. Dube",
    daysOpen: 1,
  },
  {
    id: "CMP-005",
    type: "Sewage overflow",
    category: "Sanitation",
    location: "Chitungwiza Unit L",
    status: "investigating",
    reported: "2026-04-07",
    reporter: "Citizen",
    priority: "critical",
    assignedTo: "T. Ndlovu",
    daysOpen: 3,
  },
];

export type DemoWeeklyFacility = {
  facility: string;
  week: string;
  submitted: boolean;
  onTime: boolean;
  diseases: number;
  zero: number;
  positive: number;
};

/** Illustrative weekly IDSR facility row shape (eIDSR aggregate reporting). */
export const DEMO_WEEKLY_IDSR_FACILITIES: DemoWeeklyFacility[] = [
  { facility: "Parirenyatwa Hospital", week: "W14-2026", submitted: true, onTime: true, diseases: 12, zero: 8, positive: 4 },
  { facility: "Harare Central Hospital", week: "W14-2026", submitted: true, onTime: false, diseases: 12, zero: 10, positive: 2 },
  { facility: "Chitungwiza Central", week: "W14-2026", submitted: false, onTime: false, diseases: 12, zero: 0, positive: 0 },
  { facility: "Mpilo Hospital", week: "W14-2026", submitted: true, onTime: true, diseases: 12, zero: 11, positive: 1 },
  { facility: "Sally Mugabe Hospital", week: "W14-2026", submitted: true, onTime: true, diseases: 12, zero: 9, positive: 3 },
];

export const DEMO_COMPLIANCE_BY_CATEGORY = [
  { category: "Food premises", rate: 78, target: 85 },
  { category: "Water points", rate: 91, target: 90 },
  { category: "Schools", rate: 64, target: 80 },
  { category: "Markets", rate: 71, target: 75 },
  { category: "Recreation (pools)", rate: 88, target: 85 },
];
