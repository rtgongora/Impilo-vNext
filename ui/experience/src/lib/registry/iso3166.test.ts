import { describe, expect, it } from "vitest";
import { assertZimbabweRow, buildCountryPickerGroups, type IsoCountryRow } from "./iso3166";

/** Minimal rows so Vitest does not require the webpack @registry-templates alias. */
function sampleRows(): IsoCountryRow[] {
  return [
    { name: "Zimbabwe", alpha2: "ZW", alpha3: "ZWE", numeric: "716" },
    { name: "South Africa", alpha2: "ZA", alpha3: "ZAF", numeric: "710" },
    { name: "Botswana", alpha2: "BW", alpha3: "BWA", numeric: "072" },
    { name: "Mozambique", alpha2: "MZ", alpha3: "MOZ", numeric: "508" },
    { name: "Namibia", alpha2: "NA", alpha3: "NAM", numeric: "516" },
    { name: "Zambia", alpha2: "ZM", alpha3: "ZMB", numeric: "894" },
    { name: "Malawi", alpha2: "MW", alpha3: "MWI", numeric: "454" },
    { name: "Angola", alpha2: "AO", alpha3: "AGO", numeric: "024" },
  ];
}

describe("ISO 3166-1 picker grouping", () => {
  it("validates canonical Zimbabwe ZW / ZWE / 716", () => {
    const zw = sampleRows().find((c) => c.alpha2 === "ZW");
    expect(() => assertZimbabweRow(zw)).not.toThrow();
  });

  it("pins Zimbabwe and nearby SADC countries for ZW deployments", () => {
    const g = buildCountryPickerGroups(sampleRows(), "ZW");
    expect(g.pinned.map((c) => c.alpha2)).toEqual(["ZW"]);
    expect(g.nearby.map((c) => c.alpha2)).toContain("ZA");
    expect(g.nearby.map((c) => c.alpha2)).toContain("BW");
  });
});
