import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PatientWaitingRoom } from "./PatientWaitingRoom";

const { mutateAsync, realtimeHandlers } = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
  realtimeHandlers: [] as Array<(event: { type: string; sessionRef: string; payload: Record<string, unknown> }) => void>,
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineMediaToken: () => ({ mutateAsync, isPending: false }),
}));

vi.mock("@/hooks/useSessionRealtime", () => ({
  useSessionRealtime: ({
    enabled,
    onEvent,
  }: {
    enabled?: boolean;
    onEvent: (event: { type: string; sessionRef: string; payload: Record<string, unknown> }) => void;
  }) => {
    if (enabled) realtimeHandlers.push(onEvent);
  },
}));

vi.mock("@/components/session/PreJoinPanel", () => ({
  PreJoinPanel: ({ joinLabel, onSubmit }: { joinLabel?: string; onSubmit?: (values: unknown) => void }) => (
    <button type="button" onClick={() => onSubmit?.({})}>
      {joinLabel ?? "Join session"}
    </button>
  ),
}));

describe("PatientWaitingRoom", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    realtimeHandlers.length = 0;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the waiting message on WAITING and keeps polling until a token flips the parent", async () => {
    vi.useFakeTimers();
    mutateAsync
      .mockResolvedValueOnce({ data: { status: "WAITING", sessionId: "s-1", identity: "pat-1" } })
      .mockResolvedValue({ data: { room_url: "wss://livekit.test", token: "tok-1", mediaProfile: "FULL" } });
    const onAdmitted = vi.fn();

    render(
      <PatientWaitingRoom sessionId="s-1" displayName="Tatenda" consentGranted onAdmitted={onAdmitted} />,
    );

    fireEvent.click(screen.getByRole("button", { name: /i'm ready — join the visit/i }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(mutateAsync).toHaveBeenCalledWith({ sessionId: "s-1", displayName: "Tatenda", role: "PATIENT" });
    expect(screen.getByTestId("patient-waiting-message")).toBeInTheDocument();
    expect(screen.getByText(/the care team knows you're here/i)).toBeInTheDocument();
    expect(onAdmitted).not.toHaveBeenCalled();

    // Next 5s poll returns the grant.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });

    expect(mutateAsync).toHaveBeenCalledTimes(2);
    expect(onAdmitted).toHaveBeenCalledWith({
      roomUrl: "wss://livekit.test",
      token: "tok-1",
      mediaProfile: "FULL",
    });
  });

  it("requests a token immediately when the session.admitted realtime event lands", async () => {
    mutateAsync
      .mockResolvedValueOnce({ data: { status: "WAITING", sessionId: "s-1", identity: "pat-1" } })
      .mockResolvedValue({ data: { room_url: "wss://livekit.test", token: "tok-2" } });
    const onAdmitted = vi.fn();

    render(
      <PatientWaitingRoom sessionId="s-1" displayName="Tatenda" consentGranted onAdmitted={onAdmitted} />,
    );

    fireEvent.click(screen.getByRole("button", { name: /i'm ready — join the visit/i }));
    await screen.findByTestId("patient-waiting-message");

    expect(realtimeHandlers.length).toBeGreaterThan(0);
    act(() => {
      realtimeHandlers.at(-1)?.({ type: "session.admitted", sessionRef: "s-1", payload: {} });
    });

    await waitFor(() =>
      expect(onAdmitted).toHaveBeenCalledWith({
        roomUrl: "wss://livekit.test",
        token: "tok-2",
        mediaProfile: undefined,
      }),
    );
  });

  it("shows a kind message when the join is DENIED and stops there", async () => {
    mutateAsync.mockResolvedValue({ data: { status: "DENIED", sessionId: "s-1", identity: "pat-1" } });
    const onAdmitted = vi.fn();

    render(
      <PatientWaitingRoom sessionId="s-1" displayName="Tatenda" consentGranted={false} onAdmitted={onAdmitted} />,
    );

    fireEvent.click(screen.getByRole("button", { name: /i'm ready — join the visit/i }));

    expect(await screen.findByTestId("patient-waiting-denied")).toBeInTheDocument();
    expect(screen.getByText(/we're sorry/i)).toBeInTheDocument();
    expect(onAdmitted).not.toHaveBeenCalled();
  });
});
