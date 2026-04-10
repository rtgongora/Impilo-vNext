import { describe, expect, it } from "vitest";
import type { PublicHealthCampaign } from "@/hooks/queries/usePublicHealth";
import {
  countActivePublicHealthCampaigns,
  sumCampaignsReachedPopulation,
  weightedCampaignCoveragePercent,
} from "./publicHealthCampaignKpis";

function camp(partial: Partial<PublicHealthCampaign> & Pick<PublicHealthCampaign, "id" | "name">): PublicHealthCampaign {
  return {
    status: "planning",
    campaignType: "General",
    jurisdiction: "National",
    targetPopulation: 0,
    reachedPopulation: 0,
    startDate: "",
    endDate: "",
    ...partial,
  };
}

describe("countActivePublicHealthCampaigns", () => {
  it("counts non-terminal campaigns", () => {
    const list = [
      camp({ id: "1", name: "A", status: "active" }),
      camp({ id: "2", name: "B", status: "completed" }),
      camp({ id: "3", name: "C", status: "planning" }),
    ];
    expect(countActivePublicHealthCampaigns(list)).toBe(2);
  });
});

describe("sumCampaignsReachedPopulation", () => {
  it("sums reached values", () => {
    const list = [
      camp({ id: "1", name: "A", reachedPopulation: 100 }),
      camp({ id: "2", name: "B", reachedPopulation: 50 }),
    ];
    expect(sumCampaignsReachedPopulation(list)).toBe(150);
  });
});

describe("weightedCampaignCoveragePercent", () => {
  it("returns null when no positive targets", () => {
    expect(weightedCampaignCoveragePercent([camp({ id: "1", name: "A", targetPopulation: 0 })])).toBe(null);
  });

  it("computes weighted percent", () => {
    const list = [
      camp({ id: "1", name: "A", targetPopulation: 100, reachedPopulation: 50 }),
      camp({ id: "2", name: "B", targetPopulation: 100, reachedPopulation: 50 }),
    ];
    expect(weightedCampaignCoveragePercent(list)).toBe(50);
  });

  it("caps at 100", () => {
    const list = [camp({ id: "1", name: "A", targetPopulation: 100, reachedPopulation: 200 })];
    expect(weightedCampaignCoveragePercent(list)).toBe(100);
  });
});
