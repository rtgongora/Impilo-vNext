/**
 * PrehospitalEpcrScreen — responder ePCR capture: load mission, capture a timed observation,
 * and surface the offline pending-sync indicator. Interim proof for J-TR-M1 (the Maestro flow
 * authored under apps/mobile/maestro/flows needs an emulator/device to run).
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import React, { act } from "react";
import { createRoot } from "react-dom/client";

const mockGetMission = vi.fn();
const mockGetMissionForIncident = vi.fn();
const mockRecordObs = vi.fn();
const mockUpsertEpcr = vi.fn();
const mockAdvanceMission = vi.fn();
let pendingCount = 0;

vi.mock("../../services/epcrService", () => ({
  getMission: (...a: unknown[]) => mockGetMission(...a),
  getMissionForIncident: (...a: unknown[]) => mockGetMissionForIncident(...a),
  recordObs: (...a: unknown[]) => mockRecordObs(...a),
  upsertEpcr: (...a: unknown[]) => mockUpsertEpcr(...a),
  advanceMission: (...a: unknown[]) => mockAdvanceMission(...a),
}));

vi.mock("@impilo/mobile-offline", () => ({
  useSyncEngine: () => ({ pendingCount }),
}));

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  Card: ({ children }: any) => React.createElement("div", null, children),
  CardHeader: ({ title }: any) => React.createElement("h3", null, title),
  CardBody: ({ children }: any) => React.createElement("div", null, children),
  Button: ({ title, onPress, testID }: any) =>
    React.createElement("button", { "data-testid": testID, onClick: onPress }, title),
  TextField: ({ value, onChangeText, testID }: any) =>
    React.createElement("input", {
      "data-testid": testID,
      value: value ?? "",
      onChange: (e: { target: { value: string } }) => onChangeText?.(e.target.value),
    }),
  Badge: ({ label, testID }: any) => React.createElement("span", { "data-testid": testID }, label),
  };
});

async function render(el: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(el);
  });
  return { container, root };
}

async function type(container: HTMLElement, testId: string, value: string) {
  const input = container.querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
  await act(async () => {
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!.call(input, value);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

async function click(container: HTMLElement, testId: string) {
  const el = container.querySelector(`[data-testid="${testId}"]`) as HTMLElement;
  await act(async () => {
    el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

describe("PrehospitalEpcrScreen", () => {
  beforeEach(() => {
    mockGetMission.mockReset();
    mockGetMissionForIncident.mockReset();
    mockRecordObs.mockReset();
    mockUpsertEpcr.mockReset();
    mockAdvanceMission.mockReset();
    pendingCount = 0;
  });

  it("renders the ePCR capture entry point", async () => {
    const { PrehospitalEpcrScreen } = await import("../../screens/provider/PrehospitalEpcrScreen");
    const { container } = await render(React.createElement(PrehospitalEpcrScreen));
    expect(container.querySelector('[data-testid="epcr-screen"]')).toBeTruthy();
    expect(container.querySelector('[data-testid="epcr-load-mission"]')).toBeTruthy();
    // Capture controls only appear once a mission is loaded.
    expect(container.querySelector('[data-testid="epcr-capture-vitals"]')).toBeFalsy();
  });

  it("loads the mission for an incident then captures a vitals observation", async () => {
    mockGetMissionForIncident.mockResolvedValue({ id: "m-1", missionReference: "EMS-1", state: "EN_ROUTE_FACILITY" });
    mockRecordObs.mockResolvedValue({ id: "obs-1", queued: false });
    const { PrehospitalEpcrScreen } = await import("../../screens/provider/PrehospitalEpcrScreen");
    const { container } = await render(React.createElement(PrehospitalEpcrScreen));

    await type(container, "epcr-incident-input", "inc-1");
    await click(container, "epcr-load-mission");
    expect(mockGetMissionForIncident).toHaveBeenCalledWith("inc-1");

    // Mission loaded → capture controls now render.
    expect(container.querySelector('[data-testid="epcr-capture-vitals"]')).toBeTruthy();
    await type(container, "epcr-hr", "120");
    await type(container, "epcr-sbp", "90");
    await click(container, "epcr-capture-vitals");

    expect(mockRecordObs).toHaveBeenCalledWith(
      expect.objectContaining({ missionId: "m-1", eventType: "VITALS", payload: expect.objectContaining({ hr: 120, sbp: 90 }) })
    );
    // The captured obs shows in the log.
    expect(container.querySelector('[data-testid="epcr-obs-0"]')).toBeTruthy();
  });

  it("shows the pending-sync badge when observations are queued offline", async () => {
    pendingCount = 3;
    const { PrehospitalEpcrScreen } = await import("../../screens/provider/PrehospitalEpcrScreen");
    const { container } = await render(React.createElement(PrehospitalEpcrScreen));
    const badge = container.querySelector('[data-testid="epcr-pending-badge"]');
    expect(badge?.textContent).toContain("pending sync");
  });
});
