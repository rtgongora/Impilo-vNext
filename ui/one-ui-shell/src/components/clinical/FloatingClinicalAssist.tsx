"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { MessageCircle, X, Headphones, ExternalLink } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useClinicalWorkflowConfig } from "./ClinicalWorkflowContext";
import {
  DEFAULT_CLINICAL_WIZARD_STEPS,
  wizardStepIndexForPathname,
} from "@/lib/clinical/encounter-workspace-nav";

function anonymizeRoute(path: string): string {
  return path.replace(/\/[0-9a-fA-F-]{8,}/g, "/…");
}

/**
 * Floating Nompilo / helpdesk entry — PHI-safe query params only (pattern route, no patient identifiers).
 */
export function FloatingClinicalAssist() {
  const pathname = usePathname() ?? "";
  const params = useParams();
  const patientId = params?.patientId as string | undefined;
  const encounterId = params?.encounterId as string | undefined;
  const { user } = useAuthStore();
  const role = useRoleGroup();
  const workflow = useClinicalWorkflowConfig();
  const steps = workflow?.steps ?? DEFAULT_CLINICAL_WIZARD_STEPS;
  const [open, setOpen] = useState(false);

  const askHref = useMemo(() => {
    const q = new URLSearchParams();
    q.set("from", pathname.startsWith("/ehr") ? "ehr" : "experience");
    q.set("routePattern", anonymizeRoute(pathname));
    if (user?.roles?.length) q.set("roleGroups", [role.isClinical && "clinical", role.isPrescriber && "prescriber", role.isDispenser && "dispenser"].filter(Boolean).join(",") || "other");
    const wizardIdx = patientId ? wizardStepIndexForPathname(pathname, patientId, steps) : 0;
    q.set("wizardStep", String(wizardIdx));
    if (encounterId) q.set("inEncounter", "1");
    return `/ask?${q.toString()}`;
  }, [encounterId, pathname, patientId, role.isClinical, role.isDispenser, role.isPrescriber, steps, user?.roles]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed bottom-5 right-5 z-[60] flex h-12 w-12 items-center justify-center rounded-full border border-primary/25 bg-card text-primary-hover shadow-lg hover:bg-primary-soft"
        title="Nompilo — contextual help"
        aria-label="Open Nompilo helpdesk"
      >
        <MessageCircle className="h-5 w-5" />
      </button>

      {open && (
        <>
          <button type="button" className="fixed inset-0 z-[59] bg-black/20" aria-label="Close helpdesk" onClick={() => setOpen(false)} />
          <div className="fixed bottom-20 right-5 z-[60] w-[min(100vw-2.5rem,22rem)] rounded-xl border border-border bg-card p-4 shadow-xl">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-xs font-semibold text-primary-hover">Nompilo</p>
                <p className="text-[11px] text-muted-foreground">Contextual help — no patient identifiers are sent in links.</p>
              </div>
              <button type="button" className="text-muted-foreground hover:text-muted-foreground" onClick={() => setOpen(false)} aria-label="Close">
                <X className="h-4 w-4" />
              </button>
            </div>

            <p className="mt-2 text-[10px] text-muted-foreground">
              If Nompilo or AI guidance is unavailable, use System Support — patient identifiers are never appended to these links.
            </p>

            <div className="mt-3 space-y-2">
              <Link
                href={askHref}
                onClick={() => setOpen(false)}
                className="flex items-center justify-between rounded-lg border border-primary/20 bg-primary-soft px-3 py-2 text-xs font-medium text-impilo-800 hover:bg-primary-soft"
              >
                <span className="inline-flex items-center gap-2">
                  <Headphones className="h-4 w-4" />
                  Ask Nompilo
                </span>
                <ExternalLink className="h-3.5 w-3.5 opacity-60" />
              </Link>
              <Link
                href="/communication"
                onClick={() => setOpen(false)}
                className="block rounded-lg border border-border px-3 py-2 text-xs text-foreground hover:bg-background"
              >
                Open Comms Hub
              </Link>
              <Link
                href="/support/tickets"
                onClick={() => setOpen(false)}
                className="block rounded-lg border border-border px-3 py-2 text-xs text-foreground hover:bg-background"
              >
                Escalate to system support
              </Link>
            </div>
          </div>
        </>
      )}
    </>
  );
}
