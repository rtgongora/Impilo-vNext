"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, Loader2, Sparkles } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCostaCostEstimate,
  useCostaCostEvents,
  useCostaTariffLibraries,
  useCostaTariffLists,
} from "@/hooks/queries/useCostaIntel";

function asRecordArray(v: unknown): { id?: number; name?: string; external_code?: string }[] {
  if (!Array.isArray(v)) return [];
  return v as { id?: number; name?: string; external_code?: string }[];
}

export default function FinanceCostaIntelPage() {
  const libsQ = useCostaTariffLibraries();
  const listsQ = useCostaTariffLists();
  const estimateM = useCostaCostEstimate();

  const [selectedListId, setSelectedListId] = useState<string>("");
  const [itemCode, setItemCode] = useState("ZW-OPD-001");
  const [encounterId, setEncounterId] = useState("");
  const [patientCpid, setPatientCpid] = useState("");
  const [estimatePreview, setEstimatePreview] = useState<unknown>(null);

  const lists = useMemo(() => asRecordArray(listsQ.data), [listsQ.data]);

  const eventsQ = useCostaCostEvents(
    encounterId.trim() || undefined,
    patientCpid.trim() || undefined,
  );

  return (
    <AppLayout>
      <PageShell
        title="COSTA & settlement tools"
        subtitle="Tariff library intel, quick cost estimates, captured cost events, and links into MusheX settlement — all via the finance-plane BFF."
        icon={<Sparkles className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap gap-3 text-sm">
          <Link href="/finance" className="inline-flex items-center gap-1 text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
          <Link href="/finance/workspace" className="text-indigo-600 hover:underline">
            Finance workspace
          </Link>
          <Link href="/finance/settlements" className="text-indigo-600 hover:underline">
            MusheX settlements
          </Link>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Tariff libraries</h2>
            <p className="mt-1 text-xs text-slate-500">GET /internal/v1/finance/costa-intel/tariff-libraries</p>
            {libsQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : libsQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load libraries.</p>
            ) : (
              <pre className="mt-3 max-h-56 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(libsQ.data, null, 2)}
              </pre>
            )}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Tariff lists (selector)</h2>
            <p className="mt-1 text-xs text-slate-500">GET /internal/v1/finance/costa-intel/tariff-lists</p>
            {listsQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : listsQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load tariff lists.</p>
            ) : (
              <>
                <label className="mt-3 block text-xs text-slate-600">
                  Selected list for probe
                  <select
                    className="mt-1 w-full rounded-lg border border-slate-200 px-2 py-2 text-sm"
                    value={selectedListId}
                    onChange={(e) => setSelectedListId(e.target.value)}
                    aria-label="Tariff list"
                  >
                    <option value="">Choose a list…</option>
                    {lists.map((t) => (
                      <option key={String(t.id)} value={String(t.id ?? "")}>
                        {(t.name ?? t.external_code ?? "list") + (t.id != null ? ` (#${t.id})` : "")}
                      </option>
                    ))}
                  </select>
                </label>
                <pre className="mt-3 max-h-40 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px]">
                  {JSON.stringify(listsQ.data, null, 2)}
                </pre>
              </>
            )}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:col-span-2">
            <h2 className="text-sm font-semibold text-slate-900">Cost estimate probe</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST /internal/v1/finance/costa-intel/cost-estimate — uses{" "}
              <code className="text-[11px]">selected_tariff_list_id</code> and a single{" "}
              <code className="text-[11px]">services_rendered</code> line for a smoke test.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="flex flex-col text-xs text-slate-600">
                Item code
                <input
                  className="mt-1 rounded-lg border border-slate-200 px-2 py-1.5 text-sm font-mono"
                  value={itemCode}
                  onChange={(e) => setItemCode(e.target.value)}
                />
              </label>
              <button
                type="button"
                disabled={estimateM.isPending || !selectedListId}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
                onClick={() => {
                  setEstimatePreview(null);
                  estimateM.mutate(
                    {
                      selected_tariff_list_id: Number(selectedListId),
                      services_rendered: [{ item_code: itemCode, quantity: "1" }],
                      patient_context: { exemption_category: "NONE" },
                      payer_context: {},
                    },
                    { onSuccess: (d) => setEstimatePreview(d) },
                  );
                }}
              >
                {estimateM.isPending ? (
                  <span className="inline-flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Estimating…
                  </span>
                ) : (
                  "Run estimate"
                )}
              </button>
            </div>
            {estimatePreview != null ? (
              <pre className="mt-3 max-h-80 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(estimatePreview, null, 2)}
              </pre>
            ) : (
              <p className="mt-3 text-xs text-slate-500">Pick a tariff list and run a probe to see COSTA JSON.</p>
            )}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:col-span-2">
            <h2 className="text-sm font-semibold text-slate-900">Captured cost events</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET /internal/v1/finance/costa-intel/cost-events?encounter_id=… or patient_cpid=…
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                className="min-w-[200px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                placeholder="Encounter id"
                value={encounterId}
                onChange={(e) => setEncounterId(e.target.value)}
                aria-label="Encounter id"
              />
              <input
                className="min-w-[200px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                placeholder="Patient CPID"
                value={patientCpid}
                onChange={(e) => setPatientCpid(e.target.value)}
                aria-label="Patient CPID"
              />
            </div>
            {eventsQ.isFetching ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : eventsQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load cost events (check query params).</p>
            ) : eventsQ.data != null ? (
              <pre className="mt-3 max-h-72 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(eventsQ.data, null, 2)}
              </pre>
            ) : (
              <p className="mt-3 text-xs text-slate-500">Enter an encounter id or patient CPID to query COSTA.</p>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
