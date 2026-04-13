import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PrivacyPolicyPage from "./page";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

describe("PrivacyPolicyPage", () => {
  it("renders the privacy policy with title and effective date", () => {
    render(<PrivacyPolicyPage />);

    expect(screen.getByText("Impilo Privacy Policy")).toBeInTheDocument();
    expect(screen.getByText(/Effective Date: 11 April 2026/)).toBeInTheDocument();
  });

  it("contains all 20 numbered policy sections", () => {
    render(<PrivacyPolicyPage />);

    const sectionTitles = [
      "1. Introduction",
      "2. Privacy Summary",
      "3. Scope",
      "4. Roles and Responsibilities",
      "5. Information We May Collect",
      "6. How We Collect Information",
      "7. How We Use Information",
      "8. Legal Bases for Processing",
      "9. Data Residency, Sovereignty, and Privacy by Design",
      "10. Sharing and Disclosure",
      "11. Provider Responsibility",
      "12. Data Retention",
      "13. Account Deletion",
      "14. Security",
      "15. International Transfers",
      "16. Your Rights",
      "17. Children and Age-Appropriate Use",
      "18. Third-Party Services",
      "19. Changes to This Policy",
      "20. Contact Us",
    ];

    for (const title of sectionTitles) {
      expect(screen.getByText(title)).toBeInTheDocument();
    }
  });

  it("shows navigation links to terms, sign in, and account deletion", () => {
    render(<PrivacyPolicyPage />);

    expect(screen.getByRole("link", { name: "Terms of Use" })).toHaveAttribute("href", "/terms");
    expect(screen.getByRole("link", { name: "Sign In" })).toHaveAttribute("href", "/auth/login");
    expect(screen.getByRole("link", { name: "Account Deletion" })).toHaveAttribute("href", "/account-deletion");
  });
});
