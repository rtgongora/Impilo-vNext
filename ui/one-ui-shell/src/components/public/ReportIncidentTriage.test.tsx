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

  it("offers a fully anonymous branch with no sign-in gate", () => {
    render(<ReportIncidentTriage />);
    const anonymous = screen.getByTestId("report-branch-anonymous");
    expect(anonymous).toHaveAttribute("href", "/welcome/report/anonymous");
    expect(screen.getByText(/your\s+identity is never recorded/i)).toBeInTheDocument();
  });

  it("routes the signed-in branch through the sign-in gate with the feedback destination", () => {
    render(<ReportIncidentTriage />);
    const branch = screen.getByText("Report from your account").closest("a");
    expect(branch).not.toBeNull();
    expect(branch).toHaveAttribute(
      "href",
      `/auth/login?returnTo=${encodeURIComponent("/my-life/feedback/new?type=SAFETY_CONCERN")}`,
    );
    expect(screen.getByText("Sign in to continue")).toBeInTheDocument();
  });

  it("links claim-code status checking", () => {
    render(<ReportIncidentTriage />);
    expect(screen.getByTestId("report-branch-status")).toHaveAttribute(
      "href",
      "/welcome/report/status",
    );
  });
});
