import { describe, expect, it } from "vitest";
import { mapChannelHealth } from "../../screens/provider/TelemedicineScreen";

describe("mapChannelHealth", () => {
  it("maps connected states to primary", () => {
    expect(mapChannelHealth("connected").variant).toBe("primary");
  });

  it("maps reconnecting states to warning", () => {
    expect(mapChannelHealth("reconnecting").variant).toBe("warning");
  });

  it("maps unavailable states to destructive", () => {
    expect(mapChannelHealth("offline").variant).toBe("destructive");
  });
});
