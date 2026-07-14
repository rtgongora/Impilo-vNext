import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FundraiserDetailPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const authState = vi.hoisted(() => ({
  user: { id: "user-1", healthId: "HID-1" } as { id: string; healthId?: string } | null,
  token: "tok",
}));

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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: typeof authState) => unknown) => selector(authState),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "case-1" }),
  useSearchParams: () => new URLSearchParams(),
}));

const DETAIL = {
  case: {
    id: "case-1",
    assistanceReference: "AS-2026-0001",
    title: "Help with surgery costs",
    story: "I need help.",
    category: "SURGERY",
    targetAmount: 1000,
    raisedAmount: 250,
    donorCount: 3,
    currency: "USD",
    verificationStatus: "ACTIVE",
    shareToken: "tok-1",
    attestationPublicToken: "https://verify.example/att-1",
    requesterActorId: "HID-1",
  },
  shareUrl: "/share/fund/tok-1",
  independentVerificationUrl: "https://verify.example/att-1",
};

function mockDetail(detail: unknown = DETAIL) {
  get.mockImplementation((url: string) => {
    if (url.endsWith("/timeline")) {
      return Promise.resolve([
        {
          eventType: "VERIFIED",
          fromStatus: "PENDING_VERIFICATION",
          toStatus: "ACTIVE",
          note: "Attested by facility",
          occurredAt: "2026-07-01T10:00:00Z",
        },
      ]);
    }
    if (url.endsWith("/contributions")) {
      return Promise.resolve([
        {
          contributionId: "ct-1",
          amount: 50,
          isAnonymous: true,
          contributorLabel: "Tafadzwa M.",
          recordedAt: "2026-07-02T10:00:00Z",
        },
        {
          contributionId: "ct-2",
          amount: 200,
          isAnonymous: false,
          contributorLabel: "Rudo C.",
          message: "Get well soon",
          recordedAt: "2026-07-03T10:00:00Z",
        },
      ]);
    }
    return Promise.resolve(detail);
  });
}

describe("FundraiserDetailPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    authState.user = { id: "user-1", healthId: "HID-1" };
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("renders progress, timeline, verify link and anonymised contributions", async () => {
    mockDetail();
    render(<FundraiserDetailPage />);

    expect(await screen.findByText("Help with surgery costs")).toBeInTheDocument();
    expect(screen.getByTestId("verified-badge")).toBeInTheDocument();
    expect(screen.getByTestId("verify-link")).toHaveAttribute(
      "href",
      "https://verify.example/att-1",
    );
    // Timeline event rendered with the honest status label
    expect(screen.getByText("Attested by facility")).toBeInTheDocument();
    // Anonymous contribution never leaks the label; named contribution shows it
    expect(screen.getByText("Anonymous")).toBeInTheDocument();
    expect(screen.queryByText("Tafadzwa M.")).not.toBeInTheDocument();
    expect(screen.getByText("Rudo C.")).toBeInTheDocument();
  });

  it("donates with a fresh Idempotency-Key per attempt and reloads on success", async () => {
    mockDetail();
    post.mockResolvedValue({ id: "contrib-1" });
    render(<FundraiserDetailPage />);

    await userEvent.click(await screen.findByTestId("donate-open"));
    await userEvent.type(screen.getByTestId("donate-amount"), "25");
    await userEvent.type(screen.getByTestId("donate-message"), "Stay strong");
    await userEvent.click(screen.getByTestId("donate-anonymous"));
    await userEvent.click(screen.getByTestId("donate-submit"));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/citizen/fundraisers/case-1/donate",
        { amount: "25", message: "Stay strong", isAnonymous: true },
        expect.objectContaining({
          extraHeaders: expect.objectContaining({
            "Idempotency-Key": expect.stringMatching(/^[0-9a-f-]{36}$/i),
          }),
        }),
      ),
    );
    expect(
      await screen.findByText("Thank you — your donation was recorded."),
    ).toBeInTheDocument();
  });

  it("shows the 409 not-open rejection verbatim", async () => {
    mockDetail();
    post.mockRejectedValue({
      status: 409,
      error: {
        code: "FUNDRAISER_NOT_OPEN",
        message: "This fundraiser is not open for donations (CANCELLED).",
      },
    });
    render(<FundraiserDetailPage />);

    await userEvent.click(await screen.findByTestId("donate-open"));
    await userEvent.type(screen.getByTestId("donate-amount"), "25");
    await userEvent.click(screen.getByTestId("donate-submit"));

    expect(await screen.findByTestId("donate-error")).toHaveTextContent(
      "This fundraiser is not open for donations (CANCELLED).",
    );
  });

  it("hides the donate entry for non-ACTIVE cases and keeps the status honest", async () => {
    mockDetail({
      case: { ...DETAIL.case, verificationStatus: "PENDING_VERIFICATION" },
      shareUrl: "/share/fund/tok-1",
    });
    render(<FundraiserDetailPage />);

    expect(await screen.findByTestId("status-chip")).toHaveTextContent(
      "Awaiting facility verification",
    );
    expect(screen.queryByTestId("donate-open")).not.toBeInTheDocument();
  });

  it("offers owner cancel with a confirm dialog", async () => {
    mockDetail();
    post.mockResolvedValue({});
    render(<FundraiserDetailPage />);

    await userEvent.click(await screen.findByTestId("cancel-action"));

    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/citizen/fundraisers/case-1/cancel",
        undefined,
      ),
    );
  });

  it("hides owner actions from non-owners", async () => {
    authState.user = { id: "someone-else" };
    mockDetail();
    render(<FundraiserDetailPage />);

    await screen.findByText("Help with surgery costs");
    expect(screen.queryByTestId("cancel-action")).not.toBeInTheDocument();
    expect(screen.queryByTestId("release-action")).not.toBeInTheDocument();
  });
});
