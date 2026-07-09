import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import NhumeInboundPage from "./page";

const proofMutate = vi.fn().mockResolvedValue({});
vi.mock("@/hooks/useNhume", () => ({
  useNhumeDeliveries: () => ({
    data: {
      data: [
        { delivery_id: "d-1", reference: "NHU-1", delivery_type: "LAB_SAMPLE_PICKUP", priority: "URGENT", status: "IN_TRANSIT", origin_label: "Clinic", destination_label: "District Lab", chain_of_custody_required: true },
        { delivery_id: "d-2", reference: "NHU-2", delivery_type: "MEDICINE", priority: "STANDARD", status: "DRAFT" },
      ],
    },
    isPending: false,
  }),
  useRecordProof: () => ({ mutateAsync: proofMutate, isPending: false, isError: false }),
}));
vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (sel: (s: unknown) => unknown) => sel({ facility: { name: "District Lab" } }),
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));

beforeEach(() => proofMutate.mockClear());

describe("Nhume inbound & handover", () => {
  it("shows only inbound (in-transit) movements, not drafts", () => {
    render(<NhumeInboundPage />);
    expect(screen.getByText("NHU-1")).toBeInTheDocument();
    expect(screen.queryByText("NHU-2")).not.toBeInTheDocument();
  });

  it("confirming handover records proof with the receiver — closing the loop", async () => {
    render(<NhumeInboundPage />);
    // Type-aware handover label for a specimen.
    fireEvent.click(screen.getByRole("button", { name: /Lab receives specimen/i }));
    fireEvent.change(screen.getByPlaceholderText(/name \/ role of receiver/i), { target: { value: "Sr Ncube (Lab)" } });
    fireEvent.click(screen.getByRole("button", { name: /Confirm receipt/i }));

    await waitFor(() => expect(proofMutate).toHaveBeenCalledTimes(1));
    const payload = proofMutate.mock.calls[0][0];
    expect(payload.method).toBe("FACILITY_STAMP");
    expect(payload.recipient_name).toBe("Sr Ncube (Lab)");
  });
});
