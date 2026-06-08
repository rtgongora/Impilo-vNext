"use client";

import { useState } from "react";
import Link from "next/link";
import { ShieldAlert, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { HaemovigilancePolicyOrchestrationRail } from "@/components/madi/HaemovigilancePolicyOrchestrationRail";
import { useOpenHaemovigilanceCase, useReportReaction } from "@/hooks/queries/useMadi";

export default function HaemovigilancePage() {
  const user = useAuthStore((s) => s.user);
  const report = useReportReaction();
  const openCase = useOpenHaemovigilanceCase();
  const [episodeId, setEpisodeId] = useState("");
  const [reactionType, setReactionType] = useState("FEVER");
  const [severity, setSeverity] = useState("MILD");
  const [description, setDescription] = useState("");
  const [reactionId, setReactionId] = useState("");

  function handleReport(e: React.FormEvent) {
    e.preventDefault();
    report.mutate(
      {
        episode_id: episodeId,
        reaction_type: reactionType,
        severity,
        description,
        reported_by: user?.displayName ?? user?.id,
      },
      {
        onSuccess: (row) => {
          const id = String((row as { reactionId?: string }).reactionId ?? (row as { id?: string }).id ?? "");
          if (id) setReactionId(id);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Haemovigilance" subtitle="Report and investigate transfusion reactions" icon={<ShieldAlert className="h-6 w-6" />}>
        <HaemovigilancePolicyOrchestrationRail />
        <form onSubmit={handleReport} className="max-w-lg space-y-4 rounded-2xl border border-gray-200 bg-white p-6 mb-6">
          <label className="block text-sm font-medium text-gray-700">
            Transfusion episode ID
            <input required value={episodeId} onChange={(e) => setEpisodeId(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono" />
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="block text-sm font-medium text-gray-700">
              Reaction type
              <select value={reactionType} onChange={(e) => setReactionType(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
                <option value="FEVER">Fever</option>
                <option value="URTICARIA">Urticaria</option>
                <option value="ANAPHYLAXIS">Anaphylaxis</option>
                <option value="TRALI">TRALI</option>
                <option value="OTHER">Other</option>
              </select>
            </label>
            <label className="block text-sm font-medium text-gray-700">
              Severity
              <select value={severity} onChange={(e) => setSeverity(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white">
                <option value="MILD">Mild</option>
                <option value="MODERATE">Moderate</option>
                <option value="SEVERE">Severe</option>
              </select>
            </label>
          </div>
          <label className="block text-sm font-medium text-gray-700">
            Description
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm resize-none" />
          </label>
          <button type="submit" disabled={report.isPending} className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
            {report.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Report reaction
          </button>
        </form>

        {reactionId && (
          <div className="rounded-xl border border-green-200 bg-green-50 p-4 text-sm">
            <p className="mb-2">Reaction recorded: <code className="text-xs">{reactionId}</code></p>
            <button
              type="button"
              onClick={() => openCase.mutate({ reaction_id: reactionId })}
              disabled={openCase.isPending}
              className="rounded-xl bg-gray-800 px-3 py-2 text-sm text-white disabled:opacity-50"
            >
              Open investigation case
            </button>
          </div>
        )}

        <div className="mt-4 flex flex-wrap gap-4 text-sm">
          <Link href="/madi/transfusion" className="text-rose-600 hover:underline">← Transfusion workspace</Link>
          <Link href="/madi/haemovigilance/national" className="text-gray-700 hover:text-rose-600">
            National supervisory dashboard →
          </Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
