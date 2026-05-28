import { apiClient } from "@impilo/mobile-api-client";

export interface DemoJourney {
  id: string;
  number: number;
  title: string;
  description: string;
  web_route: string;
  mobile_tab: string;
  bff_probe: string;
  maturity: "live" | "partial" | "not_wired" | "blocked";
}

/** Static fallback — mirrors BFF `wave20/demo-journey-map.json`. */
export const DEMO_JOURNEYS: DemoJourney[] = [
  {
    id: "clinical-rx",
    number: 1,
    title: "Core clinical / Rx",
    description: "Queue → encounter → Rx → MusheX → core transaction audit",
    web_route: "/pharmacy/transaction-journey?patientId=CPID-ZW-00001",
    mobile_tab: "core_transaction",
    bff_probe: "/internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001",
    maturity: "partial",
  },
  {
    id: "inpatient",
    number: 2,
    title: "Inpatient",
    description: "Admissions, ward list, nursing workbench, discharge handoff",
    web_route: "/clinical/inpatient/admissions",
    mobile_tab: "inpatient",
    bff_probe: "/internal/v1/inpatient/admissions",
    maturity: "partial",
  },
  {
    id: "wellness",
    number: 3,
    title: "Wellness",
    description: "Wellness profile, challenges, and community clubs",
    web_route: "/wellness",
    mobile_tab: "learning",
    bff_probe: "/internal/v1/wellness/challenges",
    maturity: "partial",
  },
  {
    id: "enterprise",
    number: 4,
    title: "Enterprise resources",
    description: "Inventory requisitions, marketplace, and facility ops",
    web_route: "/enterprise",
    mobile_tab: "facility",
    bff_probe: "/internal/v1/inventory/requisitions?facility_id=a1b2c3d4-0001-4000-8000-000000000001",
    maturity: "partial",
  },
  {
    id: "tele-dispatch",
    number: 5,
    title: "Telemedicine → dispatch",
    description: "Telehealth session → Rx journey → Nhume / Ndila ops",
    web_route: "/telemedicine",
    mobile_tab: "telemedicine",
    bff_probe: "/internal/v1/mobile/provider/telemedicine/sessions?patient_id=CPID-ZW-00001",
    maturity: "partial",
  },
  {
    id: "public-health",
    number: 6,
    title: "Public health + geo",
    description: "Field tasks, surveillance, and Ndila tile config",
    web_route: "/public-health",
    mobile_tab: "ph_field_tasks",
    bff_probe: "/internal/v1/ndila/tiles/config",
    maturity: "partial",
  },
  {
    id: "data-intel",
    number: 7,
    title: "Data & intelligence",
    description: "Integration hub routes, pipelines, audit intelligence",
    web_route: "/data-intelligence/pipelines",
    mobile_tab: "ops_reports",
    bff_probe: "/internal/v1/integration-hub/routes",
    maturity: "partial",
  },
];

function normalizeJourneys(value: unknown): DemoJourney[] {
  if (!Array.isArray(value)) return DEMO_JOURNEYS;
  return value
    .filter((row): row is Record<string, unknown> => typeof row === "object" && row !== null)
    .map((row) => ({
      id: String(row.id ?? ""),
      number: Number(row.number ?? 0),
      title: String(row.title ?? "Journey"),
      description: String(row.description ?? ""),
      web_route: String(row.web_route ?? ""),
      mobile_tab: String(row.mobile_tab ?? ""),
      bff_probe: String(row.bff_probe ?? ""),
      maturity: (row.maturity as DemoJourney["maturity"]) ?? "partial",
    }))
    .filter((row) => row.id.length > 0);
}

export async function fetchDemoJourneys(): Promise<DemoJourney[]> {
  try {
    const response = await apiClient.get<{ data?: unknown }>("/internal/v1/demo-journeys");
    const rows = normalizeJourneys(response.data?.data);
    return rows.length > 0 ? rows : DEMO_JOURNEYS;
  } catch {
    return DEMO_JOURNEYS;
  }
}
