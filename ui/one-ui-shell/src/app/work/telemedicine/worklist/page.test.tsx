import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SpecialistWorklistPage from "./page";

const { acceptMutate, declineMutate, workbench } = vi.hoisted(() => ({
  acceptMutate: vi.fn(),
  declineMutate: vi.fn(),
  workbench: { data: { data: [] as unknown[] } },
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (sel: (s: { facility: { id: string; name: string } }) => unknown) =>
    sel({ facility: { id: "facility-1", name: "Parirenyatwa" } }),
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineSpecialtyWorkbench: () => ({ data: workbench.data, isLoading: false }),
  useAcceptTeleconsultSession: () => ({ mutate: acceptMutate, isPending: false }),
  useDeclineTeleconsultSession: () => ({ mutate: declineMutate, isPending: false }),
}));

describe("SpecialistWorklistPage", () => {
  beforeEach(() => {
    acceptMutate.mockReset();
    declineMutate.mockReset();
    workbench.data = {
      data: [
        { id: "tc-1", specialty: "DERMATOLOGY", urgency: "URGENT", reason: "rash", referred_by_name: "Nurse Zhou" },
      ],
    };
  });

  it("accepts a referral through the governed accept with facility context", async () => {
    const user = userEvent.setup();
    render(<SpecialistWorklistPage />);

    expect(await screen.findByText("DERMATOLOGY")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() =>
      expect(acceptMutate).toHaveBeenCalledWith(
        expect.objectContaining({ id: "tc-1", receivingFacilityId: "facility-1" }),
      ),
    );
  });

  it("requires a reason before a decline can be submitted", async () => {
    const user = userEvent.setup();
    render(<SpecialistWorklistPage />);

    await user.click(await screen.findByRole("button", { name: "Decline" }));
    // Submit is disabled until a reason is typed.
    expect(screen.getByRole("button", { name: "Submit decline" })).toBeDisabled();
    await user.type(screen.getByPlaceholderText(/reason for declining/i), "Refer to rheumatology");
    await user.click(screen.getByRole("button", { name: "Submit decline" }));

    await waitFor(() =>
      expect(declineMutate).toHaveBeenCalledWith(
        expect.objectContaining({ id: "tc-1", reason: "Refer to rheumatology" }),
        expect.anything(),
      ),
    );
  });
});
