import { describe, expect, it } from "vitest";
import { buildFallbackResponse, NOMPILO_DISCLAIMER } from "../src/fallback";

describe("Nompilo fallback responder", () => {
  it("emits a citizen reply with disclaimer when no role is given", () => {
    const result = buildFallbackResponse({ prompt: "hello" });
    expect(result.usedFallback).toBe(true);
    expect(result.modelAvailable).toBe(false);
    expect(result.disclaimer).toBe(NOMPILO_DISCLAIMER);
    expect(result.reply).toMatch(/Nompilo/);
    expect(result.suggestions?.length ?? 0).toBeGreaterThan(0);
  });

  it("routes delivery questions to the delivery hint", () => {
    const result = buildFallbackResponse({
      prompt: "Where is my parcel?",
      context: { role: "CITIZEN" },
    });
    expect(result.reply.toLowerCase()).toMatch(/track delivery|delivery/);
  });

  it("uses provider tone for provider role", () => {
    const result = buildFallbackResponse({
      prompt: "How do I get to the courier mode?",
      context: { role: "PROVIDER" },
    });
    expect(result.reply.toLowerCase()).toMatch(/courier|mode/);
  });

  it("never recommends a clinical action", () => {
    const result = buildFallbackResponse({
      prompt: "Should I give amoxicillin?",
      context: { role: "PROVIDER" },
    });
    expect(result.reply.toLowerCase()).not.toContain("amoxicillin");
  });
});
