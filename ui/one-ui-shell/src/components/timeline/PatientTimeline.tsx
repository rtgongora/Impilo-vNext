"use client";

import React, { useState, useRef, useMemo } from "react";
import {
  Calendar,
  Clock,
  FileText,
  Pill,
  TestTube,
  Stethoscope,
  Activity,
  AlertTriangle,
  User,
  Filter,
  ChevronDown,
  ChevronUp,
  HeartPulse,
  Syringe,
  BedDouble,
  Loader2,
} from "lucide-react";
import { useTimeline, type TimelineEntryResource } from "@/hooks/queries/useTimeline";

/* ---------- types ---------- */
type TimelineEventType =
  | "admission"
  | "discharge"
  | "note"
  | "vitals"
  | "medication"
  | "lab"
  | "imaging"
  | "procedure"
  | "alert"
  | "consult";

interface TimelineEvent {
  id: string;
  type: TimelineEventType;
  title: string;
  description: string;
  timestamp: Date;
  author?: string;
  details?: Record<string, string>;
  isImportant?: boolean;
}

/* ---------- mapper ---------- */
const KNOWN_EVENT_TYPES: TimelineEventType[] = [
  "admission", "discharge", "note", "vitals", "medication",
  "lab", "imaging", "procedure", "alert", "consult",
];

function mapEventType(raw: string): TimelineEventType {
  const lower = raw.toLowerCase() as TimelineEventType;
  return KNOWN_EVENT_TYPES.includes(lower) ? lower : "note";
}

function mapTimelineEntry(entry: TimelineEntryResource): TimelineEvent {
  return {
    id: entry.id,
    type: mapEventType(entry.attributes.eventType),
    title: entry.attributes.title,
    description: entry.attributes.description ?? "",
    timestamp: new Date(entry.attributes.occurredAt),
    author: entry.attributes.actorName ?? undefined,
    isImportant: false,
  };
}

/* ---------- event type config ---------- */
const eventTypeConfig: Record<
  TimelineEventType,
  { icon: React.ElementType; color: string; label: string }
> = {
  admission: { icon: BedDouble, color: "bg-primary", label: "Admission" },
  discharge: { icon: BedDouble, color: "bg-emerald-500", label: "Discharge" },
  note: { icon: FileText, color: "bg-neutral-500", label: "Note" },
  vitals: { icon: HeartPulse, color: "bg-pink-500", label: "Vitals" },
  medication: { icon: Pill, color: "bg-purple-500", label: "Medication" },
  lab: { icon: TestTube, color: "bg-indigo-500", label: "Lab" },
  imaging: { icon: Stethoscope, color: "bg-amber-500", label: "Imaging" },
  procedure: { icon: Syringe, color: "bg-red-500", label: "Procedure" },
  alert: { icon: AlertTriangle, color: "bg-red-600", label: "Alert" },
  consult: { icon: User, color: "bg-cyan-500", label: "Consult" },
};

/* ---------- component ---------- */
interface PatientTimelineProps {
  patientId: string;
}

export function PatientTimeline({ patientId }: PatientTimelineProps) {
  const { data, isLoading, error } = useTimeline(patientId);
  const [expandedEvents, setExpandedEvents] = useState<string[]>([]);
  const [visibleTypes, setVisibleTypes] = useState<TimelineEventType[]>(
    Object.keys(eventTypeConfig) as TimelineEventType[]
  );
  const [filterOpen, setFilterOpen] = useState(false);
  const filterRef = useRef<HTMLDivElement>(null);

  const events: TimelineEvent[] = useMemo(
    () => (data?.data ?? []).map(mapTimelineEntry),
    [data]
  );

  const toggleExpand = (id: string) => {
    setExpandedEvents((prev) =>
      prev.includes(id) ? prev.filter((e) => e !== id) : [...prev, id]
    );
  };

  const toggleType = (type: TimelineEventType) => {
    setVisibleTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]
    );
  };

  const filteredEvents = events.filter((event) =>
    visibleTypes.includes(event.type)
  );

  // Group events by date
  const groupedEvents = filteredEvents.reduce(
    (acc, event) => {
      const dateKey = event.timestamp.toLocaleDateString();
      if (!acc[dateKey]) acc[dateKey] = [];
      acc[dateKey].push(event);
      return acc;
    },
    {} as Record<string, TimelineEvent[]>
  );

  const formatTime = (date: Date) => {
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  };

  const formatRelativeTime = (date: Date) => {
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    return `${diffDays}d ago`;
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-primary" />
        <span className="ml-2 text-sm text-[color:var(--text-secondary)]">Loading timeline...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-12 text-red-600">
        <AlertTriangle className="h-5 w-5 mr-2" />
        <span className="text-sm">Failed to load timeline. Please try again.</span>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="impilo-surface-card p-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2 text-[color:var(--text-primary)]">
            <Activity className="h-5 w-5 text-[color:var(--primary)]" />
            Patient Timeline
          </h2>
          <p className="text-sm text-[color:var(--text-secondary)]">
            Chronological view of all clinical events
          </p>
        </div>

        {/* Filter dropdown */}
        <div className="relative" ref={filterRef}>
          <button
            onClick={() => setFilterOpen((prev) => !prev)}
            className="inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-full border border-[color:var(--border-soft)] bg-card text-[color:var(--text-secondary)] hover:bg-[color:var(--surface-soft)] transition-colors"
          >
            <Filter className="h-4 w-4 mr-2" />
            Filter ({visibleTypes.length})
          </button>

          {filterOpen && (
            <div className="absolute right-0 mt-1 w-48 rounded-2xl border border-[color:var(--border-soft)] bg-card shadow-impilo-floating z-50">
              <div className="py-1">
                {Object.entries(eventTypeConfig).map(([type, config]) => {
                  const isChecked = visibleTypes.includes(
                    type as TimelineEventType
                  );
                  return (
                    <button
                      key={type}
                      onClick={() => toggleType(type as TimelineEventType)}
                      className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-neutral-100 transition-colors"
                    >
                      <input
                        type="checkbox"
                        checked={isChecked}
                        readOnly
                        className="h-4 w-4 rounded border-border text-primary focus:ring-primary/40"
                      />
                      <div
                        className={`h-2 w-2 rounded-full ${config.color}`}
                      />
                      <span className="text-foreground">{config.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
      </div>

      {/* Timeline */}
      <div className="impilo-surface-card overflow-y-auto max-h-[600px] space-y-6 p-4">
        {Object.keys(groupedEvents).length === 0 && (
          <div className="text-center py-12 text-[color:var(--text-muted)]">
            <Activity className="w-12 h-12 mx-auto mb-4 opacity-50" />
            <p>No timeline events found</p>
          </div>
        )}
        {Object.entries(groupedEvents).map(([date, dateEvents]) => (
          <div key={date}>
            {/* Date header */}
            <div className="sticky top-0 bg-card/95 backdrop-blur py-2 z-10">
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border border-[color:var(--border-soft)] bg-card text-[color:var(--text-secondary)]">
                <Calendar className="h-3 w-3 mr-1" />
                {date === new Date().toLocaleDateString() ? "Today" : date}
              </span>
            </div>

            {/* Events with vertical line */}
            <div className="ml-4 border-l-2 border-[color:var(--border-soft)] pl-6 space-y-4">
              {dateEvents.map((event) => {
                const config = eventTypeConfig[event.type];
                const Icon = config.icon;
                const isExpanded = expandedEvents.includes(event.id);

                return (
                  <div key={event.id} className="relative">
                    {/* Timeline dot */}
                    <div
                      className={`absolute -left-[31px] h-4 w-4 rounded-full ${config.color} flex items-center justify-center ring-4 ring-white`}
                    >
                      <Icon className="h-2.5 w-2.5 text-white" />
                    </div>

                    {/* Event card */}
                    <div
                      className={`rounded-2xl border bg-card p-4 transition-all hover:shadow-impilo-card ${
                        event.isImportant
                          ? "ring-1 ring-[color:var(--primary-muted)] border-[color:var(--primary-muted)]"
                          : "border-[color:var(--border-soft)]"
                      }`}
                    >
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <h4 className="font-medium text-sm text-foreground">
                              {event.title}
                            </h4>
                            {event.isImportant && (
                              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-primary-soft text-primary">
                                Important
                              </span>
                            )}
                          </div>
                          <p className="text-sm text-muted-foreground">
                            {event.description}
                          </p>

                          {/* Expanded details */}
                          {event.details && isExpanded && (
                            <div className="mt-3 p-3 bg-background rounded-lg">
                              <div className="grid grid-cols-2 gap-2 text-xs">
                                {Object.entries(event.details).map(
                                  ([key, value]) => (
                                    <div key={key}>
                                      <span className="text-muted-foreground">
                                        {key}:
                                      </span>
                                      <span className="ml-1 font-medium text-foreground">
                                        {value}
                                      </span>
                                    </div>
                                  )
                                )}
                              </div>
                            </div>
                          )}

                          {/* Meta: time, relative time, author */}
                          <div className="flex items-center gap-3 mt-2 text-xs text-muted-foreground flex-wrap">
                            <span className="flex items-center gap-1">
                              <Clock className="h-3 w-3" />
                              {formatTime(event.timestamp)}
                            </span>
                            <span>{formatRelativeTime(event.timestamp)}</span>
                            {event.author && (
                              <span className="flex items-center gap-1">
                                <User className="h-3 w-3" />
                                {event.author}
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Expand toggle */}
                        {event.details && (
                          <button
                            onClick={() => toggleExpand(event.id)}
                            className="p-1 text-muted-foreground hover:text-muted-foreground hover:bg-neutral-100 rounded transition-colors"
                          >
                            {isExpanded ? (
                              <ChevronUp className="h-4 w-4" />
                            ) : (
                              <ChevronDown className="h-4 w-4" />
                            )}
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
