"use client";

/**
 * Trust Console decision drawer — one queue item, its detail, and the
 * queue-appropriate decision actions with a mandatory-note-optional field.
 * The decision is a passthrough to the owning system of record via the BFF.
 */

import { useState } from "react";
import { Ban, CheckCircle2, HelpCircle, Loader2, Send, X, XCircle } from "lucide-react";
import {
  useTrustConsoleDecision,
  type TrustConsoleQueueName,
} from "@/hooks/queries/useTrustConsole";
import type { QueueConfig, QueueItemView, TrustConsoleDecisionValue } from "./queue-config";

const DECISION_STYLES: Record<
  TrustConsoleDecisionValue,
  { label: string; className: string; icon: typeof CheckCircle2 }
> = {
  APPROVED: {
    label: "Approve",
    className: "bg-emerald-600 text-white hover:bg-emerald-700",
    icon: CheckCircle2,
  },
  REJECTED: {
    label: "Reject",
    className: "bg-rose-600 text-white hover:bg-rose-700",
    icon: XCircle,
  },
  NEEDS_MORE_INFORMATION: {
    label: "Needs more info",
    className: "bg-amber-500 text-white hover:bg-amber-600",
    icon: HelpCircle,
  },
  RESEND: {
    label: "Resend invitation",
    className: "bg-emerald-600 text-white hover:bg-emerald-700",
    icon: Send,
  },
  REVOKE: {
    label: "Revoke invitation",
    className: "bg-rose-600 text-white hover:bg-rose-700",
    icon: Ban,
  },
};

export function DecisionDrawer({
  queue,
  config,
  item,
  onClose,
}: {
  queue: TrustConsoleQueueName;
  config: QueueConfig;
  item: QueueItemView;
  onClose: () => void;
}) {
  const [note, setNote] = useState("");
  const [outcome, setOutcome] = useState<string | null>(null);
  const decision = useTrustConsoleDecision();

  const submit = (value: TrustConsoleDecisionValue) => {
    setOutcome(null);
    decision.mutate(
      { queue, itemId: item.id, decision: value, note: note.trim() || undefined },
      {
        onSuccess: (attrs) => {
          if (attrs?.status === "denied" || attrs?.status === "pending_backend") {
            setOutcome(attrs.friendlyMessage ?? "The decision was not recorded.");
          } else {
            onClose();
          }
        },
        onError: () => setOutcome("The decision could not be submitted. Try again."),
      },
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-slate-900/40" role="dialog" aria-modal="true">
      <div className="flex h-full w-full max-w-md flex-col overflow-y-auto border-l border-warning/35 bg-card shadow-xl">
        <div className="flex items-start justify-between border-b border-border px-5 py-4">
          <div>
            <h2 className="font-semibold text-foreground">{item.title}</h2>
            <p className="mt-0.5 text-sm text-muted-foreground">{config.label}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close decision drawer"
            className="rounded-md p-1 text-muted-foreground hover:bg-background hover:text-foreground"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 space-y-4 px-5 py-4">
          <div className="rounded-lg border border-border bg-background px-4 py-3">
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Status</dt>
                <dd className="font-medium text-foreground">{item.status}</dd>
              </div>
              {item.createdAt && (
                <div className="flex justify-between gap-3">
                  <dt className="text-muted-foreground">Submitted</dt>
                  <dd className="text-foreground">{new Date(item.createdAt).toLocaleString()}</dd>
                </div>
              )}
              {item.detail.map((d) => (
                <div key={d.label} className="flex justify-between gap-3">
                  <dt className="text-muted-foreground">{d.label}</dt>
                  <dd className="break-all text-right text-foreground">{d.value}</dd>
                </div>
              ))}
            </dl>
          </div>

          {config.decisionNote && (
            <p className="rounded-md border border-warning/35 bg-warning-soft px-3 py-2 text-xs text-warning-foreground">
              {config.decisionNote}
            </p>
          )}

          <label className="block text-sm">
            <span className="font-medium text-foreground">Decision note</span>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={500}
              rows={3}
              placeholder="Why this decision was taken (recorded with the decision)"
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:border-amber-400 focus:outline-none"
            />
          </label>

          {outcome && (
            <p className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {outcome}
            </p>
          )}
        </div>

        <div className="flex flex-wrap gap-2 border-t border-border px-5 py-4">
          {config.decisions.map((value) => {
            const style = DECISION_STYLES[value];
            const Icon = style.icon;
            return (
              <button
                key={value}
                type="button"
                disabled={decision.isPending}
                onClick={() => submit(value)}
                className={`inline-flex items-center gap-1.5 rounded-md px-3 py-2 text-sm font-medium transition disabled:opacity-60 ${style.className}`}
              >
                {decision.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Icon className="h-4 w-4" />
                )}
                {style.label}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
