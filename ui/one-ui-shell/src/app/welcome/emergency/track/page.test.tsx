import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EmergencyTrackPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
const { searchParamsGet } = vi.hoisted(() => ({ searchParamsGet: vi.fn() }));

vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: searchParamsGet }),
  // PublicShell now mounts the PublicBackBar continuity strip on sub-pages.
  usePathname: () => "/welcome/emergency/track",
  useRouter: () => ({ back: vi.fn(), push: vi.fn() }),
}));

const STATUS = {
  requestReference: "SOS-2026-000123",
  status: "AWAITING_CALLBACK",
  stage: "Awaiting callback confirmation",
  callbackPending: true,
  createdAt: "2026-07-15T10:00:00Z",
};

describe("EmergencyTrackPage (public SOS status-by-reference, anonymous R0)", () => {
  beforeEach(() => {
    get.mockReset();
    searchParamsGet.mockReset();
    searchParamsGet.mockReturnValue(null);
  });

  it("looks up status on submit via the public gateway lane and renders the PII-free status", async () => {
    get.mockResolvedValue(STATUS);
    render(<EmergencyTrackPage />);

    fireEvent.change(screen.getByTestId("track-reference-input"), {
      target: { value: "SOS-2026-000123" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check status/i }));

    expect(await screen.findByTestId("track-status")).toBeInTheDocument();
    expect(screen.getByText(/Awaiting callback confirmation/)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/public/gateway/sos/SOS-2026-000123",
    );
  });

  it("prefills and auto-tracks from the ?ref= receipt link", async () => {
    searchParamsGet.mockImplementation((k: string) => (k === "ref" ? "SOS-2026-000123" : null));
    get.mockResolvedValue(STATUS);
    render(<EmergencyTrackPage />);

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith("/internal/v1/public/gateway/sos/SOS-2026-000123"),
    );
    expect(await screen.findByTestId("track-status")).toBeInTheDocument();
  });

  it("shows an honest not-found state on 404", async () => {
    get.mockRejectedValue({ status: 404 });
    render(<EmergencyTrackPage />);

    fireEvent.change(screen.getByTestId("track-reference-input"), {
      target: { value: "SOS-NONE" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check status/i }));

    expect(await screen.findByTestId("track-not-found")).toBeInTheDocument();
  });
});
