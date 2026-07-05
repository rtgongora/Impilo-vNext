import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AdaptiveSessionRoom } from "./AdaptiveSessionRoom";

/* eslint-disable @typescript-eslint/no-explicit-any */
// Keep the LiveKit runtime out of jsdom — render structural stand-ins that
// expose the props the assertions need.
vi.mock("@livekit/components-react", () => ({
  LiveKitRoom: ({ children, serverUrl, video, audio }: any) => (
    <div
      data-testid="livekit-room"
      data-server-url={serverUrl}
      data-video={String(video)}
      data-audio={String(audio)}
    >
      {children}
    </div>
  ),
  ControlBar: ({ controls }: any) => (
    <div data-testid="control-bar" data-controls={JSON.stringify(controls)} />
  ),
  GridLayout: ({ children }: any) => <div data-testid="grid-layout">{children}</div>,
  ParticipantTile: () => <div data-testid="participant-tile" />,
  FocusLayout: () => <div data-testid="focus-layout" />,
  FocusLayoutContainer: ({ children }: any) => <div data-testid="focus-container">{children}</div>,
  CarouselLayout: ({ children }: any) => <div data-testid="carousel-layout">{children}</div>,
  RoomAudioRenderer: () => null,
  ConnectionStateToast: () => null,
  PreJoin: () => <div data-testid="prejoin" />,
  useTracks: () => [],
  useRoomContext: () => undefined,
  useConnectionState: () => "connected",
  useParticipants: () => [],
}));
/* eslint-enable @typescript-eslint/no-explicit-any */

const BASE_PROPS = {
  serverUrl: "wss://livekit.example",
  token: "scoped-token",
  videoEnabled: true,
};

function controlBarControls(): Record<string, boolean> {
  return JSON.parse(screen.getByTestId("control-bar").getAttribute("data-controls") ?? "{}");
}

describe("AdaptiveSessionRoom", () => {
  it("renders the default grid layout with publish controls", () => {
    render(<AdaptiveSessionRoom {...BASE_PROPS} />);
    expect(screen.getByTestId("livekit-room")).toBeInTheDocument();
    expect(screen.getByTestId("grid-layout")).toBeInTheDocument();
    const controls = controlBarControls();
    expect(controls.microphone).toBe(true);
    expect(controls.camera).toBe(true);
    expect(controls.leave).toBe(true);
  });

  it("audience mode hides publish controls but keeps leave", () => {
    render(
      <AdaptiveSessionRoom
        {...BASE_PROPS}
        layout="stage"
        audience
        controls={{ microphone: true, camera: true, screenShare: true, leave: true }}
      />
    );
    const controls = controlBarControls();
    expect(controls.microphone).toBe(false);
    expect(controls.camera).toBe(false);
    expect(controls.screenShare).toBe(false);
    expect(controls.leave).toBe(true);
    // Subscribe-only: no local publish at connect either.
    expect(screen.getByTestId("livekit-room").getAttribute("data-video")).toBe("false");
    expect(screen.getByTestId("livekit-room").getAttribute("data-audio")).toBe("false");
  });

  it("consult layout with pip policy renders the local PiP container", () => {
    render(<AdaptiveSessionRoom {...BASE_PROPS} layout="consult" localPreviewPolicy="pip" />);
    expect(screen.getByTestId("consult-stage")).toBeInTheDocument();
    expect(screen.getByTestId("session-local-pip")).toBeInTheDocument();
  });

  it("never publishes at connect — the room mounts silent and PostConnectPublisher owns enabling", () => {
    // Publishing during connect stalls publisher negotiation (15s
    // NegotiationError churn); tracks are enabled only after RoomEvent.Connected.
    render(<AdaptiveSessionRoom {...BASE_PROPS} audioOnly />);
    expect(screen.getByTestId("livekit-room").getAttribute("data-video")).toBe("false");
    expect(screen.getByTestId("livekit-room").getAttribute("data-audio")).toBe("false");
  });

  it("recording badge is hidden when the room is not recording", () => {
    render(<AdaptiveSessionRoom {...BASE_PROPS} />);
    expect(screen.queryByTestId("session-recording-badge")).not.toBeInTheDocument();
    expect(screen.queryByText("Recording")).not.toBeInTheDocument();
  });

  it("shows waiting state without credentials", () => {
    render(<AdaptiveSessionRoom {...BASE_PROPS} token="" />);
    expect(
      screen.getByText("Waiting for governed LiveKit server URL and scoped media token.")
    ).toBeInTheDocument();
    expect(screen.queryByTestId("livekit-room")).not.toBeInTheDocument();
  });

  it("reports invalid non-websocket endpoints via onError", async () => {
    const onError = vi.fn();
    render(<AdaptiveSessionRoom {...BASE_PROPS} serverUrl="ftp://bad.endpoint" onError={onError} />);
    expect(
      screen.getByText("Governed media endpoint is invalid. Use async clinical workflow and alert platform support.")
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(onError).toHaveBeenCalledWith(
        "Invalid LiveKit server URL. Expected ws:// or wss:// endpoint from RTC Gateway."
      )
    );
  });
});

