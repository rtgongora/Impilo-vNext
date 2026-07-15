import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

import { TraumaSurveyPanel } from "./TraumaSurveyPanel";

const VISIT = "v-123";
const TID = "t-123";

function renderPanel(surveys: Array<Record<string, unknown>> = []) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TraumaSurveyPanel visitId={VISIT} traumaId={TID} surveys={surveys} />
    </QueryClientProvider>,
  );
}

describe("TraumaSurveyPanel", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    post.mockResolvedValue({ data: {} });
  });

  it("explains the empty state", () => {
    renderPanel();
    expect(screen.getByText(/No surveys recorded yet/i)).toBeInTheDocument();
  });

  it("records a primary A survey with checklist to the real endpoint", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderPanel();
    await user.click(screen.getByLabelText("Airway patent"));
    await user.click(screen.getByRole("button", { name: /Record primary A survey/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/visits/${VISIT}/trauma/survey`,
        expect.objectContaining({
          surveyType: "PRIMARY",
          sectionKey: "A",
          checklist: expect.objectContaining({ "Airway patent": true }),
        }),
      ),
    );
  });

  it("switches to a secondary survey and records it", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderPanel();
    await user.click(screen.getByRole("button", { name: /Secondary \(head-to-toe\)/i }));
    await user.click(screen.getByLabelText("Abdomen"));
    await user.click(screen.getByRole("button", { name: /Record secondary survey/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/visits/${VISIT}/trauma/survey`,
        expect.objectContaining({ surveyType: "SECONDARY", sectionKey: "SECONDARY" }),
      ),
    );
  });

  it("amends an existing survey (carries amendsSurveyId)", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderPanel([{ id: "s1", survey_type: "PRIMARY", section_key: "C", status: "RECORDED", checklist: { "Pulses present": true } }]);
    await user.click(screen.getByRole("button", { name: /Amend/i }));
    await user.click(screen.getByRole("button", { name: /Save amendment/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/visits/${VISIT}/trauma/survey`,
        expect.objectContaining({ amendsSurveyId: "s1", surveyType: "PRIMARY", sectionKey: "C" }),
      ),
    );
  });
});
