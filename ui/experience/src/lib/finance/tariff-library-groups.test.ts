import { describe, expect, it } from "vitest";
import { classifyTariffList, groupTariffLists } from "shared-ui";
import type { CostaTariffListRow } from "shared-ui";

function row(partial: Partial<CostaTariffListRow>): CostaTariffListRow {
  return {
    id: 1,
    externalCode: "X",
    name: "Test",
    tariffFamily: "other",
    tariffType: "t",
    priceBasis: "p",
    officialStatus: "active",
    validationStatus: "validated",
    referenceOnly: false,
    approvedForBilling: true,
    currency: "USD",
    ...partial,
  } as CostaTariffListRow;
}

describe("tariff library grouping", () => {
  it("classifies Zimbabwe PoC as default demo", () => {
    expect(
      classifyTariffList(
        row({
          externalCode: "ZW-PLACEHOLDER-POC-V0.1",
          tariffFamily: "zimbabwe_placeholder",
        }),
      ).section,
    ).toBe("default_demo");
  });

  it("classifies WHO lists as who_reference", () => {
    expect(classifyTariffList(row({ tariffFamily: "who_reference", referenceOnly: true })).section).toBe(
      "who_reference",
    );
  });

  it("puts retired effective_to in retired section", () => {
    const past = new Date("2020-01-01");
    expect(
      classifyTariffList(
        row({
          officialStatus: "retired",
          effectiveTo: "2019-06-01",
          referenceOnly: true,
          approvedForBilling: false,
        }),
        past,
      ).section,
    ).toBe("retired");
  });

  it("groups upload_channel under uploaded_operational", () => {
    const lists = [
      row({
        id: 10,
        externalCode: "UPL-COUNTRY-DEMO",
        name: "Country",
        tariffFamily: "operational_upload",
        metadata: { upload_channel: "country" },
      }),
    ];
    const g = groupTariffLists(lists);
    expect(g.get("uploaded_operational")?.length).toBe(1);
  });
});
