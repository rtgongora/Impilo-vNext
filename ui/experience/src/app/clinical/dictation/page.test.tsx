import type { ReactNode } from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DictationPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

describe("DictationPage", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("surfaces Wave 1 honesty: no STT, empty history, and Agent 0 callout", () => {
    render(<DictationPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Voice Dictation" })).toBeInTheDocument();
    expect(
      screen.getByText(/Local recording controls only — no connected speech-to-text or dictation store yet/),
    ).toBeInTheDocument();
    expect(screen.getByText("Not a clinical transcript pipeline")).toBeInTheDocument();
    expect(
      screen.getByText(/Recent dictations are empty until a dictation or ASR API is available/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/No saved dictation history yet\. This list needs a backend dictation or notes integration\./),
    ).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/No speech-to-text is connected/)).toBeInTheDocument();
  });

  it("does not inject a fabricated transcript after stop recording", async () => {
    render(<DictationPage />);

    const textarea = screen.getByRole("textbox");
    expect(textarea).toHaveValue("");

    await act(async () => {
      fireEvent.click(screen.getAllByRole("button")[0]);
    });

    const whileRecording = screen.getAllByRole("button");
    expect(whileRecording.length).toBeGreaterThanOrEqual(2);
    await act(async () => {
      fireEvent.click(whileRecording[1]);
    });

    await act(async () => {
      vi.advanceTimersByTime(500);
    });

    expect(textarea).toHaveValue("");
    expect(screen.queryByText(/Patient presents with a two-week history/)).not.toBeInTheDocument();
  });
});
