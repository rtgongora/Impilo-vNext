import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EncounterDischargePanel } from "./EncounterDischargePanel";

const { mutateAsync } = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
}));

vi.mock("@/hooks/queries/useDischarge", () => ({
  useDischargeEncounter: () => ({
    mutateAsync,
    isPending: false,
  }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "provider-1" } }),
}));

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

describe("EncounterDischargePanel", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({ data: { status: "INITIATED" } });
  });

  it("requires discharge diagnosis before submit", async () => {
    render(<EncounterDischargePanel patientId="patient-1" encounterId="enc-1" />);
    fireEvent.click(screen.getByRole("button", { name: /capture discharge/i }));
    expect(await screen.findByText("Discharge diagnosis is required.")).toBeInTheDocument();
    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it("posts discharge instruction fields to BFF hook", async () => {
    const user = userEvent.setup();
    render(<EncounterDischargePanel patientId="patient-1" encounterId="enc-1" />);

    await user.type(screen.getByLabelText(/discharge diagnosis/i), "Acute bronchitis");
    await user.type(screen.getByLabelText(/follow-up instructions/i), "Review in 7 days");
    await user.type(screen.getByLabelText(/patient instructions/i), "Rest and fluids");
    fireEvent.click(screen.getByRole("button", { name: /capture discharge/i }));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          encounterId: "enc-1",
          discharge_diagnosis: "Acute bronchitis",
          follow_up_instructions: "Review in 7 days",
          patient_instructions: "Rest and fluids",
        }),
      ),
    );
  });
});
