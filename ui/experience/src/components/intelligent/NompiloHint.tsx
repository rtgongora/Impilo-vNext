"use client";

import { useState, useEffect } from "react";
import { MessageCircle, X } from "lucide-react";

interface NompiloHintProps {
  /** Context-specific guidance message */
  message: string;
  /** Optional follow-up suggestions */
  suggestions?: string[];
}

/**
 * Nompilo contextual hint — a slim toast that hugs the bottom-center
 * of the viewport. Uses pointer-events-none on the wrapper so clicks
 * pass through to content beneath, with pointer-events-auto on the
 * toast itself. Narrow enough (max-w-md) and flush to the bottom edge
 * so it doesn't cover interactive elements above.
 */
export function NompiloHint({ message, suggestions }: NompiloHintProps) {
  const [dismissed, setDismissed] = useState(false);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setVisible(true), 500);
    return () => clearTimeout(timer);
  }, []);

  if (dismissed) return null;

  return (
    <div className="fixed inset-x-0 bottom-0 z-40 flex justify-center pointer-events-none pb-3">
      <div
        className={[
          "pointer-events-auto max-w-md w-full mx-4",
          "transition-all duration-400 ease-out",
          visible ? "translate-y-0 opacity-100" : "translate-y-3 opacity-0",
        ].join(" ")}
      >
        <div className="flex items-start gap-2.5 rounded-xl bg-white/95 backdrop-blur border border-impilo-200 px-3.5 py-2.5 shadow-md">
          <div className="h-6 w-6 rounded-full bg-impilo-50 flex items-center justify-center shrink-0 mt-0.5">
            <MessageCircle className="h-3.5 w-3.5 text-impilo-600" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-[11px] font-semibold text-impilo-700 leading-none">
              Nompilo
            </p>
            <p className="text-xs text-gray-600 mt-1 leading-relaxed">
              {message}
            </p>
            {suggestions && suggestions.length > 0 && (
              <div className="mt-1.5 space-y-0.5">
                {suggestions.map((s, i) => (
                  <p
                    key={i}
                    className="text-[11px] text-impilo-500 leading-snug"
                  >
                    • {s}
                  </p>
                ))}
              </div>
            )}
          </div>
          <button
            onClick={() => setDismissed(true)}
            className="text-gray-300 hover:text-gray-500 transition-colors shrink-0 mt-0.5"
            aria-label="Dismiss hint"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
