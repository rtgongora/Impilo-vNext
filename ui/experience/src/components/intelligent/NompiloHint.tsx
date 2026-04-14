"use client";

import { useState } from "react";
import { MessageCircle, X } from "lucide-react";

interface NompiloHintProps {
  /** Context-specific guidance message */
  message: string;
  /** Optional follow-up suggestions */
  suggestions?: string[];
}

export function NompiloHint({ message, suggestions }: NompiloHintProps) {
  const [dismissed, setDismissed] = useState(false);
  if (dismissed) return null;

  return (
    <div className="fixed bottom-6 right-6 z-50 max-w-sm">
      <div className="bg-white rounded-2xl shadow-lg border border-impilo-100 p-4">
        <div className="flex items-start gap-3">
          <div className="h-8 w-8 rounded-full bg-impilo-50 flex items-center justify-center shrink-0">
            <MessageCircle className="h-4 w-4 text-impilo-500" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-semibold text-impilo-700">Nompilo</p>
            <p className="text-sm text-gray-600 mt-1">{message}</p>
            {suggestions && suggestions.length > 0 && (
              <div className="mt-2 space-y-1">
                {suggestions.map((s, i) => (
                  <p key={i} className="text-xs text-impilo-500">• {s}</p>
                ))}
              </div>
            )}
          </div>
          <button onClick={() => setDismissed(true)} className="text-gray-300 hover:text-gray-500">
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
