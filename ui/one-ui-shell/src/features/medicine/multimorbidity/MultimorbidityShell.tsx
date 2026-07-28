"use client";

import { AlertTriangle, CheckCircle2, HelpCircle, Loader2, ShieldAlert } from "lucide-react";
import {
  useMultimorbidity,
  type MultimorbidityDetection,
  type MultimorbidityPanel,
} from "@/hooks/queries/useMultimorbidity";

/**
 * The multimorbidity view (brief.md §9).
 *
 * <p>Three renderings, never two. A detection that found something, a detection that looked and
 * found nothing, and a detection that could not look are visually distinct — because the third is
 * the one that gets mistaken for the second, and on this screen that mistake is the whole failure
 * mode. The patient opened this view precisely because their conditions were being managed in
 * isolation; a green tick over an unread source would confirm the isolation rather than expose it.
 */

const SEVERITY_CLASS: Record<string, string> = {
  CRITICAL: "border-danger/50 bg-red-50",
  HIGH: "border-danger/40 bg-red-50",
  MODERATE: "border-warning/40 bg-amber-50",
  LOW: "border-border bg-muted/30",
};

function DetectionCard({ detection }: { detection: MultimorbidityDetection }) {
  if (detection.state === "UNDETERMINED") {
    return (
      <div
        className="rounded-lg border border-warning/40 bg-amber-50 p-4"
        data-testid={`detection-undetermined-${detection.key}`}
      >
        <div className="flex items-start gap-2">
          <HelpCircle className="w-4 h-4 mt-0.5 text-warning-foreground" />
          <div>
            <p className="text-sm font-medium">{detection.title} — not checked</p>
            <p className="text-sm text-muted-foreground">
              {detection.undetermined_reason} This is <strong>not</strong> a clear result.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (detection.state === "NOT_DETECTED") {
    return (
      <div className="rounded-lg border border-border bg-muted/20 p-4" data-testid={`detection-clear-${detection.key}`}>
        <div className="flex items-start gap-2">
          <CheckCircle2 className="w-4 h-4 mt-0.5 text-success" />
          <p className="text-sm">
            <span className="font-medium">{detection.title}</span> — checked, nothing found.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-2" data-testid={`detection-found-${detection.key}`}>
      {detection.findings.map((f) => (
        <div
          key={f.code}
          className={`rounded-lg border p-4 ${SEVERITY_CLASS[f.severity] ?? SEVERITY_CLASS.LOW}`}
          data-testid={`finding-${f.code}`}
        >
          <div className="flex items-start gap-2">
            <ShieldAlert className="w-4 h-4 mt-0.5 text-warning-foreground" />
            <div className="space-y-1">
              <p className="text-sm font-medium">{f.message}</p>
              <p className="text-sm text-muted-foreground">{f.explanation}</p>
              {/* A finding without a next step is a notification, not decision support. */}
              <p className="text-sm font-medium">{f.required_action}</p>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function PanelCard({ panel }: { panel: MultimorbidityPanel }) {
  if (panel.state === "UNKNOWN") {
    return (
      <div className="rounded-lg border border-border bg-muted/30 p-4" data-testid={`panel-unknown-${panel.key}`}>
        <h4 className="text-sm font-medium">{panel.title}</h4>
        <p className="text-sm text-muted-foreground">{panel.unknown_reason}</p>
      </div>
    );
  }
  return (
    <div className="rounded-lg border border-border bg-card p-4" data-testid={`panel-${panel.key}`}>
      <h4 className="text-sm font-medium">{panel.title}</h4>
      {panel.entries.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid={`panel-empty-${panel.key}`}>
          Nothing recorded. This source was read.
        </p>
      ) : (
        <ul className="mt-1 space-y-1">
          {panel.entries.map((e) => (
            <li key={e} className="text-sm text-muted-foreground">
              {e}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function MultimorbidityShell({ patientId }: { patientId: string }) {
  const query = useMultimorbidity(patientId);
  const view = query.data?.data;

  if (query.isLoading) {
    return (
      <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground" data-testid="multimorbidity-loading">
        <Loader2 className="w-4 h-4 animate-spin" /> Assembling the multimorbidity view…
      </div>
    );
  }

  if (query.isError || !view) {
    return (
      <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-red-50 p-4" data-testid="multimorbidity-failed">
        <AlertTriangle className="w-4 h-4 text-danger mt-0.5" />
        <p className="text-sm text-danger">
          The multimorbidity view could not be assembled. Nothing was checked — do{" "}
          <strong>not</strong> read this as an absence of conflicts.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="multimorbidity">
      {view.incomplete && (
        <div className="rounded-lg border border-warning/40 bg-amber-50 p-4" data-testid="multimorbidity-incomplete">
          <p className="text-sm">{view.completeness_note}</p>
        </div>
      )}

      <section className="space-y-3">
        <h3 className="text-sm font-semibold">What the system checked</h3>
        {view.detections.map((d) => (
          <DetectionCard key={d.key} detection={d} />
        ))}
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold">The whole patient</h3>
        <div className="grid gap-3 lg:grid-cols-2">
          {view.panels.map((p) => (
            <PanelCard key={p.key} panel={p} />
          ))}
        </div>
      </section>

      <p className="text-xs text-muted-foreground">
        {view.approval_status} pending MoHCC ratification · content {view.content_version}
      </p>
    </div>
  );
}
