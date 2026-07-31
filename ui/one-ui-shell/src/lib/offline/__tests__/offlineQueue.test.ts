import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  NOT_TRIAGEABLE_OFFLINE,
  isTriagePath,
  isQueueableOfflineWrite,
  methodToOpType,
  offlineTriageRefusal,
  resetOfflineManifestCacheForTests,
} from "@/lib/offline/offlineQueue";
import { stalenessFromTransport } from "@/lib/offline/staleness";

describe("W16b offline queue policy", () => {
  beforeEach(() => {
    resetOfflineManifestCacheForTests();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            features: {
              emergency: {
                enrolled: true,
                apiQueuePrefixes: ["/internal/v1/emergency", "/internal/v1/ed"],
                neverQueueApiSuffixes: ["/triage", "/triage/score"],
              },
            },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
  });

  it("never treats triage paths as queueable", async () => {
    expect(isTriagePath("/internal/v1/ed/visits/v1/triage")).toBe(true);
    expect(isTriagePath("/internal/v1/ed/triage/score")).toBe(true);
    expect(await isQueueableOfflineWrite("POST", "/internal/v1/ed/visits/v1/triage")).toBe(false);
  });

  it("queues enrolled non-triage emergency writes", async () => {
    expect(await isQueueableOfflineWrite("POST", "/internal/v1/emergency/pre-arrivals")).toBe(true);
    expect(methodToOpType("POST")).toBe("CREATE");
    expect(methodToOpType("PUT")).toBe("UPDATE");
  });

  it("Tier-B refusal carries NOT_TRIAGEABLE_OFFLINE and never a numeric acuity", () => {
    const refusal = offlineTriageRefusal();
    expect(refusal.status).toBe(422);
    expect(refusal.error.code).toBe(NOT_TRIAGEABLE_OFFLINE);
    expect(refusal.error.message).toContain("Reconnect");
    expect(refusal.error.message).not.toMatch(/\b[1-5]\b/);
    expect(JSON.stringify(refusal)).not.toMatch(/"acuity"\s*:/);
  });

  it("maps offline cache transport to CACHED_OFFLINE staleness", () => {
    expect(
      stalenessFromTransport({
        asOf: new Date().toISOString(),
        thresholdSeconds: 60,
        fromOfflineCache: true,
      }),
    ).toBe("CACHED_OFFLINE");
  });
});
