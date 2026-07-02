"use client";

/**
 * Khuluma Comms Approval Queue — operator surface.
 * Templates and campaigns must pass this governance gate before dispatch. This page lists the real
 * pending queue from experience-bff (`/internal/v1/comms/approval-queue`, which orchestrates the
 * Notification + Campaigns services) and lets an authorised operator approve, reject (with a mandatory
 * reason), dry-run, or cancel a queued send. Every action calls a real BFF route — no fake buttons.
 * Route: /communication/approvals
 */

import Link from "next/link";
import { useState } from "react";
import { AlertTriangle, ArrowLeft, CheckCircle, Clock, Loader2, PlayCircle, XCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  useCommsApprovalQueue,
  useApproveTemplate,
  useRejectTemplate,
  useApproveCampaign,
  useRejectCampaign,
  useCancelCampaign,
  useTemplateDryRun,
  useCampaignDryRun,
  type ApprovalQueueItem,
  type DryRunResult,
} from "@/hooks/queries/useCommsApproval";

const PRIORITY_STYLE: Record<string, string> = {
  HIGH: "bg-red-100 text-danger",
  MEDIUM: "bg-amber-100 text-warning-foreground",
  LOW: "bg-neutral-100 text-muted-foreground",
};

function fmt(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

function itemLabel(item: ApprovalQueueItem): string {
  return item.name || item.key || item.id;
}

type Kind = "template" | "campaign";

interface RowActionsProps {
  item: ApprovalQueueItem;
  kind: Kind;
  busy: boolean;
  onApprove: () => void;
  onReject: (reason: string) => void;
  onDryRun: () => void;
  onCancel?: () => void;
  dryRun?: DryRunResult | null;
}

function QueueRow({ item, kind, busy, onApprove, onReject, onDryRun, onCancel, dryRun }: RowActionsProps) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const priorityStyle = PRIORITY_STYLE[item.priority?.toUpperCase?.() ?? ""] ?? "bg-neutral-100 text-muted-foreground";

  return (
    <div className="bg-card rounded-lg border border-border p-5">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div>
          <p className="text-sm font-medium text-foreground">{itemLabel(item)}</p>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${priorityStyle}`}>
              {item.priority || "—"}
            </span>
            <span className="text-xs text-muted-foreground">status: {item.status}</span>
            {item.channel && <span className="text-xs text-muted-foreground">channel: {item.channel}</span>}
            {item.type && <span className="text-xs text-muted-foreground">type: {item.type}</span>}
            {item.sla_breach && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full font-medium bg-red-100 text-danger">
                <AlertTriangle className="w-3 h-3" /> SLA breach
              </span>
            )}
          </div>
        </div>
        <div className="text-xs text-muted-foreground flex items-center gap-1">
          <Clock className="w-3 h-3" /> {item.age_hours}h · updated {fmt(item.updated_at)}
        </div>
      </div>

      <div className="mt-3 flex items-center gap-2 flex-wrap">
        <button
          className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
          disabled={busy}
          onClick={onApprove}
        >
          <CheckCircle className="w-4 h-4 text-green-600" /> Approve
        </button>
        <button
          className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
          disabled={busy}
          onClick={() => setRejecting((v) => !v)}
        >
          <XCircle className="w-4 h-4 text-danger" /> Reject
        </button>
        <button
          className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
          disabled={busy}
          onClick={onDryRun}
        >
          <PlayCircle className="w-4 h-4 text-muted-foreground" /> Dry-run
        </button>
        {kind === "campaign" && onCancel && (
          <button
            className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
            disabled={busy}
            onClick={onCancel}
          >
            Cancel dispatch
          </button>
        )}
      </div>

      {rejecting && (
        <div className="mt-3 space-y-2">
          <textarea
            className="w-full text-sm rounded border border-border bg-background p-2"
            rows={2}
            placeholder="Reason for rejection (required)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <div className="flex items-center gap-2">
            <button
              className="px-3 py-1.5 text-sm rounded bg-danger text-white disabled:opacity-40"
              disabled={busy || reason.trim().length === 0}
              onClick={() => {
                onReject(reason.trim());
                setRejecting(false);
                setReason("");
              }}
            >
              Confirm rejection
            </button>
            <button
              className="px-3 py-1.5 text-sm rounded border border-border hover:bg-background"
              onClick={() => {
                setRejecting(false);
                setReason("");
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {dryRun && (
        <div className="mt-3 rounded border border-border bg-background p-3 text-xs">
          <p className={dryRun.dry_run_ok ? "text-green-700 font-medium" : "text-warning-foreground font-medium"}>
            {dryRun.dry_run_ok ? "Dry-run OK — dispatch checks pass" : "Dry-run blocked — issues found"}
          </p>
          <p className="text-muted-foreground mt-1">
            estimated reachable: {dryRun.estimated_reachable_audience} · blocked by policy: {dryRun.blocked_by_policy}
          </p>
          {dryRun.issues?.length > 0 && (
            <ul className="mt-1 list-disc list-inside text-warning-foreground">
              {dryRun.issues.map((issue, i) => (
                <li key={i}>{issue}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

export default function CommsApprovalsPage() {
  const queue = useCommsApprovalQueue();
  const approveTemplate = useApproveTemplate();
  const rejectTemplate = useRejectTemplate();
  const approveCampaign = useApproveCampaign();
  const rejectCampaign = useRejectCampaign();
  const cancelCampaign = useCancelCampaign();
  const templateDryRun = useTemplateDryRun();
  const campaignDryRun = useCampaignDryRun();

  const [msg, setMsg] = useState<string | null>(null);
  const [dryRuns, setDryRuns] = useState<Record<string, DryRunResult>>({});

  const data = queue.data?.data;
  const templates = data?.templates ?? [];
  const campaigns = data?.campaigns ?? [];
  const counts = data?.counts;
  const sourceHealth = data?.source_health ?? {};
  const busy =
    approveTemplate.isPending ||
    rejectTemplate.isPending ||
    approveCampaign.isPending ||
    rejectCampaign.isPending ||
    cancelCampaign.isPending;

  const downSources = Object.entries(sourceHealth)
    .filter(([, v]) => String(v).toUpperCase() === "DOWN")
    .map(([k]) => k);

  function report(promise: Promise<unknown>, ok: string) {
    setMsg(null);
    promise
      .then(() => setMsg(ok))
      .catch((e) =>
        setMsg(
          e && typeof e === "object" && "message" in e
            ? String((e as { message: unknown }).message)
            : "Action failed (comms source may be unreachable)",
        ),
      );
  }

  return (
    <AppLayout>
      <PageShell title="Comms Approval Queue" subtitle="Approve, reject, or dry-run pending templates and campaigns before dispatch">
        <div className="mb-4 flex items-center justify-between">
          <Link href="/communication" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="w-4 h-4" /> Back to Communication
          </Link>
          <FeatureMaturityBadge status="connected" detail="Notification + Campaigns via BFF approval queue" />
        </div>

        <p className="mb-4 text-xs text-muted-foreground">
          Every template and campaign passes this governance gate before it is dispatched. Rejections
          require a reason and are audited. This view reflects the real upstream queue — items only
          appear when a source is reachable.
        </p>

        {queue.isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : queue.isError ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <p className="text-danger text-sm">Could not load the approval queue. The comms services may be unreachable.</p>
            <button className="mt-3 px-3 py-1.5 text-sm rounded border border-border hover:bg-background" onClick={() => queue.refetch()}>
              Retry
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-center justify-between gap-4 flex-wrap">
                <p className="text-sm text-foreground">
                  {counts?.total ?? 0} pending · {counts?.templates ?? 0} templates · {counts?.campaigns ?? 0} campaigns
                </p>
                {msg && <span className="text-xs text-muted-foreground">{msg}</span>}
              </div>
              {downSources.length > 0 && (
                <p className="mt-2 text-xs text-warning-foreground">
                  Source unavailable: {downSources.join(", ")} — the queue may be incomplete.
                </p>
              )}
            </div>

            <section>
              <h2 className="text-sm font-semibold text-foreground mb-2">Templates</h2>
              {templates.length === 0 ? (
                <div className="bg-card rounded-lg border border-border p-8 text-center">
                  <p className="text-muted-foreground text-sm">No templates awaiting approval.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {templates.map((item) => (
                    <QueueRow
                      key={`t-${item.id}`}
                      item={item}
                      kind="template"
                      busy={busy}
                      dryRun={dryRuns[`t-${item.id}`] ?? null}
                      onApprove={() => report(approveTemplate.mutateAsync(item.id), `Approved template ${itemLabel(item)}`)}
                      onReject={(reason) => report(rejectTemplate.mutateAsync({ templateId: item.id, reason }), `Rejected template ${itemLabel(item)}`)}
                      onDryRun={() =>
                        templateDryRun
                          .mutateAsync(item.id)
                          .then((r) => setDryRuns((prev) => ({ ...prev, [`t-${item.id}`]: r.data })))
                          .catch(() => setMsg("Dry-run failed (comms source may be unreachable)"))
                      }
                    />
                  ))}
                </div>
              )}
            </section>

            <section>
              <h2 className="text-sm font-semibold text-foreground mb-2">Campaigns</h2>
              {campaigns.length === 0 ? (
                <div className="bg-card rounded-lg border border-border p-8 text-center">
                  <p className="text-muted-foreground text-sm">No campaigns awaiting approval.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {campaigns.map((item) => (
                    <QueueRow
                      key={`c-${item.id}`}
                      item={item}
                      kind="campaign"
                      busy={busy}
                      dryRun={dryRuns[`c-${item.id}`] ?? null}
                      onApprove={() => report(approveCampaign.mutateAsync(item.id), `Approved campaign ${itemLabel(item)}`)}
                      onReject={(reason) => report(rejectCampaign.mutateAsync({ campaignId: item.id, reason }), `Rejected campaign ${itemLabel(item)}`)}
                      onCancel={() => report(cancelCampaign.mutateAsync(item.id), `Cancelled dispatch for ${itemLabel(item)}`)}
                      onDryRun={() =>
                        campaignDryRun
                          .mutateAsync(item.id)
                          .then((r) => setDryRuns((prev) => ({ ...prev, [`c-${item.id}`]: r.data })))
                          .catch(() => setMsg("Dry-run failed (comms source may be unreachable)"))
                      }
                    />
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
