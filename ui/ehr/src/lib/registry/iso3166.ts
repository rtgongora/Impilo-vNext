import countriesDoc from "@registry-templates/terminology/countries.iso3166-1.json";

export type IsoCountryRow = (typeof countriesDoc)["countries"][number];

const NEARBY = ["BW", "MZ", "NA", "ZA", "ZM", "MW"] as const;

export type DeployRegion = "ZW" | "INTL";

export function assertZimbabweRow(zw: IsoCountryRow | undefined): asserts zw is IsoCountryRow {
  if (!zw || zw.alpha2 !== "ZW" || zw.alpha3 !== "ZWE" || zw.numeric !== "716") {
    throw new Error("ISO country dataset missing canonical Zimbabwe row ZW / ZWE / 716");
  }
}

export function buildCountryPickerGroups(
  rows: IsoCountryRow[],
  deploy: DeployRegion,
): { pinned: IsoCountryRow[]; nearby: IsoCountryRow[]; rest: IsoCountryRow[] } {
  const byAlpha2 = new Map(rows.map((r) => [r.alpha2, r]));
  const zwRow = byAlpha2.get("ZW");
  assertZimbabweRow(zwRow);
  const nearbyRows = NEARBY.map((c) => byAlpha2.get(c)).filter(Boolean) as IsoCountryRow[];
  const used = new Set<string>(["ZW", ...NEARBY]);
  const rest = rows
    .filter((r) => !used.has(r.alpha2))
    .sort((a, b) => a.name.localeCompare(b.name, "en"));
  if (deploy === "ZW") {
    return { pinned: [zwRow], nearby: nearbyRows, rest };
  }
  return { pinned: [], nearby: [], rest: [...rows].sort((a, b) => a.name.localeCompare(b.name, "en")) };
}

export function defaultDeployRegion(): DeployRegion {
  const v = process.env.NEXT_PUBLIC_DEPLOY_REGION?.toUpperCase();
  return v === "INTL" ? "INTL" : "ZW";
}
