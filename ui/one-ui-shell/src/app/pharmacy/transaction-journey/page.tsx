"use client";

import { useMemo } from "react";
import { useSearchParams } from "next/navigation";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { JourneyStepper } from "@/components/workspace/JourneyStepper";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useRxJourneyLiveStatus, type RxLiveProbeState } from "@/hooks/queries/useRxJourneyLiveStatus";

function withQuery(base: string, patientId: string | null, encounterId: string | null) {
  const params = new URLSearchParams();
  if (patientId) params.set("patientId", patientId);
  if (encounterId) params.set("encounterId", encounterId);
  params.set("source", "rx-journey");
  const q = params.toString();
  return q ? `${base}?${q}` : base;
}

function stepStatusFromProbe(probe: RxLiveProbeState, fallback: "complete" | "current" | "upcoming"): "complete" | "current" | "upcoming" | "blocked" {
  if (probe === "live") return "complete";
  if (probe === "unavailable") return "blocked";
  if (probe === "empty" && fallback === "upcoming") return "current";
  return fallback;
}

export default function RxTransactionJourneyPage() {
  const searchParams = useSearchParams();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const live = useRxJourneyLiveStatus(patientId);

  const steps = useMemo(
    () => [
      {
        id: "registry",
        label: "1. Client & provider context",
        description: "Confirm patient (Vito) and prescriber (Varapi) in registry",
        href: patientId ? `/ehr/${patientId}` : "/registry/clients",
        status: patientId ? ("complete" as const) : ("current" as const),
        statusNote: patientId ? `Patient context: ${patientId}` : "Select or register a client",
      },
      {
        id: "trust",
        label: "2. Trust & purpose",
        description: "Verify facility context and purpose-of-use (Tshepo)",
        href: "/registry/trust",
        status: "current" as const,
      },
      {
        id: "encounter",
        label: "3. Clinical encounter",
        description: "Outpatient consultation or telemedicine session",
        href: patientId && encounterId
          ? `/ehr/${patientId}/encounter/${encounterId}`
          : "/queue",
        status: encounterId ? ("complete" as const) : ("upcoming" as const),
      },
      {
        id: "rx",
        label: "4. Prescription (Rx)",
        description: "Create or review prescription via pharmacy BFF",
        href: withQuery("/pharmacy/prescriptions", patientId, encounterId),
        status: stepStatusFromProbe(live.rx, patientId ? "current" : "upcoming"),
        statusNote: live.labels.rx,
      },
      {
        id: "marketplace",
        label: "5. Product / marketplace (Msika)",
        description: "Link formulary or marketplace product where applicable",
        href: "/marketplace",
        status: "upcoming" as const,
      },
      {
        id: "payment",
        label: "6. Payment / finance (MusheX)",
        description: "Billing, claim, subsidy, or deferred payment via finance BFF",
        href: "/finance/mushex-platform",
        status: stepStatusFromProbe(live.pay, "upcoming"),
        statusNote: live.labels.pay,
      },
      {
        id: "dispatch",
        label: "7. Dispatch / delivery (Nhume)",
        description: "Last-mile delivery for medicines or commodities",
        href: withQuery("/nhume/deliveries/new", patientId, encounterId),
        status: stepStatusFromProbe(live.delivery, "upcoming"),
        statusNote: live.labels.dispatch,
      },
      {
        id: "documents",
        label: "8. Documents & receipt",
        description: "Prescription record, receipt, proof of delivery",
        href: patientId ? `/ehr/${patientId}/documents` : "/home/documents",
        status: "upcoming" as const,
      },
      {
        id: "audit",
        label: "9. Transaction audit & reporting",
        description: "Core transaction feed, audit trail, data footprint",
        href: "/core-transaction",
        status: "upcoming" as const,
        statusNote: live.labels.ndila,
      },
    ],
    [patientId, encounterId, live],
  );

  return (
    <PlaneWorkspaceShell
      title="Rx transaction journey"
      subtitle="Prescription → marketplace → payment → dispatch — with registry, trust, and audit context"
      plane="Clinical / Transaction"
      maturity="partial"
      contextPatient={patientId ?? undefined}
      contextEncounter={encounterId ?? undefined}
      nompiloHint="Ask Nompilo for the next step in this Rx-to-delivery journey or payment pathway."
      relatedLinks={[
        { label: "Production Command Centre", href: "/production-command-centre" },
        { label: "Pharmacy hub", href: "/pharmacy" },
        { label: "Ndila routing", href: "/ndila" },
      ]}
    >
      <TrustContextBanner purposeOfUse="PRESCRIBING" />
      <div className="mb-4 flex flex-wrap gap-2">
        <FeatureMaturityBadge status="partial" detail="Live BFF probes on Rx · pay · dispatch · Ndila" />
      </div>
      <div
        className="mb-4 rounded-lg border border-border bg-background px-3 py-2 text-xs text-foreground"
        data-testid="rx-live-path-probes"
      >
        <span className="font-semibold text-foreground">Live path probes: </span>
        {live.labels.rx} · {live.labels.pay} · {live.labels.dispatch} · {live.labels.ndila}
      </div>
      <JourneyStepper
        title="End-to-end Rx demonstration path"
        subtitle="Each step links to an existing production surface. Pass ?patientId= and ?encounterId= to carry context."
        steps={steps}
      />
    </PlaneWorkspaceShell>
  );
}
