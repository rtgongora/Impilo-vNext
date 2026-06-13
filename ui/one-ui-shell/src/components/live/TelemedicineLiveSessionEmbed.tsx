"use client";

import Link from "next/link";
import { Shield, Stethoscope, Video } from "lucide-react";

interface TelemedicineLiveSessionEmbedProps {
  liveEventId?: string | null;
  impiloLiveJoinPath?: string | null;
  sessionId: string;
  consentGranted?: boolean;
}

/**
 * Telemedicine appointment-linked Impilo Live clinical session (doctrine §2).
 * Clinical documentation stays in Telemedicine/PCT — this is join + context only.
 */
export function TelemedicineLiveSessionEmbed({
  liveEventId,
  impiloLiveJoinPath,
  sessionId,
  consentGranted = false,
}: TelemedicineLiveSessionEmbedProps) {
  const joinPath = impiloLiveJoinPath ?? (liveEventId ? `/live/event/${liveEventId}/room` : null);

  if (!liveEventId || !joinPath) {
    return (
      <div className="rounded-lg border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
        <p className="font-medium flex items-center gap-2">
          <Stethoscope className="h-4 w-4" /> Clinical video via Impilo Live
        </p>
        <p className="mt-1">
          No governed Impilo Live clinical room is linked yet. Video may use the legacy telemedicine
          media path until a clinical session is provisioned with consent context.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-info/25 bg-info-soft/60 p-4">
      <div className="flex items-start gap-3">
        <div className="rounded-lg bg-blue-100 p-2 text-blue-800">
          <Shield className="h-5 w-5" />
        </div>
        <div className="flex-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-800">
            Telemedicine · Impilo Live
          </p>
          <p className="mt-1 text-sm text-foreground">
            Governed clinical session with consent-aware access. Documentation and orders remain in
            this telemedicine encounter.
          </p>
          <p className="mt-2 flex items-center gap-1 text-xs text-muted-foreground">
            Consent: {consentGranted ? "confirmed" : "required before join"}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {consentGranted ? (
              <Link
                href={joinPath}
                className="inline-flex items-center gap-1 rounded-lg bg-blue-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-800"
              >
                <Video className="h-4 w-4" /> Join clinical session
              </Link>
            ) : (
              <span className="inline-flex items-center rounded-lg bg-neutral-100 px-3 py-1.5 text-sm text-muted-foreground">
                Confirm consent to join
              </span>
            )}
            <Link
              href={`/telemedicine/session/${sessionId}`}
              className="inline-flex items-center rounded-lg border border-blue-700 px-3 py-1.5 text-sm font-medium text-blue-800 hover:bg-blue-100"
            >
              Return to encounter
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
