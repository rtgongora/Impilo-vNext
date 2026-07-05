"use client";

import { useEffect, useState } from "react";
import { Disc, X } from "lucide-react";

/**
 * Recording notice for meeting participants. The MEETING template sets consentRequired=false
 * (PROFESSIONAL sensitivity) but participants must still be INFORMED — this toast surfaces
 * whenever recording flips on (host/cohost started it via the existing live-room recording
 * endpoint, enforced server-side by the template's whoCanStart policy).
 */
export function RecordingConsentToast({ recording }: { recording: boolean }) {
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (recording) setDismissed(false); // re-announce every time recording starts
  }, [recording]);

  if (!recording || dismissed) return null;

  return (
    <div
      className="pointer-events-auto absolute left-1/2 top-10 z-30 flex -translate-x-1/2 items-center gap-2 rounded-md border border-red-500/50 bg-red-950/90 px-3 py-2 text-xs text-red-100 shadow-lg"
      role="status"
      data-testid="recording-consent-toast"
    >
      <Disc className="h-3.5 w-3.5 animate-pulse text-red-400" />
      <span>This meeting is being recorded by the host. The recording is governed and audited.</span>
      <button type="button" aria-label="Dismiss recording notice" onClick={() => setDismissed(true)}>
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
