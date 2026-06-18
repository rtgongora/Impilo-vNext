"use client";

import Link from "next/link";
import { useState } from "react";
import { Loader2, ScanLine } from "lucide-react";
import { useCreateEncounterImagingLink, useEncounterImagingLinks } from "@/hooks/queries/useEncounterImagingLinks";

interface Props {
  patientId: string;
  encounterId: string;
}

type ImagingLinkRow = {
  id?: number;
  governedStudyId?: string;
  orosOrderId?: string;
  linkType?: string;
};

function asRows(data: unknown): ImagingLinkRow[] {
  if (!Array.isArray(data)) return [];
  return data as ImagingLinkRow[];
}

export function EncounterLinkedImagingStudiesPanel({ patientId, encounterId }: Props) {
  const [governedStudyId, setGovernedStudyId] = useState("");
  const [orosOrderId, setOrosOrderId] = useState("");
  const linksQ = useEncounterImagingLinks(encounterId);
  const createLink = useCreateEncounterImagingLink(encounterId);
  const rows = asRows(linksQ.data?.data);

  async function handleLink() {
    const studyId = governedStudyId.trim();
    if (!studyId) return;
    await createLink.mutateAsync({
      governed_study_id: studyId,
      oros_order_id: orosOrderId.trim() || undefined,
      link_type: "TRIAGE_IMAGING",
    });
    setGovernedStudyId("");
    setOrosOrderId("");
  }

  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <div className="mb-2 flex items-center gap-2">
        <ScanLine className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-medium text-foreground">Linked imaging studies (PCT persisted)</h3>
      </div>
      <p className="mb-3 text-xs text-muted-foreground">
        Link governed imaging studies to this encounter through the clinical workflow owner (PCT).
      </p>
      <div className="grid gap-2 md:grid-cols-2">
        <label className="block text-xs font-medium text-muted-foreground">
          Governed study ID
          <input
            value={governedStudyId}
            onChange={(e) => setGovernedStudyId(e.target.value)}
            className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="e.g. 42"
          />
        </label>
        <label className="block text-xs font-medium text-muted-foreground">
          OROS order ID (optional)
          <input
            value={orosOrderId}
            onChange={(e) => setOrosOrderId(e.target.value)}
            className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="Optional order reference"
          />
        </label>
      </div>
      <button
        type="button"
        onClick={() => void handleLink()}
        disabled={createLink.isPending || !governedStudyId.trim()}
        className="mt-3 inline-flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
      >
        {createLink.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
        Link imaging study
      </button>
      <div className="mt-4 space-y-2">
        {linksQ.isLoading ? (
          <p className="text-xs text-muted-foreground">Loading linked studies…</p>
        ) : rows.length === 0 ? (
          <p className="text-xs text-muted-foreground">No linked imaging studies yet.</p>
        ) : (
          rows.map((row) => (
            <div key={row.id ?? row.governedStudyId} className="flex items-center justify-between rounded-lg border border-border bg-background p-3 text-sm">
              <div>
                <p className="font-medium text-foreground">Study {row.governedStudyId}</p>
                {row.orosOrderId ? (
                  <p className="text-xs text-muted-foreground">Order {row.orosOrderId}</p>
                ) : null}
              </div>
              {row.governedStudyId ? (
                <Link
                  href={`/ehr/${encodeURIComponent(patientId)}/imaging/viewer?governedStudyId=${encodeURIComponent(row.governedStudyId)}`}
                  className="text-xs font-medium text-primary hover:underline"
                >
                  Open viewer
                </Link>
              ) : null}
            </div>
          ))
        )}
      </div>
    </section>
  );
}
