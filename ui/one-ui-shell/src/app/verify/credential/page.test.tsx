import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import VerifyCredentialPage from "./page";

describe("VerifyCredentialPage", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_CREDENTIAL_VERIFY_PUBLIC_URL", "http://localhost:8094");
  });

  it("renders token input and verify button when base URL configured", () => {
    render(<VerifyCredentialPage />);
    expect(screen.getByLabelText(/Verification token/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Verify/i })).toBeInTheDocument();
  });
});
