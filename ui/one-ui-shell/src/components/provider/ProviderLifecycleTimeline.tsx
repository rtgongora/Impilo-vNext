"use client";

import React from "react";
import {
  FileText,
  CheckCircle2,
  Clock,
  XCircle,
  AlertTriangle,
  Award,
  Building,
  Users,
  RefreshCcw,
  Loader2,
} from "lucide-react";

type LifecycleEventType =
  | "application"
  | "license"
  | "certificate"
  | "affiliation"
  | "qualification"
  | "compliance"
  | "pic_assignment"
  | "practice_context";

interface LifecycleEvent {
  id: string;
  type: LifecycleEventType;
  title: string;
  description?: string;
  date: Date;
  status: string;
  statusLabel: string;
  actor?: string;
  details?: Record<string, string>;
}

interface ProviderLifecycleTimelineProps {
  events: LifecycleEvent[];
  isLoading?: boolean;
}

const eventTypeConfig: Record<
  LifecycleEventType,
  { icon: React.ElementType; color: string; label: string }
> = {
  application: { icon: FileText, color: "text-primary", label: "Application" },
  license: { icon: FileText, color: "text-primary", label: "License" },
  certificate: { icon: Award, color: "text-purple-600", label: "Certificate" },
  affiliation: { icon: Building, color: "text-cyan-600", label: "Affiliation" },
  qualification: { icon: FileText, color: "text-indigo-600", label: "Qualification" },
  compliance: { icon: AlertTriangle, color: "text-amber-600", label: "Compliance" },
  pic_assignment: { icon: Users, color: "text-teal-600", label: "PIC Assignment" },
  practice_context: { icon: Building, color: "text-muted-foreground", label: "Practice Context" },
};

const statusConfig: Record<
  string,
  { icon: React.ElementType; color: string; bgColor: string }
> = {
  ACTIVE: { icon: CheckCircle2, color: "text-primary", bgColor: "bg-emerald-100" },
  PENDING: { icon: Clock, color: "text-amber-600", bgColor: "bg-amber-100" },
  APPROVED: { icon: CheckCircle2, color: "text-primary", bgColor: "bg-emerald-100" },
  SUBMITTED: { icon: RefreshCcw, color: "text-primary", bgColor: "bg-blue-100" },
  REJECTED: { icon: XCircle, color: "text-red-600", bgColor: "bg-red-100" },
  EXPIRED: { icon: Clock, color: "text-muted-foreground", bgColor: "bg-neutral-100" },
  TERMINATED: { icon: XCircle, color: "text-red-600", bgColor: "bg-red-100" },
  EXEMPTED: { icon: AlertTriangle, color: "text-amber-600", bgColor: "bg-amber-100" },
  REVOKED: { icon: XCircle, color: "text-red-600", bgColor: "bg-red-100" },
};

function getStatusConfig(status: string) {
  return statusConfig[status] || {
    icon: Loader2,
    color: "text-muted-foreground",
    bgColor: "bg-neutral-100",
  };
}

function formatStatusLabel(status: string): string {
  return status.replace(/_/g, " ");
}

export function ProviderLifecycleTimeline({
  events,
  isLoading,
}: ProviderLifecycleTimelineProps) {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!events || events.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        <FileText className="mx-auto h-8 w-8 mb-2 opacity-50" />
        <p className="text-sm">No lifecycle events recorded</p>
      </div>
    );
  }

  const sortedEvents = [...events].sort(
    (a, b) => b.date.getTime() - a.date.getTime()
  );

  return (
    <div className="space-y-0">
      {sortedEvents.map((event, index) => {
        const typeInfo = eventTypeConfig[event.type];
        const statusInfo = getStatusConfig(event.status);
        const TypeIcon = typeInfo.icon;
        const StatusIcon = statusInfo.icon;

        return (
          <div key={event.id} className="relative pl-6">
            {index !== sortedEvents.length - 1 && (
              <div className="absolute left-2.5 top-8 bottom-0 w-px bg-border" />
            )}

            <div className="flex gap-3 pb-6 last:pb-0">
              <div
                className={`flex-shrink-0 rounded-full p-1.5 ${statusInfo.bgColor}`}
              >
                <TypeIcon className={`h-4 w-4 ${typeInfo.color}`} />
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-medium text-sm">{event.title}</p>
                    {event.description && (
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {event.description}
                      </p>
                    )}
                  </div>

                  <div className={`flex-shrink-0 flex items-center gap-1 rounded-full px-2 py-0.5 ${statusInfo.bgColor}`}>
                    <StatusIcon className={`h-3 w-3 ${statusInfo.color}`} />
                    <span className={`text-xs font-medium ${statusInfo.color}`}>
                      {formatStatusLabel(event.statusLabel)}
                    </span>
                  </div>
                </div>

                <div className="mt-1.5 flex items-center gap-2 text-xs text-muted-foreground">
                  <span>{event.date.toLocaleDateString()}</span>
                  {event.actor && (
                    <>
                      <span>·</span>
                      <span>{event.actor}</span>
                    </>
                  )}
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}