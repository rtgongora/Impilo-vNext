import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { FleetAssetCreatePanel } from "./FleetAssetCreatePanel";
import { CourierCreatePanel } from "./CourierCreatePanel";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

const fleetMutate = vi.fn().mockResolvedValue({});
const courierMutate = vi.fn().mockResolvedValue({});
vi.mock("@/hooks/useNhume", () => ({
  useCreateFleetAsset: () => ({ mutateAsync: fleetMutate, isPending: false }),
  useCreateCourier: () => ({ mutateAsync: courierMutate, isPending: false }),
}));

beforeEach(() => {
  push.mockClear();
  fleetMutate.mockClear();
  courierMutate.mockClear();
});

describe("Nhume create panels are wired (not decorative)", () => {
  it("fleet: registers an asset with capabilities and returns to the list", async () => {
    render(<FleetAssetCreatePanel />);
    fireEvent.change(screen.getByPlaceholderText(/AMB-HRE/i), { target: { value: "AMB-HRE-014" } });
    fireEvent.click(screen.getByLabelText(/Cold-chain/i));
    fireEvent.click(screen.getByLabelText(/Specimen transport/i));
    fireEvent.click(screen.getByRole("button", { name: /Register asset/i }));

    await waitFor(() => expect(fleetMutate).toHaveBeenCalledTimes(1));
    const payload = fleetMutate.mock.calls[0][0];
    expect(payload.registration_number).toBe("AMB-HRE-014");
    expect(payload.asset_code).toBe("AMB-HRE-014");
    expect(payload.cold_chain_capable).toBe(true);
    expect(payload.specimen_capable).toBe(true);
    await waitFor(() => expect(push).toHaveBeenCalledWith("/nhume/fleet"));
  });

  it("fleet: blocks submit without a registration and never calls the API", async () => {
    render(<FleetAssetCreatePanel />);
    fireEvent.click(screen.getByRole("button", { name: /Register asset/i }));
    expect(await screen.findByText(/Registration \/ identifier is required/i)).toBeInTheDocument();
    expect(fleetMutate).not.toHaveBeenCalled();
  });

  it("courier: registers a responder with kind and verification", async () => {
    render(<CourierCreatePanel />);
    fireEvent.change(screen.getByPlaceholderText(/Tafadzwa Moyo/i), { target: { value: "Tendai Chikomba" } });
    fireEvent.click(screen.getByLabelText(/Trust verified/i));
    fireEvent.click(screen.getByRole("button", { name: /Register courier/i }));

    await waitFor(() => expect(courierMutate).toHaveBeenCalledTimes(1));
    const payload = courierMutate.mock.calls[0][0];
    expect(payload.display_name).toBe("Tendai Chikomba");
    expect(payload.courier_code).toBe("TENDAI-CHIKOMBA");
    expect(payload.trust_verified).toBe(true);
    await waitFor(() => expect(push).toHaveBeenCalledWith("/nhume/couriers"));
  });
});
