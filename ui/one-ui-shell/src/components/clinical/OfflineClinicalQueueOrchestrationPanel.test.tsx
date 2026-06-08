import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OfflineClinicalQueueOrchestrationPanel } from "./OfflineClinicalQueueOrchestrationPanel";

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string } }) => unknown) =>
    selector({ facility: { id: "fac-9" } }),
}));

vi.mock("@/hooks/queries/useOfflineClinicalQueue", () => ({
  useOfflineClinicalQueue: () => ({
    data: { queue_depth: 4, source: "tshepo-offline-pack" },
    isLoading: false,
  }),
  usePendingOfflineReconcile: () => ({
    data: [{ id: "batch-1" }],
    isLoading: false,
  }),
  useSubmitOfflineReconcile: () => ({ mutate: vi.fn(), isPending: false }),
}));

describe("OfflineClinicalQueueOrchestrationPanel", () => {
  it("shows offline queue depth from clinical-tools BFF", () => {
    render(<OfflineClinicalQueueOrchestrationPanel />);
    expect(screen.getByTestId("offline-clinical-queue-orchestration-panel")).toBeInTheDocument();
    expect(screen.getByTestId("offline-queue-kpi-strip")).toHaveTextContent("4 queued item(s)");
  });
});
