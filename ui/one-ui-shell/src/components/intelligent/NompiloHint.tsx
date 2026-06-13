"use client";

import { useState, useEffect } from "react";
import { X } from "lucide-react";
import { NompiloTipCard } from "shared-ui";

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
        <NompiloTipCard
          message={message}
          suggestions={suggestions}
          onDismiss={() => setDismissed(true)}
          className="backdrop-blur-sm"
        />
        <button
          onClick={() => setDismissed(true)}
          className="sr-only"
          aria-label="Dismiss hint"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
