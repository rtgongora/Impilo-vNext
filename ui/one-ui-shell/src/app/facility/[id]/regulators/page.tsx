"use client";

/**
 * Facility regulators (multi-regulator relationship).
 * Route: /facility/[id]/regulators
 *
 * T4 owns this route. A facility may be regulated by MANY councils — no hardcoded
 * single regulator. Backed by the WGV facility-regulator relationship SoR.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Plus, Loader2, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityRegulators, useLinkRegulator } from "@/components/facility-mode/useRegulators";

const RELATIONSHIP_TYPES = [
  "PRIMARY_REGULATOR",
  "SECONDARY_REGULATOR",
  "LICENSING_AUTHORITY",
  "ACCREDITING_BODY",
];

export default function FacilityRegulatorsPage() {
  const params = useParams();
  const id = params.id as string;
  const { data: regulators, isLoading } = useFacilityRegulators(id);
  const link = useLinkRegulator(id);

  const [councilCode, setCouncilCode] = useState("");
  const [councilId, setCouncilId] = useState("");
  const [relationshipType, setRelationshipType] = useState(RELATIONSHIP_TYPES[0]);
  const [registrationNumber, setRegistrationNumber] = useState("");

  return (
    <AppLayout>
      <PageShell title="Regulators" subtitle="Councils that regulate this facility">
        <div className="mb-4">
          <Link href={`/facility/${id}/cockpit`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to cockpit
          </Link>
        </div>

        <div className="max-w-3xl space-y-6">
          <div className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-3 text-sm font-medium text-foreground">Link a regulator</h3>
            <div className="grid grid-cols-2 gap-2">
              <input value={councilCode} onChange={(e) => setCouncilCode(e.target.value)} placeholder="Council code (e.g. MDPCZ)"
                className="rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
              <input value={councilId} onChange={(e) => setCouncilId(e.target.value)} placeholder="Council id"
                className="rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
              <select value={relationshipType} onChange={(e) => setRelationshipType(e.target.value)}
                className="rounded-md border border-border bg-background px-2 py-1.5 text-sm">
                {RELATIONSHIP_TYPES.map((t) => (
                  <option key={t} value={t}>{t.replace(/_/g, " ")}</option>
                ))}
              </select>
              <input value={registrationNumber} onChange={(e) => setRegistrationNumber(e.target.value)} placeholder="Registration number"
                className="rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
            </div>
            <button type="button" disabled={!councilCode.trim() || !councilId.trim() || link.isPending}
              onClick={() =>
                link.mutate(
                  {
                    councilId: councilId.trim(),
                    councilCode: councilCode.trim(),
                    relationshipType,
                    registrationNumber: registrationNumber.trim() || undefined,
                  },
                  { onSuccess: () => { setCouncilCode(""); setCouncilId(""); setRegistrationNumber(""); } },
                )
              }
              className="mt-3 inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50">
              <Plus className="h-4 w-4" /> Link regulator
            </button>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : (
            <ul className="space-y-2">
              {(regulators ?? []).map((r) => (
                <li key={r.id} className="flex items-center justify-between rounded-lg border border-border bg-card p-3">
                  <div className="flex items-center gap-3">
                    <ShieldCheck className="h-5 w-5 text-primary" />
                    <div>
                      <span className="text-sm font-medium text-foreground">{r.councilCode}</span>
                      <p className="text-xs text-muted-foreground">
                        {r.relationshipType.replace(/_/g, " ")}
                        {r.registrationNumber ? ` · ${r.registrationNumber}` : ""}
                      </p>
                    </div>
                  </div>
                  <span className={`rounded-full px-2 py-0.5 text-[10px] uppercase ${
                    r.status === "ACTIVE" ? "bg-emerald-500/15 text-emerald-600" : "bg-muted text-muted-foreground"
                  }`}>
                    {r.status}
                  </span>
                </li>
              ))}
              {(regulators ?? []).length === 0 && (
                <li className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                  No regulators linked. A facility may be regulated by multiple councils.
                </li>
              )}
            </ul>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
