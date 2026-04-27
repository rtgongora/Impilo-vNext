import { describe, it, expect } from "vitest";
import {
  buildGenericHealthNotificationBody,
  genericNotificationTitle,
  PUSH_GENERIC_NEW_MESSAGE_BODY,
} from "../src/pushNotificationPrivacy";

describe("pushNotificationPrivacy", () => {
  it("returns non-empty generic title", () => {
    expect(genericNotificationTitle().length).toBeGreaterThan(0);
  });

  it("never embeds clinical words in generic health body", () => {
    const b = buildGenericHealthNotificationBody("health");
    expect(b.toLowerCase()).not.toContain("hiv");
    expect(b.toLowerCase()).not.toContain("result");
    expect(b.toLowerCase()).not.toContain("positive");
  });

  it("maps message kind to new message copy", () => {
    expect(buildGenericHealthNotificationBody("message")).toBe(PUSH_GENERIC_NEW_MESSAGE_BODY);
  });
});
