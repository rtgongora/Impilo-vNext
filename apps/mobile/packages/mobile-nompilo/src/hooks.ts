/**
 * React hook for Nompilo chat sessions in mobile apps.
 *
 * Stateless wrapper around `askNompilo`. Holds a small in-memory history,
 * exposes loading/error state, and keeps the last response so the UI can show
 * fallback indicators.
 */

import { useCallback, useState } from "react";
import { askNompilo } from "./nompiloClient";
import { NOMPILO_DISCLAIMER } from "./fallback";
import type { NompiloContext, NompiloMessage, NompiloAskResult } from "./types";

export interface UseNompiloOptions {
  context?: NompiloContext;
  /** Optional seed history (e.g. greeting). */
  seed?: NompiloMessage[];
  /** Maximum history retained (default 20). */
  maxHistory?: number;
}

export interface UseNompiloState {
  history: NompiloMessage[];
  loading: boolean;
  lastResult: NompiloAskResult | null;
  disclaimer: string;
  send: (prompt: string) => Promise<NompiloAskResult>;
  reset: () => void;
}

export function useNompilo(opts: UseNompiloOptions = {}): UseNompiloState {
  const max = opts.maxHistory ?? 20;
  const [history, setHistory] = useState<NompiloMessage[]>(opts.seed ?? []);
  const [loading, setLoading] = useState(false);
  const [lastResult, setLastResult] = useState<NompiloAskResult | null>(null);

  const send = useCallback(
    async (prompt: string) => {
      const trimmed = prompt.trim();
      if (!trimmed) {
        const noop: NompiloAskResult = {
          reply: "",
          modelAvailable: false,
          usedFallback: true,
          disclaimer: NOMPILO_DISCLAIMER,
        };
        return noop;
      }

      setLoading(true);
      const userMsg: NompiloMessage = {
        role: "user",
        content: trimmed,
        at: new Date().toISOString(),
      };
      try {
        const result = await askNompilo({
          prompt: trimmed,
          history,
          context: opts.context,
        });
        setHistory((prev) => {
          const next: NompiloMessage[] = [
            ...prev,
            userMsg,
            { role: "assistant", content: result.reply, at: new Date().toISOString() },
          ];
          return next.length > max ? next.slice(next.length - max) : next;
        });
        setLastResult(result);
        return result;
      } finally {
        setLoading(false);
      }
    },
    [history, max, opts.context]
  );

  const reset = useCallback(() => {
    setHistory(opts.seed ?? []);
    setLastResult(null);
  }, [opts.seed]);

  return {
    history,
    loading,
    lastResult,
    disclaimer: NOMPILO_DISCLAIMER,
    send,
    reset,
  };
}
