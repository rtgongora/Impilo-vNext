import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import CitizenRequestHealthIdPage from "./page";

vi.mock("@/lib/citizenPortalClient", () => ({
  citizenPortalApi: { requestId: vi.fn().mockResolvedValue({}) },
}));

const DRAFT_KEY = "impilo:health-id-request-draft";

describe("CitizenRequestHealthIdPage — resumable draft (G-CZO-09)", () => {
  beforeEach(() => localStorage.clear());

  it("restores an in-progress draft on mount and shows the restored banner", () => {
    localStorage.setItem(
      DRAFT_KEY,
      JSON.stringify({ type: "NEW", givenName: "Tendai", familyName: "Moyo", dateOfBirth: "1990-01-01", sex: "F" }),
    );

    render(<CitizenRequestHealthIdPage />);

    expect((screen.getByLabelText("Given name") as HTMLInputElement).value).toBe("Tendai");
    expect((screen.getByLabelText("Family name") as HTMLInputElement).value).toBe("Moyo");
    expect(screen.getByText(/restored your saved progress/i)).toBeInTheDocument();
  });

  it("persists typed progress to localStorage so a dropped connection doesn't lose it", () => {
    render(<CitizenRequestHealthIdPage />);
    expect(localStorage.getItem(DRAFT_KEY)).toBeNull();

    fireEvent.change(screen.getByLabelText("Given name"), { target: { value: "Rudo" } });

    const saved = JSON.parse(localStorage.getItem(DRAFT_KEY) ?? "{}");
    expect(saved.givenName).toBe("Rudo");
  });

  it("does not show the restored banner on a fresh start", () => {
    render(<CitizenRequestHealthIdPage />);
    expect(screen.queryByText(/restored your saved progress/i)).not.toBeInTheDocument();
  });
});
