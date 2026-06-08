"use client";

import Link from "next/link";
import { Radio, Video } from "lucide-react";

interface TelemedicineEncounterOrchestrationRailProps {
  sessionId?: string | null;
}

export function TelemedicineEncounterOrchestrationRail({
  sessionId,
}: TelemedicineEncounterOrchestrationRailProps) {
  return (
    <section
      className="rounded-xl border border-violet-200 bg-violet-50/60 px-4 py-3 text-sm text-violet-950"
      data-testid="telemedicine-encounter-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <Video className="mt-0.5 h-4 w-4 shrink-0 text-violet-600" />
          <div>
            <p className="font-medium">Telemedicine encounter lifecycle</p>
            <p className="text-violet-900">
              Create → consent → submit → specialist response → complete. Real-time media (RTC) remains preview-limited;
              referral and governance paths are live via Experience BFF.
            </p>
          </div>
        </div>
        {sessionId ? (
          <Link
            href={`/telemedicine/session/${sessionId}`}
            className="inline-flex items-center gap-1 rounded-lg bg-violet-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-800"
          >
            <Radio className="h-3.5 w-3.5" />
            Open session workspace
          </Link>
        ) : (
          <Link
            href="/telemedicine/new"
            className="inline-flex items-center gap-1 rounded-lg bg-violet-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-800"
          >
            Start teleconsult
          </Link>
        )}
      </div>
    </section>
  );
}
