import { describe, expect, it } from "vitest";
import { isSupportedLiveKitServerUrl, normalizeLiveKitServerUrl } from "./livekit-url";

describe("normalizeLiveKitServerUrl", () => {
  it("converts https room links to wss endpoint", () => {
    expect(normalizeLiveKitServerUrl("https://livekit.example/rooms/impilo-room-1")).toBe("wss://livekit.example");
  });

  it("keeps websocket endpoint unchanged", () => {
    expect(normalizeLiveKitServerUrl("wss://livekit.example")).toBe("wss://livekit.example");
  });

  it("converts plain http endpoints to ws", () => {
    expect(normalizeLiveKitServerUrl("http://livekit.local:7880")).toBe("ws://livekit.local:7880");
  });

  it("returns empty string for missing values", () => {
    expect(normalizeLiveKitServerUrl(undefined)).toBe("");
    expect(normalizeLiveKitServerUrl(null)).toBe("");
    expect(normalizeLiveKitServerUrl("   ")).toBe("");
  });
});

describe("isSupportedLiveKitServerUrl", () => {
  it("accepts ws and wss endpoints only", () => {
    expect(isSupportedLiveKitServerUrl("ws://livekit.local")).toBe(true);
    expect(isSupportedLiveKitServerUrl("wss://livekit.example")).toBe(true);
    expect(isSupportedLiveKitServerUrl("ftp://livekit.example")).toBe(false);
    expect(isSupportedLiveKitServerUrl("")).toBe(false);
  });
});
