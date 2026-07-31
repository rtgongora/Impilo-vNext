import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RespectfulMaternityCarePage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
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

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

const INSTRUMENT_PAYLOAD = {
  instrument_id: "RESPECTFUL_MATERNITY_CARE",
  name: "Respectful maternity care experience",
  scale: { type: "LIKERT_5", low: 1, high: 5, low_means: "never", high_means: "always" },
  measures: [
    {
      measure: "respect_dignity",
      prompt: "Were you treated with respect and dignity?",
      mistreatment_category: "NON_DIGNIFIED_CARE",
      reverse_scored: false,
    },
    {
      measure: "never_left_alone",
      prompt: "Were you left alone when you needed someone?",
      mistreatment_category: "ABANDONMENT",
      reverse_scored: true,
    },
  ],
  anonymous_by_default: true,
};

describe("/my-life/feedback/respectful-maternity", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("fetches the instrument and renders its real prompts (never a hardcoded question set)", async () => {
    get.mockResolvedValue({ data: INSTRUMENT_PAYLOAD });

    render(<RespectfulMaternityCarePage />);

    expect(await screen.findByText("Were you treated with respect and dignity?")).toBeInTheDocument();
    expect(screen.getByText("Were you left alone when you needed someone?")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/maternity/respectful-care/instrument");
  });

  it("submits raw answers and shows the claim code prominently, once, with a save instruction", async () => {
    get.mockResolvedValue({ data: INSTRUMENT_PAYLOAD });
    post.mockResolvedValue({
      data: {
        narrative: {
          filed: true,
          case_reference: "RITO-2026-000123",
          claim_code: "CLAIM-ABC-123",
        },
        scores: { stored: true, measures_stored: 1 },
        anonymous: true,
        narrative_linked_to_rating: false,
      },
    });

    const user = userEvent.setup();
    render(<RespectfulMaternityCarePage />);

    await screen.findByText("Were you treated with respect and dignity?");
    await user.click(screen.getByTestId("rmc-answer-respect_dignity-5"));
    await user.type(screen.getByTestId("rmc-narrative"), "It went well");
    await user.click(screen.getByTestId("rmc-submit"));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/maternity/respectful-care/feedback",
      expect.objectContaining({
        answers: { respect_dignity: 5 },
        narrative: "It went well",
        identifyMe: false,
      }),
    );

    expect(await screen.findByTestId("rmc-claim-code")).toHaveTextContent("CLAIM-ABC-123");
    expect(screen.getByText(/shown only once and cannot be recovered/i)).toBeInTheDocument();
  });

  it("says so honestly when scores failed to store but the narrative was still filed", async () => {
    get.mockResolvedValue({ data: INSTRUMENT_PAYLOAD });
    post.mockResolvedValue({
      data: {
        narrative: { filed: true, case_reference: "RITO-2026-000124", claim_code: "CLAIM-XYZ-999" },
        scores: {
          stored: false,
          reason: "no_attribution",
          message: "Scores attach to a rating, which needs either the encounter reference or a named provider.",
        },
        anonymous: true,
        narrative_linked_to_rating: false,
      },
    });

    const user = userEvent.setup();
    render(<RespectfulMaternityCarePage />);

    await screen.findByText("Were you treated with respect and dignity?");
    await user.click(screen.getByTestId("rmc-answer-respect_dignity-2"));
    await user.click(screen.getByTestId("rmc-submit"));

    expect(await screen.findByTestId("rmc-scores-not-stored")).toHaveTextContent(
      /Your written report above was filed/i,
    );
    expect(screen.getByTestId("rmc-claim-code")).toHaveTextContent("CLAIM-XYZ-999");
  });

  it("shows an honest unavailable state and never falls back to a hardcoded question set", async () => {
    get.mockRejectedValue({
      status: 502,
      error: "instrument_unavailable",
      message: "The measure set could not be retrieved.",
    });

    render(<RespectfulMaternityCarePage />);

    expect(await screen.findByTestId("rmc-instrument-unavailable")).toBeInTheDocument();
    expect(screen.queryByText(/Were you treated with respect and dignity/i)).not.toBeInTheDocument();
    expect(screen.getByTestId("rmc-retry")).toBeInTheDocument();
  });
});
