import React from "react";

export interface NompiloTipCardProps {
  title?: string;
  message: string;
  suggestions?: string[];
  source?: "Nompilo" | "Fundo";
  className?: string;
  onDismiss?: () => void;
}

function TipIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor" aria-hidden>
      <path d="M9 18h6v-2H9v2zm3-16C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17a1 1 0 001 1h6a1 1 0 001-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z" />
    </svg>
  );
}

export function NompiloTipCard({
  title,
  message,
  suggestions = [],
  source = "Nompilo",
  className = "",
  onDismiss,
}: NompiloTipCardProps) {
  return (
    <aside
      className={`relative overflow-hidden rounded-2xl border border-warning/35 bg-warning-soft p-4 shadow-impilo-nompilo ${className}`}
      aria-label={`${source} tip`}
    >
      <div className="absolute left-0 top-0 h-full w-1 bg-warning" aria-hidden />
      <div className="flex items-start gap-3 pl-2">
        <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-warning/25 text-warning-foreground">
          <TipIcon />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-warning-foreground">
            {title ?? source}
          </p>
          <p className="mt-1 text-sm text-foreground">{message}</p>
          {suggestions.length > 0 ? (
            <ul className="mt-2 space-y-1">
              {suggestions.map((suggestion) => (
                <li key={suggestion} className="text-xs text-muted-foreground">
                  • {suggestion}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
        {onDismiss ? (
          <button
            type="button"
            onClick={onDismiss}
            className="shrink-0 rounded-lg px-2 py-1 text-xs font-medium text-muted-foreground hover:text-foreground"
            aria-label="Dismiss tip"
          >
            Dismiss
          </button>
        ) : null}
      </div>
    </aside>
  );
}
