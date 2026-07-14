import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ShareFundPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const authState = vi.hoisted(() => ({
  user: null as { id: string } | null,
  token: null as string | null,
}));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: typeof authState) => unknown) => selector(authState),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ token: "tok-1" }),
}));

const SHARE_VIEW = {
  data: {
    request: {
      id: "req-1",
      title: "Help with surgery costs",
      targetAmount: 1000,
      raisedAmount: 250,
      currency: "USD",
      status: "OPEN",
    },
    contributions: [{ id: "c1" }, { id: "c2" }],
  },
};

describe("ShareFundPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    authState.user = null;
    authState.token = null;
  });

  it("renders the tokenized money view and routes signed-out donors to login", async () => {
    get.mockResolvedValue(SHARE_VIEW);
    render(<ShareFundPage />);

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith("/internal/v1/wallet/bill-contributions/tok-1"),
    );
    expect(await screen.findByText("Help with surgery costs")).toBeInTheDocument();
    expect(screen.getByText("USD 250")).toBeInTheDocument();
    expect(screen.getByText(/of USD 1,000/)).toBeInTheDocument();
    expect(screen.getByText("2 contributions")).toBeInTheDocument();
    expect(screen.getByTestId("share-donate-cta")).toHaveAttribute(
      "href",
      "/auth/login?returnTo=%2Fshare%2Ffund%2Ftok-1",
    );
  });

  it("routes signed-in donors to the governed donate flow on the fundraiser detail", async () => {
    authState.user = { id: "user-1" };
    authState.token = "tok";
    get.mockImplementation((url: string) =>
      url === "/internal/v1/citizen/fundraisers"
        ? Promise.resolve([{ id: "case-1", shareToken: "tok-1", verificationStatus: "ACTIVE" }])
        : Promise.resolve(SHARE_VIEW),
    );
    render(<ShareFundPage />);

    await waitFor(() =>
      expect(screen.getByTestId("share-donate-cta")).toHaveAttribute(
        "href",
        "/my-life/fundraisers/case-1?donate=1",
      ),
    );
  });

  it("shows an honest unknown-link state on 404", async () => {
    get.mockRejectedValue({ status: 404, error: { code: "CONTRIBUTION_LINK_UNKNOWN" } });
    render(<ShareFundPage />);

    expect(await screen.findByTestId("share-not-found")).toBeInTheDocument();
  });

  it("shows an honest sign-in-required state when the deployment gates the view (401)", async () => {
    get.mockRejectedValue({ status: 401, error: { code: "SESSION_EXPIRED" } });
    render(<ShareFundPage />);

    const state = await screen.findByTestId("share-signin-required");
    expect(state).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Sign in to continue/ })).toHaveAttribute(
      "href",
      "/auth/login?returnTo=%2Fshare%2Ffund%2Ftok-1",
    );
  });

  it("shows other upstream failures honestly with a retry", async () => {
    get.mockRejectedValue({ status: 503, error: { message: "service is unavailable right now" } });
    render(<ShareFundPage />);

    expect(await screen.findByText("service is unavailable right now")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Retry/ })).toBeInTheDocument();
  });
});
