import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import NewFundraiserPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

async function fillAndSubmit() {
  await userEvent.type(screen.getByTestId("title-input"), "Help with surgery");
  await userEvent.type(screen.getByTestId("story-input"), "I need an operation.");
  await userEvent.type(screen.getByTestId("target-input"), "1500");
  await userEvent.click(screen.getByTestId("create-submit"));
}

describe("NewFundraiserPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("creates a DRAFT and shows the honest go-public explainer", async () => {
    post.mockResolvedValue({
      id: "case-9",
      assistanceReference: "AS-2026-0009",
      verificationStatus: "DRAFT",
    });
    render(<NewFundraiserPage />);
    await fillAndSubmit();

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/citizen/fundraisers",
        expect.objectContaining({
          title: "Help with surgery",
          story: "I need an operation.",
          targetAmount: "1500",
          currency: "USD",
          category: "MEDICAL_COSTS",
          subjectIdentityMode: "ANONYMOUS",
        }),
      ),
    );
    expect(await screen.findByTestId("draft-explainer")).toBeInTheDocument();
    expect(screen.getByText(/verified by your treating\s+facility/)).toBeInTheDocument();
    expect(screen.getByTestId("consent-ref-input")).toBeInTheDocument();
  });

  it("sends KNOWN identity mode when the anonymous toggle is off", async () => {
    post.mockResolvedValue({ id: "case-9", verificationStatus: "DRAFT" });
    render(<NewFundraiserPage />);
    await userEvent.click(screen.getByTestId("anonymous-toggle"));
    await fillAndSubmit();

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/citizen/fundraisers",
        expect.objectContaining({ subjectIdentityMode: "KNOWN" }),
      ),
    );
  });

  it("requests verification with the care-team consent reference (no auto-consent)", async () => {
    post.mockResolvedValue({ id: "case-9", verificationStatus: "DRAFT" });
    render(<NewFundraiserPage />);
    await fillAndSubmit();
    await screen.findByTestId("draft-explainer");

    // Verification cannot be requested without a consent reference.
    expect(screen.getByTestId("request-verification-submit")).toBeDisabled();

    post.mockResolvedValue({ id: "case-9", verificationStatus: "PENDING_VERIFICATION" });
    await userEvent.type(screen.getByTestId("consent-ref-input"), "CONSENT-42");
    await userEvent.click(screen.getByTestId("request-verification-submit"));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/citizen/fundraisers/case-9/request-verification",
        { consentRef: "CONSENT-42" },
      ),
    );
    expect(await screen.findByText("Verification requested")).toBeInTheDocument();
  });

  it("surfaces creation failures honestly", async () => {
    post.mockRejectedValue({ status: 400, error: { message: "targetAmount is required" } });
    render(<NewFundraiserPage />);
    await fillAndSubmit();

    expect(await screen.findByText("targetAmount is required")).toBeInTheDocument();
  });
});
