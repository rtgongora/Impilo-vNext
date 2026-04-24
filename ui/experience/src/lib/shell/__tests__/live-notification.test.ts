import { describe, it, expect } from "vitest";
import { normalizeLiveNotificationPayload } from "../live-notification";

describe("normalizeLiveNotificationPayload", () => {
  it("maps title, message, severity", () => {
    const n = normalizeLiveNotificationPayload({
      title: "Queue",
      message: "Wait time exceeded",
      severity: "high",
    });
    expect(n.title).toBe("Queue");
    expect(n.body).toBe("Wait time exceeded");
    expect(n.severity).toBe("HIGH");
  });

  it("reads body and nested payload.message", () => {
    expect(normalizeLiveNotificationPayload({ body: "x" }).body).toBe("x");
    const n = normalizeLiveNotificationPayload({
      payload: { detail: "inside" },
    });
    expect(n.body).toBe("inside");
  });

  it("falls back to JSON preview for unknown shapes", () => {
    const n = normalizeLiveNotificationPayload({ foo: 1, bar: "z" });
    expect(n.body).toContain("foo");
    expect(n.body).toContain("bar");
  });

  it("reads nested data.notification title and body", () => {
    const n = normalizeLiveNotificationPayload({
      data: {
        notification: { title: "Shift", body: "Swap confirmed" },
      },
    });
    expect(n.title).toBe("Shift");
    expect(n.body).toBe("Swap confirmed");
  });

  it("reads alert-style keys", () => {
    const n = normalizeLiveNotificationPayload({
      alertTitle: "Stock",
      msg: "Below par level",
      urgency: "high",
    });
    expect(n.title).toBe("Stock");
    expect(n.body).toBe("Below par level");
    expect(n.severity).toBe("HIGH");
  });
});
