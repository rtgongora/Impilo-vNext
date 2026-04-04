"use client";

/**
 * Timeline — View the clinical timeline for a patient.
 * Route: /ehr/[patientId]/timeline | pageTitle: "Timeline"
 */

import { useParams } from "next/navigation";
import {
  Clock,
  Loader2,
  Stethoscope,
  HeartPulse,
  FileText,
  ClipboardList,
  FlaskConical,
  ArrowRightLeft,
  Pill,
  Syringe } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useTimeline,
  type TimelineEntryResource } from "@/hooks/queries/useTimeline";

/* ------------------------------------------------------------------ */
/*  Event type color mapping                                           */
/* ------------------------------------------------------------------ */

const EVENT_TYPE_CONFIG: Record<
  string,
  { color: string; bgColor: string; borderColor: string; icon: React.ElementType }
> = {
  ENCOUNTER: {
    color: "text-blue-700",
    bgColor: "bg-blue-100",
    borderColor: "border-blue-400",
    icon: Stethoscope },
  VITALS: {
    color: "text-red-700",
    bgColor: "bg-red-100",
    borderColor: "border-red-400",
    icon: HeartPulse },
  NOTE: {
    color: "text-indigo-700",
    bgColor: "bg-indigo-100",
    borderColor: "border-indigo-400",
    icon: FileText },
  ORDER: {
    color: "text-purple-700",
    bgColor: "bg-purple-100",
    borderColor: "border-purple-400",
    icon: ClipboardList },
  RESULT: {
    color: "text-green-700",
    bgColor: "bg-green-100",
    borderColor: "border-green-400",
    icon: FlaskConical },
  REFERRAL: {
    color: "text-orange-700",
    bgColor: "bg-orange-100",
    borderColor: "border-orange-400",
    icon: ArrowRightLeft },
  PRESCRIPTION: {
    color: "text-teal-700",
    bgColor: "bg-teal-100",
    borderColor: "border-teal-400",
    icon: Pill },
  IMMUNIZATION: {
    color: "text-cyan-700",
    bgColor: "bg-cyan-100",
    borderColor: "border-cyan-400",
    icon: Syringe } };

const DEFAULT_CONFIG = {
  color: "text-gray-700",
  bgColor: "bg-gray-100",
  borderColor: "border-gray-400",
  icon: Clock };

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function TimelinePage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data: timelineData, isLoading } = useTimeline(patientId);
  const entries: TimelineEntryResource[] = timelineData?.data ?? [];

  return (
    <EHRLayout>
      <PageShell title="Timeline" subtitle="Clinical event timeline">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading timeline...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center gap-2">
              <Clock className="w-5 h-5 text-blue-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                Timeline ({entries.length} events)
              </h2>
            </div>

            {entries.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Clock className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No timeline entries yet</p>
              </div>
            ) : (
              <div className="relative">
                {/* Vertical timeline line */}
                <div className="absolute left-[19px] top-0 bottom-0 w-0.5 bg-gray-200" />

                <div className="space-y-6">
                  {entries.map((entry) => {
                    const a = entry.attributes;
                    const config = EVENT_TYPE_CONFIG[a.eventType] ?? DEFAULT_CONFIG;
                    const IconComponent = config.icon;

                    return (
                      <div key={entry.id} className="relative flex gap-4">
                        {/* Timeline dot */}
                        <div
                          className={`relative z-10 flex-shrink-0 w-10 h-10 rounded-full ${config.bgColor} flex items-center justify-center border-2 ${config.borderColor}`}
                        >
                          <IconComponent className={`w-4 h-4 ${config.color}`} />
                        </div>

                        {/* Timeline card */}
                        <div className="flex-1 bg-white rounded-lg border border-gray-200 p-4 hover:shadow-sm transition-shadow">
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 mb-1">
                                <span
                                  className={`px-2 py-0.5 text-xs font-medium rounded-full ${config.bgColor} ${config.color}`}
                                >
                                  {a.eventType}
                                </span>
                                <span className="text-sm font-semibold text-gray-900 truncate">
                                  {a.title}
                                </span>
                              </div>
                              {a.description && (
                                <p className="text-sm text-gray-600 mt-1">{a.description}</p>
                              )}
                              {a.actorName && (
                                <p className="text-xs text-gray-500 mt-2">
                                  By: <span className="font-medium">{a.actorName}</span>
                                </p>
                              )}
                            </div>
                            <span className="text-xs text-gray-400 whitespace-nowrap flex-shrink-0">
                              {new Date(a.occurredAt).toLocaleString()}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
