import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EmergencyTriagePanel } from "./EmergencyTriagePanel";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;
const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

describe("EmergencyTriagePanel", () => {
  beforeEach(() => {
    post.mockReset();
    get.mockReset();
    get.mockResolvedValue({ results: [] });
    window.sessionStorage.clear();
  });

  it("asks one question at a time, starting with the situation", async () => {
    render(<EmergencyTriagePanel />);
    expect(await screen.findByText(/What has happened/)).toBeInTheDocument();
    // Only the situation options are offered at this point.
    expect(screen.getByTestId("triage-opt-MEDICAL")).toBeInTheDocument();
    expect(screen.queryByTestId("triage-opt-yes")).not.toBeInTheDocument();
  });

  it("escalates immediately on a danger sign and shortens the flow to callback", async () => {
    render(<EmergencyTriagePanel />);
    fireEvent.click(await screen.findByTestId("triage-opt-TRAUMA"));
    fireEvent.click(await screen.findByTestId("triage-opt-no")); // unconscious

    // Danger banner with call actions + safe first-aid guidance appears at once.
    expect(await screen.findByTestId("triage-danger")).toBeInTheDocument();
    expect(screen.getByText(/Call for help now/)).toBeInTheDocument();
    expect(screen.getByText(/recovery position/)).toBeInTheDocument();

    // Continue the shortened flow: breathing → bleeding → red flags → callback.
    fireEvent.click(await screen.findByTestId("triage-opt-not_breathing"));
    fireEvent.click(await screen.findByTestId("triage-opt-none"));
    fireEvent.click(await screen.findByTestId("triage-multi-continue"));

    // No onset/age questioning — straight to the callback number.
    expect(await screen.findByTestId("triage-callback")).toBeInTheDocument();
    expect(screen.queryByText(/When did it start/)).not.toBeInTheDocument();
  });

  it("completes a calm full flow and submits the structured summary to the real SOS lane", async () => {
    post.mockResolvedValue({ requestReference: "SOS-20260720-XYZ01" });
    render(<EmergencyTriagePanel />);

    fireEvent.click(await screen.findByTestId("triage-opt-MEDICAL"));
    fireEvent.click(await screen.findByTestId("triage-opt-yes"));
    fireEvent.click(await screen.findByTestId("triage-opt-normal"));
    fireEvent.click(await screen.findByTestId("triage-opt-none")); // bleeding: none
    // red flags: none of these
    fireEvent.click(await screen.findByTestId("triage-opt-none"));
    fireEvent.click(screen.getByTestId("triage-multi-continue"));
    fireEvent.click(await screen.findByTestId("triage-opt-today"));
    fireEvent.click(await screen.findByTestId("triage-opt-adult"));
    fireEvent.click(await screen.findByTestId("triage-opt-no")); // pregnancy: no
    // conditions: none
    fireEvent.click(await screen.findByTestId("triage-opt-none"));
    fireEvent.click(screen.getByTestId("triage-multi-continue"));
    fireEvent.click(await screen.findByTestId("triage-opt-no")); // someone present

    // Callback number with inline validation.
    fireEvent.change(await screen.findByTestId("triage-callback"), {
      target: { value: "123" },
    });
    fireEvent.click(screen.getByTestId("triage-callback-continue"));
    expect(await screen.findByTestId("triage-callback-err")).toBeInTheDocument();
    fireEvent.change(screen.getByTestId("triage-callback"), {
      target: { value: "0771 234 567" },
    });
    fireEvent.click(screen.getByTestId("triage-callback-continue"));

    // Location: manual description.
    fireEvent.change(await screen.findByTestId("triage-location-text"), {
      target: { value: "Mbare Musika, near the bus rank" },
    });
    fireEvent.click(screen.getByTestId("triage-location-continue"));

    // Review: structured summary shown, then submit.
    expect(await screen.findByTestId("triage-summary")).toHaveTextContent(
      "Nompilo guided triage",
    );
    fireEvent.click(screen.getByTestId("triage-submit"));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    const [url, body] = post.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe("/internal/v1/public/gateway/sos");
    expect(body.emergencyCategory).toBe("MEDICAL");
    expect(body.callbackNumber).toBe("0771 234 567");
    expect(String(body.description)).toContain("Standard — no danger signs reported");

    // Honest receipt: reference + timeline + track link, no dispatch claim.
    expect(await screen.findByTestId("triage-receipt")).toBeInTheDocument();
    expect(screen.getByTestId("triage-reference")).toHaveTextContent("SOS-20260720-XYZ01");
    expect(screen.getByTestId("sos-status-timeline")).toBeInTheDocument();
    expect(screen.getByTestId("triage-track-link")).toBeInTheDocument();
  });

  it("keeps the conversation across unmount/remount (sessionStorage persistence)", async () => {
    const first = render(<EmergencyTriagePanel />);
    fireEvent.click(await screen.findByTestId("triage-opt-TRAUMA"));
    fireEvent.click(await screen.findByTestId("triage-opt-yes"));
    first.unmount();

    render(<EmergencyTriagePanel />);
    // Restored past the first two questions: breathing question is active.
    expect(await screen.findByTestId("triage-opt-normal")).toBeInTheDocument();
    expect(screen.getByText("Injury or accident")).toBeInTheDocument();
  });

  it("shows an honest failure message and keeps state when submission fails", async () => {
    post.mockRejectedValue({ status: 503 });
    render(<EmergencyTriagePanel />);
    fireEvent.click(await screen.findByTestId("triage-opt-CARDIAC")); // instant danger
    fireEvent.click(await screen.findByTestId("triage-opt-yes"));
    fireEvent.click(await screen.findByTestId("triage-opt-normal"));
    fireEvent.click(await screen.findByTestId("triage-opt-none"));
    fireEvent.click(await screen.findByTestId("triage-opt-none"));
    fireEvent.click(screen.getByTestId("triage-multi-continue"));
    fireEvent.change(await screen.findByTestId("triage-callback"), {
      target: { value: "0771 234 567" },
    });
    fireEvent.click(screen.getByTestId("triage-callback-continue"));
    fireEvent.change(await screen.findByTestId("triage-location-text"), {
      target: { value: "Harare CBD" },
    });
    fireEvent.click(screen.getByTestId("triage-location-continue"));
    fireEvent.click(await screen.findByTestId("triage-submit"));

    expect(await screen.findByTestId("triage-submit-error")).toHaveTextContent(/call the numbers/i);
    // Still on review — the user can retry without losing anything.
    expect(screen.getByTestId("triage-submit")).toBeInTheDocument();
  });
});
