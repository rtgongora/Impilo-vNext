import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import VerifyPractitionerPage from "./page";
import { apiClient } from "@/lib/api-client";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(""),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn() },
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

const REGISTERED = {
  registerStatus: "REGISTERED",
  displayName: "Dr Tariro Moyo",
  profession: "Medical Practitioner",
  cadre: "GENERAL_PRACTITIONER",
  registrationNumber: "MDPCZ-778899",
  registeredSince: "2019-02-01",
  registrationExpiryDate: "2026-12-31",
  licenceStatus: "ACTIVE",
  licenceValidFrom: "2026-01-01",
  licenceValidTo: "2026-12-31",
  registeringAuthority: "Medical and Dental Practitioners Council of Zimbabwe",
};

const NOT_FOUND = {
  registerStatus: "NOT_FOUND",
  displayName: null,
  profession: null,
  cadre: null,
  registrationNumber: null,
  registeredSince: null,
  registrationExpiryDate: null,
  licenceStatus: null,
  licenceValidFrom: null,
  licenceValidTo: null,
  registeringAuthority: null,
};

describe("VerifyPractitionerPage", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("renders the registration-number input and verify button", () => {
    render(<VerifyPractitionerPage />);
    expect(screen.getByLabelText(/Registration number/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Verify/i })).toBeInTheDocument();
  });

  it("verifies via the public gateway lane and renders register facts", async () => {
    get.mockResolvedValue(REGISTERED);
    render(<VerifyPractitionerPage />);

    fireEvent.change(screen.getByLabelText(/Registration number/i), {
      target: { value: "MDPCZ-778899" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Verify/i }));

    expect(await screen.findByText(/Dr Tariro Moyo/i)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/public/gateway/practitioners/verify/MDPCZ-778899",
    );
    expect(screen.getByText("REGISTERED")).toBeInTheDocument();
    expect(screen.getByText(/Medical Practitioner/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Registering authority/i),
    ).toBeInTheDocument();
  });

  it("shows the not-found state for a NOT_FOUND register status (still HTTP 200)", async () => {
    get.mockResolvedValue(NOT_FOUND);
    render(<VerifyPractitionerPage />);

    fireEvent.change(screen.getByLabelText(/Registration number/i), {
      target: { value: "NOPE-1" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Verify/i }));

    expect(
      await screen.findByText(/No registered professional matches this number/i),
    ).toBeInTheDocument();
  });

  it("shows the unavailable state when the lane errors", async () => {
    get.mockRejectedValue(Object.assign(new Error("bad gateway"), { status: 502 }));
    render(<VerifyPractitionerPage />);

    fireEvent.change(screen.getByLabelText(/Registration number/i), {
      target: { value: "MDPCZ-778899" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Verify/i }));

    await waitFor(() =>
      expect(screen.getByText(/temporarily unavailable/i)).toBeInTheDocument(),
    );
  });
});
