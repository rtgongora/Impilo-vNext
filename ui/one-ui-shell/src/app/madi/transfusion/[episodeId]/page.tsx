"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Activity, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { MadiBedsideVerifyPanel } from "@/components/madi/MadiBedsideVerifyPanel";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useCompleteTransfusion,
  useRecordTransfusionObservation,
  useTransfusionEpisode,
  useVerifyTransfusion,
} from "@/hooks/queries/useMadi";

export default function TransfusionEpisodePage() {
  const params = useParams<{ episodeId: string }>();
  const episodeId = params.episodeId;
  const user = useAuthStore((s) => s.user);
  const { data: episode, isPending: episodeLoading } = useTransfusionEpisode(episodeId);
  const observation = useRecordTransfusionObservation(episodeId);
  const complete = useCompleteTransfusion(episodeId);
  const verify = useVerifyTransfusion(episodeId);

  const [preVerifyDone, setPreVerifyDone] = useState(false);
  const [obsType, setObsType] = useState("VITAL_TEMP");
  const [obsValue, setObsValue] = useState("");
  const [outcome, setOutcome] = useState("COMPLETED");

  const ep = episode as Record<string, unknown> | null | undefined;
  const patientVerified = ep?.patientVerified === true || preVerifyDone;

  return (
    <AppLayout>
      <PageShell title="Transfusion Episode" subtitle={`Episode ${episodeId}`} icon={<Activity className="h-6 w-6" />}>
        {episodeLoading && (
          <div className="mb-4 flex items-center gap-2 text-sm text-gray-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading episode…
          </div>
        )}

        <div className="mb-4">
          <MadiBedsideVerifyPanel
            episodeId={episodeId}
            defaultPatientCpid={ep?.patientCpid as string | undefined}
            verifiedBy={user?.displayName ?? user?.id}
            onVerified={() => setPreVerifyDone(true)}
          />
        </div>

        <section className="rounded-2xl border border-gray-200 bg-white p-5 mb-4 space-y-3">
          <h2 className="text-sm font-semibold">Record observation</h2>
          <select value={obsType} onChange={(e) => setObsType(e.target.value)} className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
            <option value="VITAL_TEMP">Temperature</option>
            <option value="VITAL_HR">Heart rate</option>
            <option value="VITAL_BP">Blood pressure</option>
            <option value="REACTION">Reaction note</option>
          </select>
          <input value={obsValue} onChange={(e) => setObsValue(e.target.value)} placeholder="Value" className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
          <button
            type="button"
            onClick={() => observation.mutate({
              observation_type: obsType,
              value_numeric: obsType.startsWith("VITAL") ? Number(obsValue) : undefined,
              value_text: obsType === "REACTION" ? obsValue : undefined,
            })}
            disabled={!obsValue || observation.isPending || !patientVerified}
            className="rounded-xl bg-gray-800 px-3 py-2 text-sm text-white disabled:opacity-50"
          >
            {observation.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : null} Add observation
          </button>
          {!patientVerified && (
            <p className="text-xs text-amber-700">Complete bedside verification before recording observations.</p>
          )}
        </section>

        <section className="rounded-2xl border border-gray-200 bg-white p-5 space-y-3">
          <h2 className="text-sm font-semibold">Complete & verify</h2>
          <select value={outcome} onChange={(e) => setOutcome(e.target.value)} className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
            <option value="COMPLETED">Completed</option>
            <option value="STOPPED">Stopped early</option>
            <option value="ADVERSE_EVENT">Adverse event</option>
          </select>
          <div className="flex gap-2">
            <button type="button" onClick={() => complete.mutate({ outcome_status: outcome })} disabled={complete.isPending || !patientVerified} className="rounded-xl bg-rose-600 px-3 py-2 text-sm text-white disabled:opacity-50">Complete</button>
            <button type="button" onClick={() => verify.mutate({})} disabled={verify.isPending} className="rounded-xl border border-gray-300 px-3 py-2 text-sm disabled:opacity-50">Verify</button>
          </div>
          <Link href="/madi/haemovigilance" className="text-sm text-rose-600">Report reaction →</Link>
        </section>
      </PageShell>
    </AppLayout>
  );
}
