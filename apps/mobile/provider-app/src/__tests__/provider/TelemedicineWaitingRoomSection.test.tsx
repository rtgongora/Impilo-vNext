/**
 * TelemedicineScreen waiting-room section tests — waiting participants render
 * per active session and Admit/Deny call the governed teleconsult actions.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const serviceMocks = vi.hoisted(() => ({
  listProviderTelemedicineSessions: vi.fn(),
  joinProviderTelemedicineSession: vi.fn(),
  endProviderTelemedicineSession: vi.fn(),
  sendProviderTelemedicineSignal: vi.fn(),
  fetchTelemedicineWaitingRoom: vi.fn(),
  admitTelemedicineParticipant: vi.fn(),
  denyTelemedicineParticipant: vi.fn(),
  requestProviderTelemedicineMediaToken: vi.fn(),
  refreshProviderTelemedicineMediaToken: vi.fn(),
}));

vi.mock("../../services/telemedicineService", () => serviceMocks);

vi.mock("@impilo/mobile-auth", () => ({
  useAuth: () => ({ user: { sub: "prov-1", given_name: "Dr", family_name: "Moyo", preferred_username: "drmoyo" } }),
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: () => ({ facilityId: "fac-1" }),
}));

vi.mock("@impilo/mobile-messaging", () => ({
  useChannel: () => ({ status: "CONNECTED", messages: [], isConnected: true }),
}));

vi.mock("@impilo/mobile-session", async () => {
  const React = await import("react");
  return {
    AdaptiveSessionRoomNative: () => React.createElement("div", { "data-testid": "session-room-stage" }),
  };
});

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    Screen: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Header: ({ title }: { title: string }) => React.createElement("header", null, title),
    Card: ({ children }: { children: React.ReactNode }) => React.createElement("section", null, children),
    CardBody: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Badge: ({ children }: { children: React.ReactNode }) => React.createElement("span", null, children),
    LoadingSpinner: () => React.createElement("span", null, "loading"),
    EmptyState: ({ title }: { title: string }) => React.createElement("div", null, title),
    ErrorState: ({ message }: { message: string }) => React.createElement("div", null, message),
    Button: ({ title, onPress, disabled, testID }: { title: string; onPress: () => void; disabled?: boolean; testID?: string }) =>
      React.createElement("button", { disabled, onClick: onPress, "data-testid": testID }, title),
    TextField: ({ value, onChange, testID }: { value: string; onChange: (next: string) => void; testID?: string }) =>
      React.createElement("input", {
        value,
        "data-testid": testID,
        onChange: (event: { target: { value: string } }) => onChange(event.target.value),
      }),
  };
});

import { TelemedicineScreen } from "../../screens/provider/TelemedicineScreen";

const scheduledSession = {
  id: "session-1",
  encounterId: "enc-1",
  patientId: "pat-1",
  providerId: "prov-1",
  status: "SCHEDULED" as const,
  scheduledAt: "2026-07-05T09:00:00Z",
};

let root: Root | undefined;
let container: HTMLDivElement | undefined;

function renderScreen(element: React.ReactElement) {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => {
    root!.render(element);
  });
  return container;
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

function byTestId(node: HTMLElement, id: string): HTMLElement {
  const found = node.querySelector(`[data-testid="${id}"]`);
  if (!found) throw new Error(`Missing testID: ${id}`);
  return found as HTMLElement;
}

describe("TelemedicineScreen waiting room", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serviceMocks.listProviderTelemedicineSessions.mockResolvedValue([scheduledSession]);
    serviceMocks.fetchTelemedicineWaitingRoom.mockResolvedValue([
      { identity: "cpid-1", displayName: "Thandi M", waitingSince: "2026-07-05T08:55:00Z" },
    ]);
    serviceMocks.admitTelemedicineParticipant.mockResolvedValue(undefined);
    serviceMocks.denyTelemedicineParticipant.mockResolvedValue(undefined);
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container?.remove();
    root = undefined;
    container = undefined;
  });

  it("renders the waiting room with the waiting participant", async () => {
    const el = renderScreen(<TelemedicineScreen />);
    await flush(); // sessions load
    await flush(); // waiting-room refresh

    expect(serviceMocks.fetchTelemedicineWaitingRoom).toHaveBeenCalledWith("session-1");
    expect(el.querySelector('[data-testid="telemedicine-waiting-room"]')).toBeTruthy();
    expect(el.textContent).toContain("Thandi M");
  });

  it("admits a waiting participant via the governed admit action", async () => {
    const el = renderScreen(<TelemedicineScreen />);
    await flush();
    await flush();

    act(() => {
      byTestId(el, "telemedicine-admit-button").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(serviceMocks.admitTelemedicineParticipant).toHaveBeenCalledWith("session-1", "cpid-1");
  });

  it("denies a waiting participant with a reason", async () => {
    const el = renderScreen(<TelemedicineScreen />);
    await flush();
    await flush();

    act(() => {
      byTestId(el, "telemedicine-deny-button").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(serviceMocks.denyTelemedicineParticipant).toHaveBeenCalledWith(
      "session-1",
      "cpid-1",
      "Provider declined admission"
    );
  });

  it("navigates to the call screen on join", async () => {
    serviceMocks.joinProviderTelemedicineSession.mockResolvedValue({
      ...scheduledSession,
      status: "IN_PROGRESS",
      sessionToken: "join-token",
      roomUrl: "wss://rtc.impilo.zw/join",
      channelId: "chan-1",
    });
    serviceMocks.requestProviderTelemedicineMediaToken.mockResolvedValue({ status: "WAITING" });

    const el = renderScreen(<TelemedicineScreen />);
    await flush();
    await flush();

    act(() => {
      byTestId(el, "join-session-session-1").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(el.querySelector('[data-testid="telemedicine-call-screen"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="session-room-stage"]')).toBeTruthy();
  });
});
