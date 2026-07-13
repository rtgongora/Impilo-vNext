/**
 * Regression guard for QA #12a: the BFF LiveComposerController returns a FLAT
 * object with top-level `result`/`fallbackUsed` (not an ApiResponse `{data}`
 * envelope) and always 200. composerAssist must read those top-level fields —
 * reading `res.data` gave undefined and Ask Nompilo rendered nothing.
 */
import { describe, expect, it, vi, beforeEach } from "vitest";

const post = vi.fn();
vi.mock("../api-client", () => ({
  apiClient: {
    post: (...args: unknown[]) => post(...args),
    get: vi.fn(),
    put: vi.fn(),
  },
}));

import { liveApi } from "../live";

beforeEach(() => post.mockReset());

describe("liveApi.composerAssist", () => {
  it("returns the BFF top-level `result`, not res.data", async () => {
    post.mockResolvedValueOnce({
      op: "suggest_agenda",
      provider: "anthropic",
      fallbackUsed: false,
      result: "Here is what this event covers…",
    });
    const out = await liveApi.composerAssist({ op: "suggest_agenda" });
    expect(out.result).toBe("Here is what this event covers…");
    expect(out.fallbackUsed).toBe(false);
  });

  it("propagates the BFF fallbackUsed flag instead of hard-coding false", async () => {
    post.mockResolvedValueOnce({ op: "suggest_agenda", fallbackUsed: true, result: "deterministic text" });
    const out = await liveApi.composerAssist({ op: "suggest_agenda" });
    expect(out.result).toBe("deterministic text");
    expect(out.fallbackUsed).toBe(true);
  });

  it("falls back locally when the request throws", async () => {
    post.mockRejectedValueOnce(new Error("network"));
    const out = await liveApi.composerAssist({ op: "suggest_title", eventType: "HEALTH_TALK", context: "diabetes" });
    expect(out.fallbackUsed).toBe(true);
    expect(String(out.result)).toContain("diabetes");
  });
});
