import { describe, expect, it } from "vitest";
import * as ndila from "../src/index";

describe("@impilo/mobile-ndila exports", () => {
  it("exposes core client and queue helpers", () => {
    expect(ndila.ndilaMobile).toBeDefined();
    expect(typeof ndila.captureCurrentLocation).toBe("function");
    expect(typeof ndila.queueLocationCapture).toBe("function");
    expect(typeof ndila.queueTrackingEvent).toBe("function");
    expect(typeof ndila.flushQueue).toBe("function");
  });
});
