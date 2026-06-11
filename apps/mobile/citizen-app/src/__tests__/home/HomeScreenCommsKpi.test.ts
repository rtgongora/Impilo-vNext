import { describe, expect, it, vi } from "vitest";
import { buildCitizenCommsKpis } from "../../lib/homeCommsKpis";

describe("HomeScreen Comms KPI actions", () => {
  it("routes threads and sent KPIs to messaging tab", () => {
    const setActiveTab = vi.fn();
    const kpis = buildCitizenCommsKpis(
      { active_threads: 8, sent_today: 14, failed_today: 1 },
      setActiveTab
    );

    kpis.find((k) => k.id === "threads")?.onPress();
    kpis.find((k) => k.id === "sent")?.onPress();

    expect(setActiveTab).toHaveBeenNthCalledWith(1, "messaging");
    expect(setActiveTab).toHaveBeenNthCalledWith(2, "messaging");
  });

  it("routes failed KPI to public health tab", () => {
    const setActiveTab = vi.fn();
    const kpis = buildCitizenCommsKpis(
      { active_threads: 1, sent_today: 2, failed_today: 3 },
      setActiveTab
    );

    kpis.find((k) => k.id === "failed")?.onPress();

    expect(setActiveTab).toHaveBeenCalledWith("public_health");
  });
});
