import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { EmergencyBundlePanel } from "./EmergencyBundlePanel";

const mutate = vi.fn();
vi.mock("./useEmergencyBundle", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./useEmergencyBundle")>();
  return {
    ...actual,
    useEmergencyBundleAssess: () => ({
      mutate, isPending: false, isError: false, error: null,
      data: { data: { status: "ACTIVE", steps: [{ code: "PPH_MASSAGE", name: "Uterine massage", status: "OVERDUE", mandatory: true, action: "Massage now." }],
        outstanding_mandatory: ["PPH_MASSAGE"], may_close: false, note: "1 mandatory step(s) still outstanding." } },
    }),
  };
});

describe("EmergencyBundlePanel", () => {
  beforeEach(() => mutate.mockReset());

  it("renders checklist and assess control", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <EmergencyBundlePanel kind="pph" title="PPH" controlLabel="Bleeding controlled" testIdPrefix="pph-protocol" />
      </QueryClientProvider>,
    );
    expect(await screen.findByTestId("pph-protocol-checklist")).toBeInTheDocument();
    expect(screen.getByTestId("pph-protocol-assess")).toBeInTheDocument();
  });

  it("calls assess on submit", async () => {
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={new QueryClient()}>
        <EmergencyBundlePanel kind="pph" title="PPH" controlLabel="Bleeding controlled" testIdPrefix="pph-protocol" />
      </QueryClientProvider>,
    );
    await user.click(screen.getByTestId("pph-protocol-assess"));
    await waitFor(() => expect(mutate).toHaveBeenCalled());
  });
});
