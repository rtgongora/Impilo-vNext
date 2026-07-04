"use client";

import { Headphones } from "lucide-react";

/**
 * Low-bandwidth toggle — switches the session between full video and
 * audio-only (real subscription pause, handled by AdaptiveSessionRoom).
 */
export function LowBandwidthToggle({
  audioOnly,
  onAudioOnlyChange,
}: {
  audioOnly: boolean;
  onAudioOnlyChange: (value: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onAudioOnlyChange(!audioOnly)}
      aria-pressed={audioOnly}
      className={`inline-flex items-center gap-1 rounded-lg border px-2 py-1 text-xs ${
        audioOnly
          ? "border-amber-400 bg-amber-950/40 text-warning-foreground"
          : "border-gray-600 text-muted-foreground"
      }`}
    >
      <Headphones className="h-3.5 w-3.5" />
      {audioOnly ? "Audio only" : "Video"}
    </button>
  );
}
