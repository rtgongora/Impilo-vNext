import { describe, expect, it } from "vitest";
import { normalizeLiveKitServerUrl } from "./LiveKitConsultRoom";

describe("normalizeLiveKitServerUrl", () => {
  it("converts https room links to wss endpoint", () => {
    expect(normalizeLiveKitServerUrl("https://livekit.example/rooms/impilo-room-1")).toBe("wss://livekit.example");
  });

  it("keeps websocket endpoint unchanged", () => {
    expect(normalizeLiveKitServerUrl("wss://livekit.example")).toBe("wss://livekit.example");
  });
});
