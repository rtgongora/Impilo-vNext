import { beforeEach, describe, expect, it, vi } from "vitest";

const setSupervisorEntryTab = vi.fn();

vi.mock("../../stores/appStore", () => ({
  appStore: {
    getState: () => ({
      setSupervisorEntryTab,
    }),
  },
  useAppStore: vi.fn(),
}));

import { buildProviderCommsKpis } from "../../lib/providerCommsKpis";

describe("ProviderDashboard Comms KPI actions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("opens provider messaging for thread and sent KPI taps", () => {
    const setProviderTab = vi.fn();
    const setMode = vi.fn();

    const kpis = buildProviderCommsKpis(
      { active_threads: 9, sent_today: 22, failed_today: 0 },
      setProviderTab,
      setMode
    );

    kpis.find((k) => k.label === "Active Threads")?.onPress();
    kpis.find((k) => k.label === "Sent Today")?.onPress();

    expect(setProviderTab).toHaveBeenCalledTimes(2);
    expect(setProviderTab).toHaveBeenNthCalledWith(1, "messaging");
    expect(setProviderTab).toHaveBeenNthCalledWith(2, "messaging");
    expect(setMode).not.toHaveBeenCalled();
  });

  it("routes failed KPI tap to supervisor escalations", () => {
    const setProviderTab = vi.fn();
    const setMode = vi.fn();

    const kpis = buildProviderCommsKpis(
      { active_threads: 3, sent_today: 11, failed_today: 2 },
      setProviderTab,
      setMode
    );

    kpis.find((k) => k.label === "Failed Today")?.onPress();

    expect(setSupervisorEntryTab).toHaveBeenCalledWith("escalations");
    expect(setMode).toHaveBeenCalledWith("supervisor");
    expect(setProviderTab).not.toHaveBeenCalled();
  });
});
