"use client";

/**
 * Delivery Policy Management
 *
 * Lists every delivery policy with its proof requirements, SLA target,
 * allowed modes and integration provider hints. The policies drive what the
 * dispatcher and courier UIs can offer — they are read by the backend at
 * delivery creation and assignment time.
 */

import { ScrollText, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNhumePolicies } from "@/hooks/useNhume";

export default function NhumePoliciesPage() {
  const { data, isPending } = useNhumePolicies();
  const rows = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Delivery Policies"
        subtitle="Approval, proof, SLA, allowed modes and integration provider hints"
        icon={<ScrollText className="h-6 w-6" />}
      >
        {isPending ? (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading…
          </div>
        ) : rows.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-gray-300 bg-gray-50 p-12 text-center text-gray-600">
            No delivery policies configured.
          </div>
        ) : (
          <div className="space-y-3">
            {rows.map((p, idx) => {
              const r = p as Record<string, unknown>;
              return (
                <div key={idx} className="rounded-2xl border border-gray-200 bg-white p-5">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-semibold text-gray-900">{String(r.display_name ?? r.policy_id ?? "policy")}</h3>
                      <p className="text-xs text-gray-500 mt-0.5">{String(r.delivery_type ?? "")}</p>
                    </div>
                    {r.active ? (
                      <span className="inline-flex items-center rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 px-2 py-0.5 text-[11px]">Active</span>
                    ) : (
                      <span className="inline-flex items-center rounded-full bg-gray-100 text-gray-700 border border-gray-200 px-2 py-0.5 text-[11px]">Inactive</span>
                    )}
                  </div>
                  <p className="mt-2 text-sm text-gray-700">{String(r.description ?? "")}</p>
                  <dl className="mt-3 grid grid-cols-2 md:grid-cols-4 gap-x-4 gap-y-2 text-xs">
                    <Pair label="Approval" value={r.requires_approval ? "required" : "not required"} />
                    <Pair label="Payment" value={r.requires_payment ? "required" : "not required"} />
                    <Pair label="Stock check" value={r.requires_stock_check ? "yes" : "no"} />
                    <Pair label="Cold chain" value={r.cold_chain_required ? "yes" : "no"} />
                    <Pair label="Chain of custody" value={r.requires_chain_of_custody ? "yes" : "no"} />
                    <Pair label="Identity verify" value={String(r.requires_identity_verify ?? "NONE")} />
                    <Pair label="SLA target" value={r.sla_target_minutes ? `${r.sla_target_minutes} min` : "—"} />
                    <Pair label="Escalation" value={String(r.escalation_rule ?? "—")} />
                  </dl>
                  <div className="mt-3 grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
                    <Block label="Allowed modes" raw={r.allowed_modes} />
                    <Block label="Allowed courier kinds" raw={r.allowed_courier_kinds} />
                    <Block label="Required proof" raw={r.proof_methods_required} />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Pair({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="uppercase tracking-wide text-gray-500">{label}</dt>
      <dd className="font-medium text-gray-900">{value}</dd>
    </div>
  );
}

function Block({ label, raw }: { label: string; raw: unknown }) {
  const list = Array.isArray(raw) ? raw : typeof raw === "string" ? safeParseArray(raw) : [];
  return (
    <div className="rounded-xl border border-gray-100 bg-gray-50 p-3">
      <div className="text-[11px] uppercase tracking-wide text-gray-500 mb-1">{label}</div>
      {list.length === 0 ? (
        <span className="text-gray-500">—</span>
      ) : (
        <div className="flex flex-wrap gap-1">
          {list.map((v, idx) => (
            <span key={idx} className="inline-flex items-center rounded-full bg-white border border-gray-200 px-2 py-0.5 text-[11px] text-gray-800">
              {String(v)}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function safeParseArray(input: string): unknown[] {
  try {
    const v = JSON.parse(input);
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}
