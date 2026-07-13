import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ImagingReportPanel } from "./ImagingReportPanel";

const { authorMutate, releaseMutate, versions } = vi.hoisted(() => ({
  authorMutate: vi.fn(),
  releaseMutate: vi.fn(),
  versions: { data: [] as unknown[] },
}));

vi.mock("@/hooks/queries/useDiagnosticsOrders", () => ({
  useOrderReportVersions: () => ({ data: { data: versions.data }, isLoading: false }),
  useAuthorReport: () => ({ mutate: authorMutate, isPending: false, isError: false, isSuccess: false }),
  useReleaseReport: () => ({ mutate: releaseMutate, isPending: false, variables: undefined }),
}));

describe("ImagingReportPanel", () => {
  beforeEach(() => {
    authorMutate.mockReset();
    releaseMutate.mockReset();
    versions.data = [];
  });

  it("authors a report against the viewer's orderId", async () => {
    render(<ImagingReportPanel orderId="ORD-9" />);
    fireEvent.change(screen.getByLabelText("Impression"), { target: { value: "No acute finding" } });
    fireEvent.click(screen.getByRole("button", { name: /Save report/ }));
    await waitFor(() => expect(authorMutate).toHaveBeenCalledTimes(1));
    expect(authorMutate.mock.calls[0][0]).toMatchObject({ orderId: "ORD-9", action: "preliminary", impression: "No acute finding" });
  });

  it("releases an un-released report version", async () => {
    versions.data = [{ resultId: "res-1", version: 1, reportStatus: "FINAL", releasedAt: null }];
    render(<ImagingReportPanel orderId="ORD-9" />);
    fireEvent.click(screen.getByRole("button", { name: /^Release$/ }));
    expect(releaseMutate).toHaveBeenCalledWith(expect.objectContaining({ resultId: "res-1", orderId: "ORD-9" }));
  });
});
