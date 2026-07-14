import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ReportIncidentTriage } from "./ReportIncidentTriage";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

describe("ReportIncidentTriage", () => {
  it("renders the emergency branch linking to the always-open emergency surface", () => {
    render(<ReportIncidentTriage />);
    const emergency = screen.getByTestId("report-branch-emergency");
    expect(emergency).toHaveAttribute("href", "/welcome/emergency");
    expect(screen.getByText("Someone needs help right now")).toBeInTheDocument();
    expect(screen.getByText("Never blocked by sign-in")).toBeInTheDocument();
  });

  it("routes the safety-concern branch through the sign-in gate with the feedback destination", () => {
    render(<ReportIncidentTriage />);
    const branch = screen.getByText("Unsafe care, a safety concern, or a complaint").closest("a");
    expect(branch).not.toBeNull();
    expect(branch).toHaveAttribute(
      "href",
      `/auth/login?returnTo=${encodeURIComponent("/my-life/feedback/new?type=SAFETY_CONCERN")}`,
    );
    expect(screen.getByText("Sign in to continue")).toBeInTheDocument();
  });

  it("states the anonymity posture honestly (anonymous after sign-in)", () => {
    render(<ReportIncidentTriage />);
    expect(screen.getByText(/Anonymous submission is available once signed in/)).toBeInTheDocument();
  });
});
