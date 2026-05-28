import { describe, expect, it } from "vitest";
import {
  normalizeIntegrationRouteRows,
  resolveTileIntegrationHealth,
  rowMatchesKeywords,
} from "@/features/production-command-centre/tile-integration-health";

describe("tile integration health", () => {
  it("normalizes array and object route payloads", () => {
    expect(normalizeIntegrationRouteRows([{ id: "a" }])).toHaveLength(1);
    expect(normalizeIntegrationRouteRows({ items: [{ id: "b" }] })).toHaveLength(1);
  });

  it("matches keywords in route rows", () => {
    expect(rowMatchesKeywords({ targetSystem: "DHIS2" }, ["dhis2"])).toBe(true);
    expect(rowMatchesKeywords({ name: "other" }, ["ndila"])).toBe(false);
  });

  it("resolves routed vs not in hub", () => {
    const routes = [{ system: "inpatient-service" }];
    expect(resolveTileIntegrationHealth(["inpatient"], routes, true, false)).toBe("routed");
    expect(resolveTileIntegrationHealth(["ndila"], routes, true, false)).toBe("not_in_hub");
    expect(resolveTileIntegrationHealth(undefined, routes, true, false)).toBe("not_applicable");
    expect(resolveTileIntegrationHealth(["inpatient"], routes, false, false)).toBe("hub_down");
  });
});
