import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OfflineClinicalQueueOrchestrationPanel } from "./OfflineClinicalQueueOrchestrationPanel";

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string } }) => unknown) =>
    selector({ facility: { id: "fac-9" } }),
}));

const queue = vi.fn(() => ({
  data: { queue_depth: 4, source: "tshepo-offline-pack" } as { queue_depth: number; source: string } | undefined,
  isLoading: false,
  isError: false,
}));
const pending = vi.fn(() => ({
  data: [{ id: "batch-1" }] as { id: string }[] | undefined,
  isLoading: false,
  isError: false,
}));

vi.mock("@/hooks/queries/useOfflineClinicalQueue", () => ({
  useOfflineClinicalQueue: () => queue(),
  usePendingOfflineReconcile: () => pending(),
  useSubmitOfflineReconcile: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

describe("OfflineClinicalQueueOrchestrationPanel", () => {
  it("shows offline queue depth from clinical-tools BFF", () => {
    render(<OfflineClinicalQueueOrchestrationPanel />);
    expect(screen.getByTestId("offline-clinical-queue-orchestration-panel")).toBeInTheDocument();
    expect(screen.getByTestId("offline-queue-kpi-strip")).toHaveTextContent("4 queued item(s)");
  });

  /**
   * experience-bff empty-200 honesty sweep — UI half. "0 queued item(s) · 0 pending reconcile
   * batch(es)" is how a clinician decides that nothing captured offline is still waiting to reach
   * the record. It must not be produced by an outage.
   */
  it("reports an unreadable queue as unknown, not zero", () => {
    queue.mockReturnValueOnce({ data: undefined, isLoading: false, isError: true });
    pending.mockReturnValueOnce({ data: undefined, isLoading: false, isError: true });

    render(<OfflineClinicalQueueOrchestrationPanel />);

    const strip = screen.getByTestId("offline-queue-kpi-strip");
    expect(strip).toHaveTextContent(/unknown, not zero/i);
    expect(strip).not.toHaveTextContent("0 queued item(s)");
  });
});
