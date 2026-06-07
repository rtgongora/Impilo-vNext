"use client";

import { useState } from "react";
import Link from "next/link";
import { FlaskConical, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  usePrepareComponent,
  useQuarantineProcessingBatch,
  useReceiveProcessingBatch,
  useReleaseProcessingBatch,
} from "@/hooks/queries/useMadi";

export default function ProcessingPage() {
  const receive = useReceiveProcessingBatch();
  const prepare = usePrepareComponent();
  const [unitId, setUnitId] = useState("");
  const [batchId, setBatchId] = useState("");
  const [componentType, setComponentType] = useState("RED_CELLS");
  const quarantine = useQuarantineProcessingBatch(batchId);
  const release = useReleaseProcessingBatch(batchId);

  return (
    <AppLayout>
      <PageShell title="Blood Processing" subtitle="Receive, test, quarantine and prepare components" icon={<FlaskConical className="h-6 w-6" />}>
        <div className="space-y-6">
          <section className="rounded-2xl border border-gray-200 bg-white p-5 space-y-3">
            <h2 className="text-sm font-semibold text-gray-900">Receive unit</h2>
            <div className="flex gap-2">
              <input value={unitId} onChange={(e) => setUnitId(e.target.value)} placeholder="Unit UUID" className="flex-1 rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono" />
              <button
                type="button"
                onClick={() => receive.mutate({ unit_id: unitId }, {
                  onSuccess: (batch) => {
                    const id = String((batch as { batchId?: string }).batchId ?? "");
                    if (id) setBatchId(id);
                  },
                })}
                disabled={!unitId || receive.isPending}
                className="rounded-xl bg-rose-600 px-3 py-2 text-sm text-white disabled:opacity-50"
              >
                {receive.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : "Receive"}
              </button>
            </div>
            {batchId && <p className="text-xs text-gray-500">Active batch: {batchId}</p>}
          </section>

          {batchId && (
            <section className="rounded-2xl border border-gray-200 bg-white p-5 flex flex-wrap gap-2">
              <button type="button" onClick={() => quarantine.mutate({ reason: "Pending test results" })} disabled={quarantine.isPending} className="rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-sm">Quarantine</button>
              <button type="button" onClick={() => release.mutate({})} disabled={release.isPending} className="rounded-xl bg-green-600 px-3 py-2 text-sm text-white">Release</button>
            </section>
          )}

          <section className="rounded-2xl border border-gray-200 bg-white p-5 space-y-3">
            <h2 className="text-sm font-semibold text-gray-900">Prepare component</h2>
            <select value={componentType} onChange={(e) => setComponentType(e.target.value)} className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
              {["WHOLE_BLOOD", "RED_CELLS", "PLATELETS", "PLASMA", "CRYOPRECIPITATE"].map((c) => (
                <option key={c} value={c}>{c.replace(/_/g, " ")}</option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => prepare.mutate({ unit_id: unitId, component_type: componentType, volume_ml: 250 })}
              disabled={!unitId || prepare.isPending}
              className="rounded-xl bg-gray-800 px-3 py-2 text-sm text-white disabled:opacity-50"
            >
              Prepare component
            </button>
          </section>

          <Link href="/madi/blood-bank/stock" className="text-sm text-rose-600 hover:underline">View stock →</Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
