import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ClinicalWizardHeader } from "./ClinicalWizardHeader";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), prefetch: vi.fn() }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({
    isClinical: true,
    isPrescriber: false,
    isDispenser: false,
    isAdmin: false,
  }),
}));

describe("ClinicalWizardHeader", () => {
  it("renders encounter workflow strip with default steps", () => {
    render(<ClinicalWizardHeader patientId="p1" encounterId={null} pathname="/ehr/p1" />);
    expect(screen.getByText("Encounter workflow")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /1\.\s*Patient Context/i })).toBeInTheDocument();
  });
});
