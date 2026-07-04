import { describe, expect, it, vi, beforeEach } from "vitest";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";

// Minimal React Native shim (same approach as the app-level vitest setups).
vi.mock("react-native", async () => {
  const ReactMod = await import("react");
  const make =
    (tag: string) =>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ({ children, testID, style: _style, onPress, ...props }: any) => {
      const p: Record<string, unknown> = { ...props };
      if (testID) p["data-testid"] = testID;
      if (onPress) p.onClick = onPress;
      return ReactMod.createElement(tag, p, children);
    };
  return {
    View: make("div"),
    Text: make("span"),
    TouchableOpacity: make("button"),
    StyleSheet: { create: (s: unknown) => s },
  };
});

const tracksState = vi.hoisted(() => ({
  tracks: [] as unknown[],
  connectionState: "connected" as string,
}));

vi.mock("@livekit/react-native", async () => {
  const ReactMod = await import("react");
  return {
    registerGlobals: vi.fn(),
    LiveKitRoom: ({ children }: { children: React.ReactNode }) =>
      ReactMod.createElement("div", { "data-testid": "livekit-room" }, children),
    VideoTrack: ({ trackRef }: { trackRef?: { participant?: { identity?: string } } }) =>
      ReactMod.createElement("div", {
        "data-testid": "video-track",
        "data-participant": trackRef?.participant?.identity ?? "",
      }),
    VideoView: () => ReactMod.createElement("div", { "data-testid": "video-view" }),
    useConnectionState: () => tracksState.connectionState,
    useRoomContext: () => ({
      localParticipant: {
        setMicrophoneEnabled: vi.fn(),
        setCameraEnabled: vi.fn(),
      },
    }),
    useTracks: () => tracksState.tracks,
  };
});

vi.mock("livekit-client", () => ({
  ConnectionState: {
    Connected: "connected",
    Connecting: "connecting",
    Reconnecting: "reconnecting",
    Disconnected: "disconnected",
  },
  Track: { Source: { Camera: "camera" } },
  createLocalVideoTrack: vi.fn(async () => ({ stop: vi.fn() })),
}));

import { AdaptiveSessionRoomNative } from "../src";

function makeTrack(identity: string, isLocal: boolean, sid: string) {
  return {
    participant: { identity, isLocal },
    publication: { trackSid: sid },
    source: "camera",
  };
}

function render(props: Partial<React.ComponentProps<typeof AdaptiveSessionRoomNative>> = {}) {
  return renderToStaticMarkup(
    React.createElement(AdaptiveSessionRoomNative, {
      serverUrl: "wss://livekit.example",
      token: "jwt",
      videoEnabled: true,
      micMuted: false,
      ...props,
    }),
  );
}

describe("AdaptiveSessionRoomNative", () => {
  beforeEach(() => {
    tracksState.tracks = [];
    tracksState.connectionState = "connected";
  });

  it("renders the governed fallback when no token is available", () => {
    const html = render({ token: undefined });
    expect(html).toContain("session-room-unavailable");
    expect(html).toContain("Media room not available yet");
  });

  it("reports invalid non-websocket endpoints via onError", () => {
    const onError = vi.fn();
    const html = render({ serverUrl: "ftp://bad.example", onError });
    expect(html).toContain("session-room-invalid-endpoint");
    expect(onError).toHaveBeenCalledWith(
      "Invalid media endpoint returned by RTC Gateway. Expected ws:// or wss:// URL.",
    );
  });

  it("normalizes https room URLs and connects", () => {
    const html = render({ serverUrl: "https://livekit.example/rooms/consult-1" });
    expect(html).toContain("livekit-room");
    expect(html).toContain("session-room-stage");
  });

  it("consult layout renders remote as primary with local overlay", () => {
    tracksState.tracks = [
      makeTrack("local-me", true, "t-local"),
      makeTrack("remote-doc", false, "t-remote"),
    ];
    const html = render({ layout: "consult" });
    expect(html).toContain("session-room-consult");
    expect(html).toContain("session-room-local-overlay");
    // Primary (first video-track) is the remote participant.
    expect(html.indexOf("remote-doc")).toBeLessThan(html.indexOf("local-me"));
  });

  it("grid layout renders a tile per camera track", () => {
    tracksState.tracks = [
      makeTrack("local-me", true, "t-local"),
      makeTrack("remote-a", false, "t-a"),
      makeTrack("remote-b", false, "t-b"),
    ];
    const html = render({ layout: "grid" });
    expect(html).toContain("session-room-grid");
    expect(html.match(/data-testid="video-track"/g)?.length).toBe(3);
  });

  it("stage layout renders remote publishers only", () => {
    tracksState.tracks = [
      makeTrack("local-me", true, "t-local"),
      makeTrack("remote-speaker", false, "t-speaker"),
    ];
    const html = render({ layout: "stage" });
    expect(html).toContain("session-room-stage-publishers");
    expect(html).toContain("remote-speaker");
    expect(html).not.toContain("local-me");
  });

  it("audio-only sessions skip video rendering", () => {
    tracksState.tracks = [makeTrack("remote-doc", false, "t-remote")];
    const html = render({ audioOnly: true });
    expect(html).toContain("Audio only mode");
    expect(html).not.toContain('data-testid="video-track"');
  });

  it("shows the reconnect banner only while reconnecting and when enabled", () => {
    tracksState.connectionState = "reconnecting";
    expect(render({ showReconnectBanner: true })).toContain("session-room-reconnect-banner");
    expect(render({ showReconnectBanner: false })).not.toContain("session-room-reconnect-banner");
    tracksState.connectionState = "connected";
    expect(render({ showReconnectBanner: true })).not.toContain("session-room-reconnect-banner");
  });

  it("shows the recording badge when requested", () => {
    const html = render({ showRecordingBadge: true });
    expect(html).toContain("session-room-recording-badge");
  });
});
