"use client";

/**
 * Pharmacy Hub — Card grid for pharmacy features.
 * Route: /pharmacy
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Pill, FileText, Package, ClipboardList, User } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { NompiloContextPanel } from "@/components/intelligent/NompiloContextPanel";
import { RelatedServicesPanel } from "@/components/workspace/RelatedServicesPanel";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const PHARMACY_RELATED_LINKS = [
  { label: "Rx transaction journey", href: "/pharmacy/transaction-journey", description: "Step-through Rx · pay · dispatch" },
  { label: "Core transactions", href: "/core-transaction", description: "Audit and transaction history" },
  { label: "Clinical hub", href: "/clinical", description: "Encounter and inpatient context" },
  { label: "Finance billing", href: "/finance/billing", description: "Payment and billing follow-through" },
];

const PHARMACY_SECTIONS = [
  {
    title: "Rx transaction journey",
    description: "Step-through: registry → Rx → payment → dispatch → audit",
    href: "/pharmacy/transaction-journey",
    icon: ClipboardList,
    color: "bg-cyan-100 text-cyan-700",
  },
  {
    title: "Prescriptions",
    description: "View and manage patient prescriptions",
    href: "/pharmacy/prescriptions",
    icon: FileText,
    color: "bg-impilo-100 text-impilo-500",
  },
  {
    title: "Dispense",
    description: "Dispense medications for approved prescriptions",
    href: "/pharmacy/dispense",
    icon: Pill,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Stock",
    description: "Monitor pharmacy stock levels and reorder points",
    href: "/pharmacy/stock",
    icon: Package,
    color: "bg-amber-100 text-amber-600",
  },
];

export default function PharmacyHubPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");
  const handoffParams = new URLSearchParams();

  if (patientId) handoffParams.set("patientId", patientId);
  if (encounterId) handoffParams.set("encounterId", encounterId);
  if (source) handoffParams.set("source", source);

  const withHandoff = (href: string) => {
    const query = handoffParams.toString();
    return query ? `${href}?${query}` : href;
  };

  const actions = [
    encounterId && patientId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    patientId
      ? { href: `/ehr/${patientId}/medications`, label: "Medication Review", icon: FileText, tone: "secondary" as const }
      : null,
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell title="Pharmacy" subtitle="Manage prescriptions, dispensing, and stock">
        <div className="space-y-6">
          <TrustContextBanner purposeOfUse="PRESCRIBING" />
          <WorkflowHeader
            badge="Medication handoff"
            badgeIcon={Pill}
            title="Keep pharmacy work tied to the patient, encounter, and outcome handoff that generated medication follow-through."
            description="The pharmacy hub now makes facility scope, closure source, and the next medication surface explicit before staff branch into prescription review, dispensing, or stock checks."
            context={[
              { label: "Facility", value: facility?.name ?? "No facility selected" },
              { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Pharmacy workspace" },
              {
                label: "Encounter context",
                value: encounterId && patientId ? "Linked to active clinical episode" : "General pharmacy queue",
              },
            ]}
            actions={actions}
            metrics={[
              {
                label: "Pharmacy surfaces",
                value: String(PHARMACY_SECTIONS.length),
                detail: "Prescriptions, dispense, and stock remain reachable from one continuity-aware hub.",
              },
              {
                label: "Next action",
                value: encounterId ? "Continue medication handoff" : "Choose pharmacy lane",
                detail:
                  encounterId && patientId
                    ? "Use the linked pharmacy surfaces below without losing the encounter and chart context."
                    : "Choose the pharmacy surface that matches the next medication task.",
              },
            ]}
          />

          <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
              Medication loop status
            </p>
            <p className="mt-2 text-sm text-slate-800">
              {source === "discharge"
                ? "This pharmacy hub was reached from encounter outcome, so the loop here is keeping prescribing, dispensing, and stock awareness tied to the same patient and encounter."
                : "This hub anchors pharmacy work, but the next medication surface should still preserve the source patient and encounter context when it exists."}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Open the next pharmacy surface below, or move back to the encounter, chart, or medication review workspace when the medication plan needs clinical clarification.
            </p>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1fr_260px]">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {PHARMACY_SECTIONS.map((section) => (
                <Link
                  key={section.href}
                  href={withHandoff(section.href)}
                  className="bg-white rounded-lg border border-gray-200 p-5 hover:border-impilo-200 hover:shadow-md transition-all group"
                >
                  <div className="flex flex-col items-center text-center gap-3">
                    <div className={`w-12 h-12 rounded-lg flex items-center justify-center ${section.color}`}>
                      <section.icon className="w-6 h-6" />
                    </div>
                    <div>
                      <h3 className="font-medium text-gray-900 group-hover:text-impilo-600">{section.title}</h3>
                      <p className="text-xs text-gray-500 mt-1">{section.description}</p>
                    </div>
                  </div>
                </Link>
              ))}
            </div>
            <div className="space-y-4">
              <RelatedServicesPanel links={PHARMACY_RELATED_LINKS} />
              <NompiloContextPanel
                plane="Pharmacy Plane"
                hint="Ask Nompilo for dispensing steps, stock checks, or the next Rx-to-delivery action."
              />
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
