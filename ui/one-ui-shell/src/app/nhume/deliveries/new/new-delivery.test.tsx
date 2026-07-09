import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import NhumeNewDeliveryPage from "./page";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

const mutateAsync = vi.fn().mockResolvedValue({ delivery_id: "d-1" });
vi.mock("@/hooks/useNhume", () => ({
  useCreateNhumeDelivery: () => ({ mutateAsync, isPending: false, isError: false, error: null }),
}));

// AppLayout/PageShell pull in heavy shell chrome; stub to keep the test on the form.
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));

beforeEach(() => {
  push.mockClear();
  mutateAsync.mockClear();
});

describe("Nhume new-delivery is cargo-type-aware", () => {
  it("a blood mission persists cold-chain + custody + the MADI link (not a patient-only form)", async () => {
    render(<NhumeNewDeliveryPage />);

    // Choose the Blood cargo profile.
    fireEvent.click(screen.getByRole("button", { name: /Blood \/ blood product/i }));

    // Type-specific fields now exist — fill the MADI order link + units.
    const madi = screen.getByPlaceholderText(/madi order id/i);
    fireEvent.change(madi, { target: { value: "MADI-42" } });

    fireEvent.click(screen.getByRole("button", { name: /Create delivery/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledTimes(1));
    const payload = mutateAsync.mock.calls[0][0];
    expect(payload.delivery_type).toBe("BLOOD_PRODUCT");
    expect(payload.cold_chain_required).toBe(true);
    expect(payload.chain_of_custody_required).toBe(true);
    expect(payload.clinical_context_ref).toBe("MADI-42");
    expect(payload.metadata.links.madiOrderRef).toBe("MADI-42");
    expect(payload.metadata.cargoProfile).toBe("BLOOD");
    await waitFor(() => expect(push).toHaveBeenCalledWith("/nhume/deliveries/d-1"));
  });

  it("a specimen mission flags specimen handling and links the lab order", async () => {
    render(<NhumeNewDeliveryPage />);
    fireEvent.click(screen.getByRole("button", { name: /Lab specimen \/ sample/i }));
    fireEvent.change(screen.getByPlaceholderText(/^order id$/i), { target: { value: "ORD-7" } });
    fireEvent.click(screen.getByRole("button", { name: /Create delivery/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledTimes(1));
    const payload = mutateAsync.mock.calls[0][0];
    expect(payload.delivery_type).toBe("LAB_SAMPLE_PICKUP");
    expect(payload.specimen).toBe(true);
    expect(payload.chain_of_custody_required).toBe(true);
    expect(payload.metadata.links.orosOrderRef).toBe("ORD-7");
  });
});
