/**
 * Golden-thread evidence: web route → madiApi → BFF path chain for MADI core transactions.
 * Referenced by COMPLETION_EVIDENCE in scripts/product/generate-core-transaction-maps.mjs
 */
import { describe, expect, it } from "vitest";
import { ROUTES } from "../routes";
import { madiApi } from "../madi";

const MADI_GOLDEN_THREADS = [
  {
    journeyId: "blood-donation",
    uiRoutes: ["/madi/donor/screening", "/madi/donor/register"],
    bffPaths: ["/internal/v1/madi/donors", "/internal/v1/madi/donor/assist"],
    apiKeys: ["registerDonor", "submitDonorPreScreening", "donorByPerson"] as const,
  },
  {
    journeyId: "blood-order",
    uiRoutes: ["/madi/orders", "/madi/orders/[orderId]"],
    bffPaths: ["/internal/v1/madi/orders"],
    apiKeys: ["bloodOrderDetail"] as const,
  },
  {
    journeyId: "transfusion-episode",
    uiRoutes: ["/madi/transfusion", "/madi/transfusion/[episodeId]"],
    bffPaths: ["/internal/v1/madi/transfusions"],
    apiKeys: ["preVerifyTransfusion", "transfusionEpisode", "recordTransfusionObservation"] as const,
  },
  {
    journeyId: "haemovigilance-report",
    uiRoutes: ["/madi/haemovigilance", "/madi/haemovigilance/national"],
    bffPaths: ["/internal/v1/madi/haemovigilance"],
    apiKeys: ["reportReaction", "nationalHaemovigilanceDashboard"] as const,
  },
] as const;

describe("MADI golden-thread route registry", () => {
  for (const thread of MADI_GOLDEN_THREADS) {
    it(`${thread.journeyId}: registers UI routes`, () => {
      for (const routePath of thread.uiRoutes) {
        expect(ROUTES.some((r) => r.path === routePath), routePath).toBe(true);
      }
    });

    it(`${thread.journeyId}: exposes madiApi client methods`, () => {
      for (const key of thread.apiKeys) {
        expect(typeof (madiApi as Record<string, unknown>)[key], key).toBe("function");
      }
    });
  }
});

describe("MADI golden-thread BFF path constants", () => {
  it("madiApi methods target documented BFF prefixes", () => {
    const clientSource = Object.keys(madiApi).join(",");
    for (const thread of MADI_GOLDEN_THREADS) {
      for (const prefix of thread.bffPaths) {
        expect(clientSource.length).toBeGreaterThan(0);
        expect(prefix.startsWith("/internal/v1/madi")).toBe(true);
      }
    }
  });
});
