import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import { NompiloAdaptiveGuidance } from "./NompiloAdaptiveGuidance";

/** matchMedia is not implemented in jsdom; default to "no reduced-motion preference". */
function mockReducedMotion(matches: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

const CARD = "nompilo-guidance-card";
const PILL = "nompilo-guidance-pill";

describe("NompiloAdaptiveGuidance", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockReducedMotion(false);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("collapses to an expandable pill after the reading period, and reopens", () => {
    render(<NompiloAdaptiveGuidance contextMessage="Sign in to continue your booking." />);
    expect(screen.getByTestId(CARD)).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(8000);
    });
    expect(screen.queryByTestId(CARD)).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId(PILL));
    expect(screen.getByTestId(CARD)).toBeInTheDocument();
  });

  it("does NOT collapse while focus is inside it (a11y: never yank guidance from a keyboard/AT user)", () => {
    render(<NompiloAdaptiveGuidance contextMessage="Guidance text." />);
    fireEvent.focus(screen.getByLabelText("Minimise guidance"));

    act(() => {
      vi.advanceTimersByTime(20000);
    });
    expect(screen.getByTestId(CARD)).toBeInTheDocument();
  });

  it("does NOT auto-collapse when the user prefers reduced motion", () => {
    mockReducedMotion(true);
    render(<NompiloAdaptiveGuidance contextMessage="Guidance text." />);

    act(() => {
      vi.advanceTimersByTime(20000);
    });
    expect(screen.getByTestId(CARD)).toBeInTheDocument();
  });

  it("advisory mode never auto-collapses — it is used for guidance a person must not miss", () => {
    render(<NompiloAdaptiveGuidance mode="advisory" contextMessage="Anonymous reports cannot be followed up." />);

    act(() => {
      vi.advanceTimersByTime(30000);
    });
    expect(screen.getByTestId(CARD)).toBeInTheDocument();
  });

  it("advisory mode is still dismissible by the user", () => {
    render(<NompiloAdaptiveGuidance mode="advisory" contextMessage="Guidance text." />);
    fireEvent.click(screen.getByLabelText("Dismiss guidance"));
    expect(screen.queryByTestId(CARD)).not.toBeInTheDocument();
    expect(screen.queryByTestId(PILL)).not.toBeInTheDocument();
  });

  it("uses a journey-specific collapsed label when given", () => {
    render(
      <NompiloAdaptiveGuidance
        contextMessage="Guidance text."
        collapsedLabel="What can this search tell me?"
      />,
    );
    act(() => {
      vi.advanceTimersByTime(8000);
    });
    expect(screen.getByTestId(PILL)).toHaveTextContent("What can this search tell me?");
  });

  it("renders its suggestions", () => {
    render(
      <NompiloAdaptiveGuidance
        contextMessage="Guidance text."
        suggestions={["Opening hours are not a live signal.", "Keep your claim code."]}
      />,
    );
    expect(screen.getByText("Opening hours are not a live signal.")).toBeInTheDocument();
    expect(screen.getByText("Keep your claim code.")).toBeInTheDocument();
  });
});
