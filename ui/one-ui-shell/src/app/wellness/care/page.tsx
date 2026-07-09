"use client";

import { useMemo, useState } from "react";
import { Stethoscope, Loader2, ArrowUpRight, ShieldAlert, Clock } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useCareLinkages, useRouteToCare } from "@/hooks/queries/useSimbaDepth";

function asRows(payload: unknown): Array<Record<string, unknown>> {
  const data = (payload as { data?: unknown })?.data ?? payload;
  if (Array.isArray(data)) return data as Array<Record<string, unknown>>;
  return [];
}

const TRIGGERS = [
  { value: "SCREENING_DUE", label: "A screening is due" },
  { value: "RISK_THRESHOLD", label: "A reading looks high" },
  { value: "RED_FLAG", label: "I have a worrying symptom" },
];

const SEVERITIES = [
  { value: "ROUTINE", label: "Routine — book when convenient" },
  { value: "URGENT", label: "Urgent — soon" },
  { value: "EMERGENCY", label: "Emergency — now" },
];

/**
 * Wellness → care linkage. Wellness is not a diagnosis: a worrying finding routes to the right
 * clinical owner (PCT referral, or emergency guidance). Simba records the linkage; the clinical
 * lifecycle and results stay with the care owner.
 */
export default function WellnessCarePage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id;

  const linkagesQ = useCareLinkages(cpid);
  const routeCare = useRouteToCare();

  const [trigger, setTrigger] = useState("SCREENING_DUE");
  const [severity, setSeverity] = useState("ROUTINE");
  const [reason, setReason] = useState("");

  const linkages = useMemo(() => asRows(linkagesQ.data), [linkagesQ.data]);
  const result = routeCare.data?.data as Record<string, unknown> | undefined;

  function submit() {
    if (!cpid) return;
    routeCare.mutate({ person_cpid: cpid, trigger, severity, reason: reason.trim() || undefined });
  }

  return (
    <AppLayout>
      <PageShell
        title="Connect a concern to care"
        subtitle="Wellness is not a diagnosis. If something needs attention, route it safely to the right care service."
        icon={<Stethoscope className="h-6 w-6" />}
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        <NompiloContextualGuidance routePath="/wellness/care" />

        {!cpid ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Sign in to connect a wellness concern to care.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <GlassSurface className="p-5">
              <h2 className="mb-3 font-semibold text-foreground">Route a concern</h2>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">What is happening?</label>
              <select value={trigger} onChange={(e) => setTrigger(e.target.value)} className="mb-3 w-full rounded-lg border px-3 py-2 text-sm">
                {TRIGGERS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">How soon?</label>
              <select value={severity} onChange={(e) => setSeverity(e.target.value)} className="mb-3 w-full rounded-lg border px-3 py-2 text-sm">
                {SEVERITIES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
              </select>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Anything to add? (optional)</label>
              <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} className="mb-3 w-full rounded-lg border px-3 py-2 text-sm" placeholder="e.g. blood pressure has been high this week" />

              {severity === "EMERGENCY" && (
                <p className="mb-3 flex items-start gap-1.5 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">
                  <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                  If this is a real emergency, use emergency services or go to your nearest emergency entry now.
                </p>
              )}

              <button
                type="button"
                onClick={submit}
                disabled={routeCare.isPending}
                className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
              >
                {routeCare.isPending ? (<><Loader2 className="h-4 w-4 animate-spin" /> Routing…</>) : (<>Connect to care <ArrowUpRight className="h-4 w-4" /></>)}
              </button>

              {result && (
                <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
                  {String(result.referral_status) === "EMERGENCY_ROUTED"
                    ? String(result.emergency_guidance ?? "Routed to emergency.")
                    : String(result.referral_status) === "ROUTED"
                      ? "A referral has been created with the care team. Track it in your care record."
                      : "Your concern was recorded. A care referral is pending and will follow up."}
                </div>
              )}
            </GlassSurface>

            <GlassSurface className="p-5">
              <h2 className="mb-3 font-semibold text-foreground">Your care linkages</h2>
              {linkagesQ.isLoading ? (
                <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</p>
              ) : linkages.length === 0 ? (
                <p className="text-sm text-muted-foreground">No care linkages yet. When a wellness finding needs attention it will appear here.</p>
              ) : (
                <ul className="space-y-2">
                  {linkages.map((l, i) => (
                    <li key={String(l.linkageId ?? i)} className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm">
                      <span className="text-foreground">{String(l.trigger ?? "")}</span>
                      <span className="flex items-center gap-1 text-xs text-muted-foreground">
                        <Clock className="h-3.5 w-3.5" /> {String(l.status ?? "OPEN")} · {String(l.targetOwner ?? "PCT")}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </GlassSurface>
          </div>
        )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
