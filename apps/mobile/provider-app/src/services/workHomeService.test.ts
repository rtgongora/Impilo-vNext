import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { getWorkHome, getWorkHomeSection } from "./workHomeService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

function envelope(attributes: unknown) {
  return { data: { data: { id: "a1", type: "work-home", attributes } }, status: 200, correlationId: "c1", headers: {} };
}

describe("workHomeService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it("requests the same composition web /work reads, with context_id", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope({ contextId: "ctx-1", family: "FACILITY_CLINICAL", mode: "CLINICAL_CARE", sections: [] }) as never
    );
    const result = await getWorkHome("ctx-1");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/work-home?context_id=ctx-1");
    expect(result.family).toBe("FACILITY_CLINICAL");
  });

  it("includes mode when supplied", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope({ sections: [] }) as never);
    await getWorkHome("ctx-1", "FACILITY_MANAGEMENT");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/work-home?context_id=ctx-1&mode=FACILITY_MANAGEMENT");
  });

  it("preserves a DEGRADED section rather than dropping it", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope({
        contextId: "ctx-1",
        family: "FACILITY_CLINICAL",
        mode: "CLINICAL_CARE",
        sections: [
          { sectionId: "clinical-worklist", title: "Worklist", status: "DEGRADED", items: [], note: "PCT timed out" },
        ],
      }) as never
    );
    const result = await getWorkHome("ctx-1");
    expect(result.sections[0].status).toBe("DEGRADED");
    expect(result.sections[0].note).toBe("PCT timed out");
  });

  it("retries a single section against the differential endpoint", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope({ sectionId: "clinical-worklist", title: "Worklist", status: "OK", items: [] }) as never
    );
    const section = await getWorkHomeSection("clinical-worklist", "ctx-1", "CLINICAL_CARE");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/work-home/sections/clinical-worklist?context_id=ctx-1&mode=CLINICAL_CARE"
    );
    expect(section.status).toBe("OK");
  });
});
