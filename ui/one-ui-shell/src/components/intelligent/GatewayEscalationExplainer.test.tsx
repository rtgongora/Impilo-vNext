import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { GatewayEscalationExplainer } from "./GatewayEscalationExplainer";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function renderExplainer(props: Partial<Parameters<typeof GatewayEscalationExplainer>[0]> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <GatewayEscalationExplainer stepKey="signin-to-book" {...props} />
    </QueryClientProvider>,
  );
}

const SIGNIN_TO_BOOK = {
  data: {
    stepKey: "signin-to-book",
    pillar: "get-care",
    rungFrom: "R1",
    rungTo: "R2",
    title: "Sign in to book your visit",
    why: "Appointments become part of your personal health record.",
    levelLabel: "Sign in with your account or Health ID.",
    whatNext: "Your booking details are saved; you will return to this exact step.",
    howToGetHelp: "Use the recovery options or ask for assisted access.",
    continueWithoutLabel: "Continue without signing in",
  },
};

describe("GatewayEscalationExplainer", () => {
  it("fetches from the public lane and renders the four doctrinal parts", async () => {
    get.mockResolvedValue(SIGNIN_TO_BOOK);
    renderExplainer();

    expect(await screen.findByText(/Sign in to book your visit/i)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/public/gateway/guidance/explain-steps/signin-to-book",
    );
    // why / what level / what next / how to get help
    expect(screen.getByText(/personal health record/i)).toBeInTheDocument();
    expect(screen.getByText(/account or Health ID/i)).toBeInTheDocument();
    expect(screen.getByText(/return to this exact step/i)).toBeInTheDocument();
    expect(screen.getByText(/assisted access/i)).toBeInTheDocument();
  });

  it("offers Continue without signing in when a public return path exists", async () => {
    get.mockResolvedValue(SIGNIN_TO_BOOK);
    renderExplainer({ continueWithoutHref: "/welcome/find-care" });

    const link = await screen.findByTestId("gateway-continue-without");
    expect(link).toHaveAttribute("href", "/welcome/find-care");
    expect(link).toHaveTextContent(/continue without signing in/i);
  });

  it("hides the continue option when the activity requires authentication", async () => {
    get.mockResolvedValue(SIGNIN_TO_BOOK);
    renderExplainer({ continueWithoutHref: null });

    await screen.findByText(/Sign in to book your visit/i);
    expect(screen.queryByTestId("gateway-continue-without")).not.toBeInTheDocument();
  });

  it("still mediates with safe fallback copy when the public lane is unreachable", async () => {
    get.mockRejectedValue(new Error("lane not open yet"));
    renderExplainer();

    // Never a bare auth wall: fallback keeps why + progress-saved reassurance,
    // and never surfaces security internals or error details.
    expect(await screen.findByText(/Sign in to continue/i)).toBeInTheDocument();
    expect(screen.getByText(/progress is saved/i)).toBeInTheDocument();
    expect(screen.queryByText(/lane not open yet/i)).not.toBeInTheDocument();
  });
});
