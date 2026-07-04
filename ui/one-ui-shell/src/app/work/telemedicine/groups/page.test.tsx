import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import ClinicalGroupsPage from "./page";
import { CLINICAL_GROUP_TYPES } from "@/lib/telemedicine/clinical-groups";

describe("/work/telemedicine/groups", () => {
  it("keeps clinical group creation fail-closed with the reason stated", () => {
    render(<ClinicalGroupsPage />);
    expect(screen.getByText(/group creation is governance-blocked/i)).toBeInTheDocument();
    expect(screen.getByText(/creation stays disabled until the governed model ships/i)).toBeInTheDocument();
    // No create affordance exists anywhere on the page.
    expect(screen.queryByRole("button", { name: /create/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /create/i })).not.toBeInTheDocument();
  });

  it("renders the full governed taxonomy", () => {
    render(<ClinicalGroupsPage />);
    for (const group of CLINICAL_GROUP_TYPES) {
      expect(screen.getByText(group.label)).toBeInTheDocument();
    }
  });

  it("shows patient-data exposure limits per group type", async () => {
    const user = userEvent.setup();
    render(<ClinicalGroupsPage />);

    // MDT panels never get care-team-scoped data
    expect(screen.getAllByText("Case presentation only").length).toBeGreaterThan(0);

    await user.click(screen.getByText("MDT panel"));
    expect(screen.getByText("Oncology MDT")).toBeInTheDocument();
    expect(screen.getByText("MDT / Case Review Mode")).toBeInTheDocument();
  });

  it("explains why wellness social groups are not reused", () => {
    render(<ClinicalGroupsPage />);
    expect(screen.getByText(/why wellness social groups are not reused/i)).toBeInTheDocument();
  });
});
