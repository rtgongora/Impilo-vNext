import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SpecialistWorklistPage from "./page";

const { acceptMutate, declineMutate, workbench, poolQueue, directory } = vi.hoisted(() => ({
  acceptMutate: vi.fn(),
  declineMutate: vi.fn(),
  workbench: { data: { data: [] as unknown[] } },
  poolQueue: { data: { data: [] as unknown[] }, isLoading: false, isError: false, refetch: vi.fn() },
  directory: { data: [] as unknown[], isLoading: false },
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

vi.mock("@/hooks/queries/useVirtualCare", () => ({
  useTeleconsultPoolQueue: () => poolQueue,
}));

vi.mock("@/hooks/queries/useTelemedicineOperatingModel", () => ({
  useVirtualHospitalDirectory: () => directory,
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
    directory.isLoading = false;
    directory.data = [
      {
        id: "vh-national-telemedicine",
        name: "National Telemedicine Hospital",
        substrateStatus: "ROUTABLE_VIA_EXISTING_SEAMS",
        currentRoutingSeams: [{ routingType: "SPECIALTY_POOL", targetRef: "GENERAL_TELEMEDICINE" }],
      },
      {
        id: "vh-maternal-newborn",
        name: "Virtual Maternal & Newborn Hospital",
        substrateStatus: "ROUTABLE_VIA_EXISTING_SEAMS",
        currentRoutingSeams: [{ routingType: "SPECIALTY_POOL", targetRef: "MATERNAL_NEWBORN" }],
      },
      {
        id: "vh-mental-health",
        name: "Virtual Mental Health Unit",
        substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE",
        currentRoutingSeams: [],
      },
    ];
    poolQueue.isLoading = false;
    poolQueue.isError = false;
    poolQueue.data = {
      data: [
        {
          id: "vc-1",
          specialty: "GENERAL_TELECONSULTATION",
          urgency: "ROUTINE",
          reason: "Cough for two weeks",
          routingPoolId: "GENERAL_TELEMEDICINE",
        },
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
      ),
    );
  });

  it("lists only routable specialty pools in the pool picker with honest roster copy", async () => {
    const user = userEvent.setup();
    render(<SpecialistWorklistPage />);

    await user.click(screen.getByRole("tab", { name: "Virtual hospital pools" }));

    // Routable seams become pool options; the non-routable VH does not.
    expect(
      screen.getByRole("option", { name: "National Telemedicine Hospital (GENERAL_TELEMEDICINE)" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Virtual Maternal & Newborn Hospital (MATERNAL_NEWBORN)" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Mental Health/ })).not.toBeInTheDocument();

    // Honest copy: no duty rosters yet — any authorized provider may accept.
    expect(
      screen.getByText(/Any authorized telemedicine provider can accept from a pool/),
    ).toBeInTheDocument();
    expect(screen.getByText(/duty\s+rosters arrive in Wave 2/)).toBeInTheDocument();
  });

  it("renders the pool queue and wires the existing governed accept action", async () => {
    const user = userEvent.setup();
    render(<SpecialistWorklistPage />);

    await user.click(screen.getByRole("tab", { name: "Virtual hospital pools" }));

    expect(await screen.findByText("Cough for two weeks")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Accept" }));

    await waitFor(() =>
      expect(acceptMutate).toHaveBeenCalledWith(
        expect.objectContaining({ id: "vc-1", receivingFacilityId: "facility-1" }),
      ),
    );
  });
});
