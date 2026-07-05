import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SessionModesPage from "./page";
import { CLINICAL_SESSION_MODES } from "@/lib/telemedicine/session-modes";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("/work/telemedicine/session-modes", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockImplementation(async (path: string) => {
      if (path === "/internal/v1/session-templates") {
        return { data: ["TELEMEDICINE", "MEETING", "LIVE_EVENT", "LEARNING_LIVE", "LEARNING_RECORDING"] };
      }
      if (path.startsWith("/internal/v1/session-templates/")) {
        return {
          sessionMode: "TELEMEDICINE",
          owningService: "PCT",
          joinFlow: "LOBBY_THEN_ADMIT",
          maxParticipants: 8,
          auditDepth: "CLINICAL",
          recording: { consentRequired: true },
          khulumaNotificationKeys: ["rtc.telemedicine.patient-waiting"],
        };
      }
      return { data: null };
    });
  });

  it("renders all ten clinical session modes", () => {
    renderWithQuery(<SessionModesPage />);
    for (const mode of CLINICAL_SESSION_MODES) {
      expect(screen.getAllByText(mode.name).length).toBeGreaterThan(0);
    }
  });

  it("shows the live template registry modes", async () => {
    renderWithQuery(<SessionModesPage />);
    expect(await screen.findByText("LEARNING_LIVE")).toBeInTheDocument();
    expect(screen.getAllByText("TELEMEDICINE").length).toBeGreaterThan(0);
  });

  it("surfaces the real backing template with Khuluma notification keys", async () => {
    renderWithQuery(<SessionModesPage />);
    // Default selection is Direct Clinical Encounter Mode (backed by TELEMEDICINE)
    expect(await screen.findByText("rtc.telemedicine.patient-waiting")).toBeInTheDocument();
    expect(screen.getByText("Owning service")).toBeInTheDocument();
  });

  it("declares honest gaps for planned modes and never defaults them to full identity", async () => {
    const user = userEvent.setup();
    renderWithQuery(<SessionModesPage />);

    await user.click(screen.getByRole("button", { name: /audit \/ mortality review mode/i }));
    expect(screen.getByText(/honest gap:/i)).toBeInTheDocument();
    expect(screen.getAllByText("De-identified").length).toBeGreaterThan(0);
    expect(screen.getByText(/no audit-session model/i)).toBeInTheDocument();
  });

  it("shows privacy badges driven by the governance matrix", async () => {
    const user = userEvent.setup();
    renderWithQuery(<SessionModesPage />);

    await user.click(screen.getByRole("button", { name: /emergency advisory mode/i }));
    expect(screen.getAllByText("Emergency access").length).toBeGreaterThan(0);
  });

  it("explains all six identity-visibility levels with honest enforcement status", () => {
    renderWithQuery(<SessionModesPage />);

    expect(screen.getByText(/identity visibility & privacy badges/i)).toBeInTheDocument();
    for (const label of [
      "Full identity",
      "Care-team limited",
      "Pseudonymised",
      "De-identified",
      "Emergency access",
      "Sensitive / restricted case",
    ]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }
    // None of the levels is policy-enforced yet — every card must say so
    expect(screen.getAllByText(/declared only — not yet enforced/i)).toHaveLength(6);
    expect(screen.queryByText(/^policy-enforced$/)).not.toBeInTheDocument();
  });
});
