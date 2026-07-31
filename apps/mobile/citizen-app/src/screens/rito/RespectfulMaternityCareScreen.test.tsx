import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React, { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const serviceMocks = vi.hoisted(() => ({
  fetchInstrument: vi.fn(),
  submitRespectfulCareFeedback: vi.fn(),
}));

vi.mock("../../services/respectfulCareService", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return { ...actual, fetchInstrument: serviceMocks.fetchInstrument, submitRespectfulCareFeedback: serviceMocks.submitRespectfulCareFeedback };
});

// The shared test-setup's react-native shim (src/__tests__/setup.ts) forwards TouchableOpacity's
// `onPress` verbatim instead of wiring it to a DOM `onClick`, so a dispatched click never reaches
// the handler. This re-declares the same minimal shim (deliberately NOT calling importOriginal —
// that would resolve the real "react-native" package, which is exactly what setup.ts's shim
// exists to avoid) with the one addition this screen's Likert buttons need: onPress -> onClick.
vi.mock("react-native", async () => {
  const React = await import("react");

  const createComponent =
    (tag: string) =>
    ({ children, testID, contentContainerStyle: _contentContainerStyle, ...props }: Record<string, unknown> & { children?: React.ReactNode }) => {
      const domProps: Record<string, unknown> = { ...props };
      if (testID) domProps["data-testid"] = testID;
      delete domProps.accessibilityRole;
      delete domProps.accessibilityState;
      delete domProps.showsVerticalScrollIndicator;
      return React.createElement(tag, domProps, children);
    };

  const Pressable = ({
    children,
    testID,
    onPress,
    ...props
  }: Record<string, unknown> & { children?: React.ReactNode; onPress?: () => void }) => {
    const domProps: Record<string, unknown> = { ...props, onClick: onPress };
    if (testID) domProps["data-testid"] = testID;
    delete domProps.accessibilityRole;
    delete domProps.accessibilityState;
    return React.createElement("button", domProps, children);
  };

  return {
    View: createComponent("div"),
    Text: createComponent("span"),
    ScrollView: createComponent("div"),
    TouchableOpacity: Pressable,
    Pressable,
    StyleSheet: { create: (s: unknown) => s },
    Platform: { OS: "ios", select: (m: Record<string, unknown>) => m.ios ?? m.default },
  };
});

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    Card: ({ children }: { children: React.ReactNode }) => React.createElement("div", {}, children),
    CardHeader: ({ title }: { title: string }) => React.createElement("h2", {}, title),
    CardBody: ({ children }: { children: React.ReactNode }) => React.createElement("div", {}, children),
    Button: ({ title, onPress, disabled, testID }: { title: string; onPress: () => void; disabled?: boolean; testID?: string }) =>
      React.createElement("button", { disabled, onClick: onPress, "data-testid": testID }, title),
    Badge: ({ children, testID }: { children: React.ReactNode; testID?: string }) =>
      React.createElement("span", { "data-testid": testID }, children),
    TextField: ({ value, onChange, testID, placeholder }: { value: string; onChange: (v: string) => void; testID?: string; placeholder?: string }) =>
      React.createElement("input", {
        value,
        placeholder,
        "data-testid": testID,
        onChange: (e: { target: { value: string } }) => onChange(e.target.value),
        onInput: (e: { target: { value: string } }) => onChange(e.target.value),
      }),
    Switch: ({ value, onValueChange, testID }: { value: boolean; onValueChange: (v: boolean) => void; testID?: string }) =>
      React.createElement("input", {
        type: "checkbox",
        checked: value,
        "data-testid": testID,
        onChange: (e: { target: { checked: boolean } }) => onValueChange(e.target.checked),
      }),
    LoadingSpinner: () => React.createElement("div", { "data-testid": "rmc-loading-spinner" }),
  };
});

import { RespectfulMaternityCareScreen } from "./RespectfulMaternityCareScreen";

const INSTRUMENT = {
  instrumentId: "RESPECTFUL_MATERNITY_CARE",
  name: "Respectful maternity care experience",
  scale: { type: "LIKERT_5", low: 1, high: 5, lowMeans: "never", highMeans: "always" },
  measures: [
    { measure: "respect_dignity", prompt: "Were you treated with respect and dignity?", reverseScored: false },
    { measure: "never_left_alone", prompt: "Were you left alone when you needed someone?", reverseScored: true },
  ],
};

function render(el: React.ReactElement) {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => root.render(el));
  return { container, root };
}
function testId(c: HTMLElement, id: string): HTMLElement {
  const node = c.querySelector(`[data-testid="${id}"]`);
  if (!node) throw new Error(`missing ${id}`);
  return node as HTMLElement;
}
function queryTestId(c: HTMLElement, id: string): HTMLElement | null {
  return c.querySelector(`[data-testid="${id}"]`);
}
function type(input: HTMLElement, value: string) {
  act(() => {
    (input as HTMLInputElement).value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}
function click(el: HTMLElement) {
  act(() => el.dispatchEvent(new MouseEvent("click", { bubbles: true })));
}
async function flush() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("RespectfulMaternityCareScreen", () => {
  let mounted: { root: Root; container: HTMLElement };

  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    act(() => mounted?.root.unmount());
  });

  it("fetches the instrument and renders its real prompts (never a hardcoded question set)", async () => {
    serviceMocks.fetchInstrument.mockResolvedValue(INSTRUMENT);

    mounted = render(React.createElement(RespectfulMaternityCareScreen));
    await flush();

    expect(serviceMocks.fetchInstrument).toHaveBeenCalledTimes(1);
    expect(mounted.container.textContent).toContain("Were you treated with respect and dignity?");
    expect(mounted.container.textContent).toContain("Were you left alone when you needed someone?");
  });

  it("submits raw answers and shows the claim code prominently with a save instruction", async () => {
    serviceMocks.fetchInstrument.mockResolvedValue(INSTRUMENT);
    serviceMocks.submitRespectfulCareFeedback.mockResolvedValue({
      narrative: { filed: true, caseReference: "RITO-2026-000123", claimCode: "CLAIM-ABC-123" },
      scores: { stored: true, measuresStored: 1 },
      anonymous: true,
      narrativeLinkedToRating: false,
    });

    mounted = render(React.createElement(RespectfulMaternityCareScreen));
    await flush();

    const c = mounted.container;
    click(testId(c, "rmc-answer-respect_dignity-5"));
    type(testId(c, "rmc-narrative"), "It went well");
    click(testId(c, "rmc-submit"));
    await flush();

    expect(serviceMocks.submitRespectfulCareFeedback).toHaveBeenCalledWith(
      expect.objectContaining({
        answers: { respect_dignity: 5 },
        narrative: "It went well",
        identifyMe: false,
      }),
    );
    expect(testId(c, "rmc-claim-code").textContent).toBe("CLAIM-ABC-123");
    expect(c.textContent).toContain("shown only once and cannot be recovered");
  });

  it("says so honestly when scores failed to store but the narrative was still filed", async () => {
    serviceMocks.fetchInstrument.mockResolvedValue(INSTRUMENT);
    serviceMocks.submitRespectfulCareFeedback.mockResolvedValue({
      narrative: { filed: true, caseReference: "RITO-2026-000124", claimCode: "CLAIM-XYZ-999" },
      scores: { stored: false, reason: "no_attribution", message: "Scores need attribution." },
      anonymous: true,
      narrativeLinkedToRating: false,
    });

    mounted = render(React.createElement(RespectfulMaternityCareScreen));
    await flush();

    const c = mounted.container;
    click(testId(c, "rmc-answer-respect_dignity-2"));
    click(testId(c, "rmc-submit"));
    await flush();

    expect(testId(c, "rmc-scores-not-stored").textContent).toContain("Your written report above was filed");
    expect(testId(c, "rmc-claim-code").textContent).toBe("CLAIM-XYZ-999");
  });

  it("shows an honest unavailable state and never falls back to a hardcoded question set", async () => {
    serviceMocks.fetchInstrument.mockRejectedValue(new Error("instrument_unavailable"));

    mounted = render(React.createElement(RespectfulMaternityCareScreen));
    await flush();

    const c = mounted.container;
    expect(testId(c, "rmc-instrument-unavailable")).toBeTruthy();
    expect(c.textContent).not.toContain("Were you treated with respect and dignity?");
    expect(queryTestId(c, "rmc-answer-respect_dignity-5")).toBeNull();
    expect(testId(c, "rmc-retry")).toBeTruthy();
  });
});
