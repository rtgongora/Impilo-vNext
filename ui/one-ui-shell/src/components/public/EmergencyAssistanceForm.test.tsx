import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EmergencyAssistanceForm } from "./EmergencyAssistanceForm";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

describe("EmergencyAssistanceForm", () => {
  beforeEach(() => {
    post.mockReset();
  });

  it("requires a callback number before it will submit", async () => {
    render(<EmergencyAssistanceForm />);
    fireEvent.click(screen.getByTestId("sos-submit"));

    expect(await screen.findByTestId("sos-callback-error")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it("posts to the anonymous SOS lane and shows the reference + callback reassurance", async () => {
    post.mockResolvedValue({
      requestReference: "SOS-20260712-ABCDE",
      message: "We will call you back on the number you provided to confirm before help is dispatched.",
    });
    render(<EmergencyAssistanceForm />);

    fireEvent.change(screen.getByTestId("sos-callback"), { target: { value: "0771 234 567" } });
    fireEvent.change(screen.getByTestId("sos-category"), { target: { value: "TRAUMA" } });
    fireEvent.change(screen.getByTestId("sos-location"), { target: { value: "Mbare Musika" } });
    fireEvent.click(screen.getByTestId("sos-submit"));

    expect(await screen.findByTestId("sos-success")).toBeInTheDocument();
    expect(screen.getByTestId("sos-reference")).toHaveTextContent("SOS-20260712-ABCDE");
    expect(screen.getByText(/call you back/i)).toBeInTheDocument();
    // Life-threatening reminder to call directly is still shown.
    expect(screen.getByText(/life-threatening/i)).toBeInTheDocument();

    expect(post).toHaveBeenCalledWith("/internal/v1/public/gateway/sos", {
      callbackNumber: "0771 234 567",
      emergencyCategory: "TRAUMA",
      locationDescription: "Mbare Musika",
      description: undefined,
    });
  });

  it("shows a busy message and keeps pointing to the call numbers on a 429", async () => {
    post.mockRejectedValue({ status: 429 });
    render(<EmergencyAssistanceForm />);

    fireEvent.change(screen.getByTestId("sos-callback"), { target: { value: "+263771234567" } });
    fireEvent.click(screen.getByTestId("sos-submit"));

    await waitFor(() => expect(screen.getByTestId("sos-error")).toBeInTheDocument());
    expect(screen.getByTestId("sos-error")).toHaveTextContent(/very busy/i);
  });

  it("surfaces an invalid-number message on a 400", async () => {
    post.mockRejectedValue({ status: 400 });
    render(<EmergencyAssistanceForm />);

    fireEvent.change(screen.getByTestId("sos-callback"), { target: { value: "123" } });
    fireEvent.click(screen.getByTestId("sos-submit"));

    await waitFor(() => expect(screen.getByTestId("sos-callback-error")).toBeInTheDocument());
    expect(screen.getByTestId("sos-callback-error")).toHaveTextContent(/valid phone number/i);
  });
});
