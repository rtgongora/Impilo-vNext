import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NompiloAskInline } from "./NompiloAskInline";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

describe("NompiloAskInline", () => {
  beforeEach(() => {
    post.mockReset();
  });

  it("submits a question to the anonymous ask lane and renders the grounded answer + disclaimer", async () => {
    post.mockResolvedValue({
      answer: "Keep the person warm and monitor their breathing until help arrives.",
      answerSource: "llm",
      confidence: 0.9,
      sources: ["First aid basics", "Emergency care at home"],
      disclaimer: "Nompilo gives general health guidance, not a diagnosis.",
    });
    render(<NompiloAskInline />);

    fireEvent.change(screen.getByTestId("nompilo-ask-input"), {
      target: { value: "What should I do while we wait?" },
    });
    fireEvent.click(screen.getByTestId("nompilo-ask-send"));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    const [url, body] = post.mock.calls[0] as [string, Record<string, unknown>];
    expect(url).toBe("/internal/v1/public/gateway/guidance/ask");
    expect(body.question).toBe("What should I do while we wait?");

    const answerBox = await screen.findByTestId("nompilo-ask-answer");
    expect(answerBox).toHaveTextContent("Keep the person warm");
    expect(answerBox).toHaveTextContent("First aid basics");
    expect(answerBox).toHaveTextContent("not a diagnosis");
  });

  it("shows the honest don't-know answer when the honesty gate returns none", async () => {
    post.mockResolvedValue({
      answer:
        "I don't have specific information about that topic yet. Please consult your healthcare provider for personalized advice.",
      answerSource: "none",
      confidence: 0.3,
      sources: [],
    });
    render(<NompiloAskInline />);
    fireEvent.change(screen.getByTestId("nompilo-ask-input"), {
      target: { value: "Something very obscure?" },
    });
    fireEvent.click(screen.getByTestId("nompilo-ask-send"));

    const answerBox = await screen.findByTestId("nompilo-ask-answer");
    expect(answerBox).toHaveTextContent(/don't have specific information/);
  });

  it("degrades honestly when the lane is unavailable and never blocks the journey", async () => {
    post.mockRejectedValue({ status: 502 });
    render(<NompiloAskInline />);
    fireEvent.change(screen.getByTestId("nompilo-ask-input"), {
      target: { value: "Is this safe?" },
    });
    fireEvent.click(screen.getByTestId("nompilo-ask-send"));

    expect(await screen.findByTestId("nompilo-ask-error")).toHaveTextContent(/unavailable/i);
    // Input is still usable for a retry.
    expect(screen.getByTestId("nompilo-ask-input")).toBeEnabled();
  });

  it("rate-limit answers get a distinct try-again message", async () => {
    post.mockRejectedValue({ status: 429 });
    render(<NompiloAskInline />);
    fireEvent.change(screen.getByTestId("nompilo-ask-input"), {
      target: { value: "Another question here" },
    });
    fireEvent.click(screen.getByTestId("nompilo-ask-send"));

    expect(await screen.findByTestId("nompilo-ask-error")).toHaveTextContent(/busy/i);
  });

  it("does not submit questions shorter than 3 characters", () => {
    render(<NompiloAskInline />);
    fireEvent.change(screen.getByTestId("nompilo-ask-input"), { target: { value: "ok" } });
    expect(screen.getByTestId("nompilo-ask-send")).toBeDisabled();
    fireEvent.keyDown(screen.getByTestId("nompilo-ask-input"), { key: "Enter" });
    expect(post).not.toHaveBeenCalled();
  });
});
