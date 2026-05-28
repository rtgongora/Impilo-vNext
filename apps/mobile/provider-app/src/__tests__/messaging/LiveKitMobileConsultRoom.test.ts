import { describe, expect, it } from "vitest";
import { normalizeLiveKitServerUrl } from "../../screens/provider/LiveKitMobileConsultRoom";

describe("normalizeLiveKitServerUrl", () => {
  it("normalizes browser room URLs to websocket server URLs", () => {
    expect(normalizeLiveKitServerUrl("https://livekit.example/rooms/test-room")).toBe("wss://livekit.example");
  });

  it("preserves websocket URLs", () => {
    expect(normalizeLiveKitServerUrl("wss://livekit.example")).toBe("wss://livekit.example");
  });
});
