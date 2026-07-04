import { describe, expect, it } from "vitest";
import { isSupportedLiveKitServerUrl, normalizeLiveKitServerUrl } from "../src/normalizeLiveKitServerUrl";

describe("normalizeLiveKitServerUrl", () => {
  it("normalizes browser room URLs to websocket server URLs", () => {
    expect(normalizeLiveKitServerUrl("https://livekit.example/rooms/test-room")).toBe("wss://livekit.example");
  });

  it("strips room paths with query strings and fragments", () => {
    expect(normalizeLiveKitServerUrl("https://livekit.example/rooms/abc?token=x#top")).toBe("wss://livekit.example");
  });

  it("rewrites plain http to ws", () => {
    expect(normalizeLiveKitServerUrl("http://livekit.local:7880")).toBe("ws://livekit.local:7880");
  });

  it("preserves websocket URLs", () => {
    expect(normalizeLiveKitServerUrl("wss://livekit.example")).toBe("wss://livekit.example");
    expect(normalizeLiveKitServerUrl("ws://livekit:7880")).toBe("ws://livekit:7880");
  });

  it("trims surrounding whitespace", () => {
    expect(normalizeLiveKitServerUrl("  wss://livekit.example  ")).toBe("wss://livekit.example");
  });

  it("returns empty string for missing values", () => {
    expect(normalizeLiveKitServerUrl(undefined)).toBe("");
    expect(normalizeLiveKitServerUrl(null)).toBe("");
    expect(normalizeLiveKitServerUrl("   ")).toBe("");
  });
});

describe("isSupportedLiveKitServerUrl", () => {
  it("accepts only websocket schemes", () => {
    expect(isSupportedLiveKitServerUrl("wss://livekit.example")).toBe(true);
    expect(isSupportedLiveKitServerUrl("ws://livekit:7880")).toBe(true);
    expect(isSupportedLiveKitServerUrl("https://livekit.example")).toBe(false);
    expect(isSupportedLiveKitServerUrl("")).toBe(false);
  });
});
