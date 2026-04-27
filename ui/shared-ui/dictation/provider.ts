import type { DictationConfig, DictationProvider, DictationSession } from "./types";

function randomId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `dict-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

/**
 * No-op provider for SSR, tests, or deployments with dictation disabled.
 */
export function createNoopDictationProvider(): DictationProvider {
  return {
    id: "noop",
    isAvailable: () => false,
    async startSession(config: DictationConfig): Promise<DictationSession> {
      return {
        id: randomId(),
        requestedLanguage: config.language,
        startedAtUtc: new Date().toISOString(),
        metadata: { engine: "noop" },
      };
    },
    async endSession(_session: DictationSession): Promise<void> {
      /* no-op */
    },
  };
}
