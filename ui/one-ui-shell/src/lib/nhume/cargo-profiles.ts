/**
 * Cargo profiles — the heart of Nhume as a *movement* layer, not an ambulance
 * tool. Nhume moves what the health system needs moved: people, samples,
 * commodities, teams, blood, vaccines, equipment and documents. Each profile
 * pins the right delivery type, sane handling defaults, and the type-specific
 * fields that would otherwise be missing if every mission were forced through
 * one generic (patient-shaped) form.
 *
 * Everything maps onto the EXISTING CreateDeliveryRequest contract (typed
 * flags + clinical_context_ref + metadata), so no backend/schema change is
 * needed. The delivery detail surface reads `metadata.links` back to render
 * honest, deep-linked integration references (Dura / OROS / MADI / PCT).
 */

export type CargoFieldType = "text" | "number" | "select" | "checkbox";

export interface CargoField {
  key: string;
  label: string;
  type: CargoFieldType;
  options?: string[];
  placeholder?: string;
  help?: string;
}

export interface CargoProfile {
  id: string;
  label: string;
  description: string;
  icon: string; // lucide icon name
  deliveryType: string;
  /** True when the mission concerns a person (enables patient linkage fields). */
  patientLinked: boolean;
  /** Handling defaults applied the moment the profile is chosen. */
  defaults: {
    coldChain?: boolean;
    chainOfCustody?: boolean;
    controlled?: boolean;
    fragile?: boolean;
    identityVerification?: string;
  };
  fields: CargoField[];
}

export const CARGO_PROFILES: CargoProfile[] = [
  {
    id: "PATIENT",
    label: "Patient / client",
    description: "Emergency or planned patient movement and facility transfer",
    icon: "Ambulance",
    deliveryType: "FACILITY_TRANSFER",
    patientLinked: true,
    defaults: { identityVerification: "OTP" },
    fields: [
      { key: "patientRef", label: "Patient (Health ID / CPID)", type: "text", placeholder: "known patient reference", help: "Leave blank if the patient is unidentified." },
      { key: "chiefComplaint", label: "Chief complaint / reason", type: "text", placeholder: "e.g. obstructed labour" },
      { key: "receivingService", label: "Receiving service", type: "text", placeholder: "e.g. Maternity, Theatre" },
      { key: "pctReferralRef", label: "PCT referral ref (link)", type: "text", placeholder: "referral id" },
      { key: "stretcherRequired", label: "Stretcher required", type: "checkbox" },
      { key: "oxygenRequired", label: "Oxygen required", type: "checkbox" },
    ],
  },
  {
    id: "SPECIMEN",
    label: "Lab specimen / sample",
    description: "Sample pickup and transport to a laboratory",
    icon: "TestTube",
    deliveryType: "LAB_SAMPLE_PICKUP",
    patientLinked: false,
    defaults: { chainOfCustody: true },
    fields: [
      { key: "specimenType", label: "Specimen type", type: "select", options: ["BLOOD", "SPUTUM", "URINE", "STOOL", "CSF", "TISSUE", "SWAB", "OTHER"] },
      { key: "numberOfSpecimens", label: "Number of specimens", type: "number", placeholder: "1" },
      { key: "biohazard", label: "Biohazard handling", type: "checkbox" },
      { key: "coldChain", label: "Temperature controlled", type: "checkbox" },
      { key: "orosOrderRef", label: "Lab order ref (OROS link)", type: "text", placeholder: "order id" },
    ],
  },
  {
    id: "BLOOD",
    label: "Blood / blood product",
    description: "Blood bank dispatch to a receiving facility",
    icon: "Droplet",
    deliveryType: "BLOOD_PRODUCT",
    patientLinked: false,
    defaults: { coldChain: true, chainOfCustody: true, identityVerification: "OTP" },
    fields: [
      { key: "bloodGroup", label: "Blood group", type: "select", options: ["O_NEG", "O_POS", "A_NEG", "A_POS", "B_NEG", "B_POS", "AB_NEG", "AB_POS"] },
      { key: "productType", label: "Product", type: "select", options: ["WHOLE_BLOOD", "PACKED_CELLS", "PLASMA", "PLATELETS", "CRYOPRECIPITATE"] },
      { key: "units", label: "Units", type: "number", placeholder: "1" },
      { key: "madiOrderRef", label: "Blood order ref (MADI link)", type: "text", placeholder: "madi order id" },
    ],
  },
  {
    id: "VACCINE",
    label: "Vaccine / cold-chain",
    description: "Cold-chain vaccine movement to an outreach site or facility",
    icon: "Snowflake",
    deliveryType: "VACCINE",
    patientLinked: false,
    defaults: { coldChain: true },
    fields: [
      { key: "vaccineType", label: "Vaccine", type: "text", placeholder: "e.g. Measles-Rubella" },
      { key: "batchNumber", label: "Batch number", type: "text" },
      { key: "doses", label: "Doses", type: "number", placeholder: "0" },
      { key: "temperatureRange", label: "Temperature range", type: "select", options: ["2C_TO_8C", "MINUS_15C_TO_MINUS_25C", "MINUS_60C_TO_MINUS_80C"] },
      { key: "campaignRef", label: "Campaign ref", type: "text", placeholder: "programme / campaign id" },
    ],
  },
  {
    id: "MEDICINE",
    label: "Medicine",
    description: "Pharmacy stock or prescription movement",
    icon: "Pill",
    deliveryType: "MEDICINE",
    patientLinked: false,
    defaults: {},
    fields: [
      { key: "controlled", label: "Controlled medicine", type: "checkbox" },
      { key: "coldChain", label: "Cold-chain", type: "checkbox" },
      { key: "duraRequisitionRef", label: "Stock requisition ref (Dura link)", type: "text", placeholder: "requisition id" },
    ],
  },
  {
    id: "COMMODITY",
    label: "Commodity / supplies",
    description: "Programme commodity or emergency resupply",
    icon: "Package",
    deliveryType: "PROGRAMME_COMMODITY",
    patientLinked: false,
    defaults: {},
    fields: [
      { key: "commodityCategory", label: "Category", type: "text", placeholder: "e.g. ARVs, oxytocin, PPE" },
      { key: "stockoutRisk", label: "Stockout risk", type: "checkbox" },
      { key: "coldChain", label: "Cold-chain", type: "checkbox" },
      { key: "duraRequisitionRef", label: "Stock requisition ref (Dura link)", type: "text", placeholder: "requisition id" },
    ],
  },
  {
    id: "EQUIPMENT",
    label: "Equipment / device",
    description: "Medical equipment or assistive device movement",
    icon: "Wrench",
    deliveryType: "EQUIPMENT",
    patientLinked: false,
    defaults: { fragile: true },
    fields: [
      { key: "equipmentType", label: "Equipment", type: "text", placeholder: "e.g. oxygen concentrator" },
      { key: "assetId", label: "Asset ID", type: "text" },
      { key: "requiresTechnician", label: "Requires technician", type: "checkbox" },
      { key: "returnRequired", label: "Return trip required", type: "checkbox" },
    ],
  },
  {
    id: "TEAM",
    label: "Outreach / mobile team",
    description: "Move an outreach or mobile-clinic team to a site",
    icon: "Users",
    deliveryType: "COMMUNITY_HEALTH_WORKER",
    patientLinked: false,
    defaults: {},
    fields: [
      { key: "teamName", label: "Team", type: "text", placeholder: "e.g. Mbare outreach team" },
      { key: "headcount", label: "Headcount", type: "number", placeholder: "0" },
      { key: "sessionRef", label: "Session / programme ref", type: "text" },
    ],
  },
  {
    id: "DOCUMENT",
    label: "Documents / records",
    description: "Physical documents or records where still required",
    icon: "FileText",
    deliveryType: "DOCUMENT_DELIVERY",
    patientLinked: false,
    defaults: {},
    fields: [
      { key: "documentKind", label: "Document kind", type: "text", placeholder: "e.g. signed consent, referral pack" },
      { key: "confidential", label: "Confidential", type: "checkbox" },
    ],
  },
  {
    id: "GENERAL",
    label: "General movement",
    description: "Any other operational movement",
    icon: "Boxes",
    deliveryType: "GENERAL",
    patientLinked: false,
    defaults: {},
    fields: [
      { key: "cargoDescription", label: "What is moving?", type: "text" },
    ],
  },
];

export function getCargoProfile(id: string): CargoProfile | undefined {
  return CARGO_PROFILES.find((p) => p.id === id);
}

/** Keys that carry an integration reference we surface as a deep link on detail. */
const LINK_KEYS: Record<string, string> = {
  duraRequisitionRef: "duraRequisitionRef",
  orosOrderRef: "orosOrderRef",
  madiOrderRef: "madiOrderRef",
  pctReferralRef: "pctReferralRef",
  campaignRef: "campaignRef",
};

/** The primary clinical link per profile (populates clinical_context_ref). */
const PRIMARY_CLINICAL_KEY: Record<string, string> = {
  SPECIMEN: "orosOrderRef",
  BLOOD: "madiOrderRef",
  PATIENT: "pctReferralRef",
};

/** Only the flags whose JSON names align exactly with the backend DTO. */
export interface CargoFlags {
  cold_chain_required?: boolean;
  chain_of_custody_required?: boolean;
  controlled_item?: boolean;
  fragile?: boolean;
  specimen?: boolean;
  biohazard?: boolean;
  return_required?: boolean;
}

export interface CargoContribution {
  deliveryType: string;
  flags: CargoFlags;
  clinicalContextRef?: string;
  metadata: Record<string, unknown>;
  itemKind: string;
}

/**
 * Map a chosen cargo profile + the operator's field values onto a partial
 * CreateDeliveryRequest contribution. Pure — unit tested.
 *
 * Everything that lacks an exactly-aligned typed field goes into `metadata`
 * (a passthrough Map on the backend), so this is immune to frontend/backend
 * field-name drift and never silently drops captured data.
 */
export function applyCargoProfile(
  profileId: string,
  values: Record<string, string | number | boolean | undefined>,
): CargoContribution {
  const profile = getCargoProfile(profileId) ?? getCargoProfile("GENERAL")!;
  const flags: CargoFlags = {};
  const links: Record<string, string> = {};
  const cargo: Record<string, unknown> = {};

  if (profile.defaults.coldChain) flags.cold_chain_required = true;
  if (profile.defaults.chainOfCustody) flags.chain_of_custody_required = true;
  if (profile.defaults.controlled) flags.controlled_item = true;
  if (profile.defaults.fragile) flags.fragile = true;
  if (profile.defaults.identityVerification) cargo.identityVerification = profile.defaults.identityVerification;
  if (profileId === "SPECIMEN") flags.specimen = true;

  for (const field of profile.fields) {
    const v = values[field.key];
    if (v === undefined || v === "" || v === false) continue;
    // Toggle-style keys that are really handling flags.
    if (field.key === "coldChain") flags.cold_chain_required = Boolean(v);
    else if (field.key === "controlled") flags.controlled_item = Boolean(v);
    else if (field.key === "biohazard") { flags.biohazard = Boolean(v); flags.chain_of_custody_required = true; }
    else if (field.key === "returnRequired") flags.return_required = Boolean(v);
    else if (LINK_KEYS[field.key]) links[LINK_KEYS[field.key]] = String(v);
    else cargo[field.key] = v;
  }

  const primaryKey = PRIMARY_CLINICAL_KEY[profileId];
  const clinicalContextRef = primaryKey && values[primaryKey] ? String(values[primaryKey]) : undefined;

  const metadata: Record<string, unknown> = { cargoProfile: profileId };
  if (Object.keys(cargo).length) metadata.cargo = cargo;
  if (Object.keys(links).length) metadata.links = links;

  return {
    deliveryType: profile.deliveryType,
    flags,
    clinicalContextRef,
    metadata,
    itemKind: profileId,
  };
}

/**
 * Read drop-off write-back outcomes recorded by nhume-service under
 * metadata.links_writeback ({SYSTEM: {status, detail, at}}). Failure is a
 * first-class state the dispatcher must see.
 */
export function readWriteBackOutcomes(
  metadata: unknown,
): Record<string, { status: string; detail: string; at?: string }> {
  const block = (metadata as { links_writeback?: Record<string, unknown> })?.links_writeback;
  if (!block || typeof block !== "object") return {};
  const out: Record<string, { status: string; detail: string; at?: string }> = {};
  for (const [system, raw] of Object.entries(block)) {
    if (!raw || typeof raw !== "object") continue;
    const o = raw as { status?: unknown; detail?: unknown; at?: unknown };
    out[system] = {
      status: String(o.status ?? "UNKNOWN"),
      detail: String(o.detail ?? ""),
      at: o.at ? String(o.at) : undefined,
    };
  }
  return out;
}

/** Read integration links back off a persisted delivery's metadata (detail view). */
export function readDeliveryLinks(metadata: unknown): Array<{ system: string; label: string; ref: string; href: string }> {
  const out: Array<{ system: string; label: string; ref: string; href: string }> = [];
  const links = (metadata as { links?: Record<string, string> })?.links;
  if (!links || typeof links !== "object") return out;
  const add = (system: string, label: string, ref: string | undefined, href: (r: string) => string) => {
    if (ref) out.push({ system, label, ref, href: href(ref) });
  };
  add("DURA", "Stock requisition", links.duraRequisitionRef, (r) => `/work/dura?requisition=${encodeURIComponent(r)}`);
  add("OROS", "Lab order", links.orosOrderRef, (r) => `/diagnostics/orders?order=${encodeURIComponent(r)}`);
  add("MADI", "Blood order", links.madiOrderRef, (r) => `/madi?order=${encodeURIComponent(r)}`);
  add("PCT", "Referral", links.pctReferralRef, (r) => `/referrals?ref=${encodeURIComponent(r)}`);
  add("DAIDZAI", "Emergency incident", links.daidzaiIncidentRef, (r) => `/work/daidzai?incident=${encodeURIComponent(r)}`);
  return out;
}
