import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { getClinicalWorklist } from "./clinicalWorklistService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe("clinicalWorklistService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it("maps composed clinical worklist payload", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        data: {
          items: [
            {
              id: "task:1",
              kind: "TASK",
              source: "pct",
              title: "Review abnormal result",
              priority: "HIGH",
              status: "PENDING",
            },
          ],
          summary: {
            total: 1,
            urgent: 1,
            overdue: 0,
            by_source: { pct: 1 },
          },
        },
      },
    } as never);

    const out = await getClinicalWorklist("fac-1");
    expect(out.items).toHaveLength(1);
    expect(out.items[0].title).toBe("Review abnormal result");
    expect(out.summary.bySource).toEqual({ pct: 1 });
  });
});
