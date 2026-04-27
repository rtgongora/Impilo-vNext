/**
 * COSTA tariff library grouping for Experience / One UI Shell.
 * Aligns with costing-engine seed (V007+) and {@code /api/costa/tariff-lists}.
 */

export const REFERENCE_TARIFF_WARNING =
  "This is a reference library. It may be used for viewing, mapping, comparison, and demonstration, but it is not an approved operational billing tariff.";

export const ZIMBABWE_POC_DISCLAIMER =
  "Zimbabwe Placeholder Health Services Tariff — Proof of Concept v0.1: illustrative pricing for demos and automated tests only; not an official national tariff.";

export type CostaTariffListRow = {
  id: number;
  externalCode: string;
  name: string;
  description?: string | null;
  tariffFamily: string;
  tariffType: string;
  priceBasis: string;
  officialStatus: string;
  validationStatus: string;
  referenceOnly: boolean;
  approvedForBilling: boolean;
  currency: string;
  effectiveFrom?: string;
  effectiveTo?: string | null;
  metadata?: string | Record<string, unknown> | null;
  tenantId?: string | null;
  libraryId?: number;
};

export type TariffListSectionKey =
  | "default_demo"
  | "who_reference"
  | "international_reference"
  | "uploaded_operational"
  | "retired"
  | "other";

export function parseTariffMetadata(raw: string | Record<string, unknown> | null | undefined): Record<string, unknown> {
  if (raw == null) return {};
  if (typeof raw === "object") return raw as Record<string, unknown>;
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function isRetiredRow(list: CostaTariffListRow, meta: Record<string, unknown>, now: Date): boolean {
  if (String(list.officialStatus).toLowerCase() === "retired") return true;
  if (meta.retired === true) return true;
  if (list.effectiveTo) {
    const t = new Date(list.effectiveTo);
    if (!Number.isNaN(t.getTime()) && t < now) return true;
  }
  return false;
}

/**
 * Classifies a tariff list for grouped UI (library selector).
 */
export function classifyTariffList(list: CostaTariffListRow, now = new Date()): {
  section: TariffListSectionKey;
  uploadChannel?: string;
} {
  const meta = parseTariffMetadata(list.metadata);
  if (isRetiredRow(list, meta, now)) {
    return { section: "retired" };
  }
  if (
    list.externalCode === "ZW-PLACEHOLDER-POC-V0.1" ||
    meta.default_for_demo === true ||
    list.tariffFamily === "zimbabwe_placeholder"
  ) {
    return { section: "default_demo" };
  }
  if (list.tariffFamily === "who_reference") {
    return { section: "who_reference" };
  }
  if (list.tariffFamily === "international_reference") {
    return { section: "international_reference" };
  }
  const ch = meta.upload_channel;
  if (typeof ch === "string" && ch.length > 0) {
    return { section: "uploaded_operational", uploadChannel: ch };
  }
  if (list.tariffFamily === "operational_upload" || list.tenantId) {
    return { section: "uploaded_operational", uploadChannel: (meta.provenance as string) || "tenant" };
  }
  return { section: "other" };
}

const SECTION_ORDER: { key: TariffListSectionKey; title: string; description: string }[] = [
  {
    key: "default_demo",
    title: "Default / demo operational tariff",
    description: "Use for end-to-end demos. Clearly labelled as proof-of-concept; not an official government tariff.",
  },
  {
    key: "who_reference",
    title: "WHO references",
    description: "Economic and classification references — not patient billing price lists unless imported and approved.",
  },
  {
    key: "international_reference",
    title: "International reference tariffs",
    description: "Examples (NHS, MBS, CMS-style references) — reference-only in this build unless approved for operations.",
  },
  {
    key: "uploaded_operational",
    title: "Uploaded / operational-style lists",
    description: "Country, provider, payer, donor, programme, or organisation-scoped lists (incl. tenant-sourced and upload pipeline).",
  },
  {
    key: "retired",
    title: "Retired / historical",
    description: "Superseded schedules kept for audit and trend comparison.",
  },
  {
    key: "other",
    title: "Other",
    description: "Additional lists (see support if misclassified).",
  },
];

export function sectionCatalog(): Readonly<typeof SECTION_ORDER> {
  return SECTION_ORDER;
}

export function groupTariffLists(
  lists: CostaTariffListRow[],
  now = new Date(),
): Map<TariffListSectionKey, { list: CostaTariffListRow; uploadChannel?: string }[]> {
  const map = new Map<TariffListSectionKey, { list: CostaTariffListRow; uploadChannel?: string }[]>();
  for (const key of SECTION_ORDER) {
    map.set(key.key, []);
  }
  for (const list of lists) {
    const c = classifyTariffList(list, now);
    const bucket = map.get(c.section) ?? map.get("other")!;
    bucket.push({ list, uploadChannel: c.uploadChannel });
  }
  return map;
}
