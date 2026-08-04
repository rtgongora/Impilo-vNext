"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, FileText, Scale, Building2, Receipt } from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Public regulatory reference explorer (PF-W2). Reference-only "what you need to apply":
 * facility licensing application types, classifications, fees and rules (TUSO) plus
 * professional councils (VARAPI), readable WITHOUT sign-in via the anonymous gateway lane.
 * Starting or managing an actual application requires sign-in.
 */

interface ApplicationType {
  code?: string;
  displayName?: string;
  route?: string;
  councilReviewRequired?: boolean;
  inspectionRequired?: boolean;
  feeRequired?: boolean;
  manualRouteSummary?: string;
}
interface Classification {
  classCode?: string;
  facilityTypeLabel?: string;
}
interface FeeSchedule {
  feeCode?: string;
  appliesToClassCode?: string;
  amount?: number | null;
  currency?: string;
  sourceInstrument?: string;
}
interface Rule {
  code?: string;
  ruleKind?: string;
  statement?: string;
}
interface Requirements {
  applicationTypes?: ApplicationType[];
  classifications?: Classification[];
  feeSchedules?: FeeSchedule[];
  rules?: Rule[];
}
interface Council {
  councilCode?: string;
  name?: string;
  councilType?: string;
  description?: string;
  website?: string;
}
interface PublicRegister {
  registerCode?: string;
  name?: string;
  status?: string;
  description?: string;
}

export function PublicRegulatoryExplorer() {
  const [req, setReq] = useState<Requirements | null>(null);
  const [councils, setCouncils] = useState<Council[] | null>(null);
  const [registersByCouncil, setRegistersByCouncil] = useState<Record<string, PublicRegister[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const [r, c] = await Promise.all([
          apiClient.get<Requirements>("/internal/v1/public/gateway/regulatory/requirements"),
          apiClient.get<Council[]>("/internal/v1/public/gateway/regulatory/councils"),
        ]);
        const councilList = Array.isArray(c) ? c : [];
        const registerEntries = await Promise.all(
          councilList.map(async (council): Promise<[string | null, PublicRegister[]]> => {
            const code = council.councilCode;
            if (!code) return [null, []];
            try {
              const registers = await apiClient.get<PublicRegister[] | { registers?: PublicRegister[] }>(
                `/internal/v1/public/gateway/regulatory/councils/${encodeURIComponent(code)}/registers`,
              );
              const list: PublicRegister[] = Array.isArray(registers)
                ? registers
                : Array.isArray(registers?.registers)
                  ? registers.registers
                  : [];
              return [code, list];
            } catch {
              return [code, []];
            }
          }),
        );
        if (!cancelled) {
          setReq(r ?? {});
          setCouncils(councilList);
          const map: Record<string, PublicRegister[]> = {};
          for (const [code, list] of registerEntries) {
            if (code) map[code] = list;
          }
          setRegistersByCouncil(map);
        }
      } catch {
        if (!cancelled) setError("Regulatory information is temporarily unavailable. Please try again shortly.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <p className="flex items-center gap-2 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading regulatory information…
      </p>
    );
  }
  if (error) return <p className="text-sm text-red-700">{error}</p>;

  const money = (f: FeeSchedule) =>
    f.amount === null || f.amount === undefined
      ? "Set by governed schedule"
      : `${f.currency ?? ""} ${f.amount}`.trim();

  return (
    <div className="space-y-8">
      {/* Facility licensing application types */}
      <section aria-labelledby="apptypes-h" data-testid="reg-application-types">
        <h2 id="apptypes-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
          <FileText className="h-5 w-5 text-emerald-600" aria-hidden /> Facility licensing — how to apply
        </h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          {(req?.applicationTypes ?? []).map((a) => (
            <div key={a.code} className="rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
              <p className="font-semibold text-slate-900">{a.displayName}</p>
              {a.manualRouteSummary && (
                <p className="mt-1 text-[13px] leading-relaxed text-slate-600">{a.manualRouteSummary}</p>
              )}
              <ul className="mt-2 flex flex-wrap gap-1.5 text-[12px]">
                {a.councilReviewRequired && <li className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-700">Council review</li>}
                {a.inspectionRequired && <li className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-700">Inspection</li>}
                {a.feeRequired && <li className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-700">Fee payable</li>}
              </ul>
            </div>
          ))}
          {(req?.applicationTypes ?? []).length === 0 && (
            <p className="text-sm text-slate-600">No application types are published yet.</p>
          )}
        </div>
      </section>

      {/* Fees */}
      {(req?.feeSchedules ?? []).length > 0 && (
        <section aria-labelledby="fees-h" data-testid="reg-fees">
          <h2 id="fees-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
            <Receipt className="h-5 w-5 text-emerald-600" aria-hidden /> Fees
          </h2>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[420px] text-left text-sm">
              <thead className="text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="py-2 pr-4">Fee</th>
                  <th className="py-2 pr-4">Applies to</th>
                  <th className="py-2 pr-4">Amount</th>
                  <th className="py-2">Instrument</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {(req?.feeSchedules ?? []).map((f, i) => (
                  <tr key={`${f.feeCode}-${i}`}>
                    <td className="py-2 pr-4 font-medium text-slate-800">{f.feeCode}</td>
                    <td className="py-2 pr-4 text-slate-600">{f.appliesToClassCode ?? "—"}</td>
                    <td className="py-2 pr-4 text-slate-800">{money(f)}</td>
                    <td className="py-2 text-slate-500">{f.sourceInstrument ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* Classifications */}
      {(req?.classifications ?? []).length > 0 && (
        <section aria-labelledby="class-h" data-testid="reg-classifications">
          <h2 id="class-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
            <Building2 className="h-5 w-5 text-emerald-600" aria-hidden /> Facility classifications
          </h2>
          <div className="mt-3 flex flex-wrap gap-2">
            {(req?.classifications ?? []).map((c) => (
              <span key={c.classCode} className="rounded-full border border-slate-200 bg-white px-3 py-1 text-sm text-slate-700">
                <span className="font-semibold">{c.classCode}</span> · {c.facilityTypeLabel}
              </span>
            ))}
          </div>
        </section>
      )}

      {/* Professional councils */}
      <section aria-labelledby="councils-h" data-testid="reg-councils">
        <h2 id="councils-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
          <Scale className="h-5 w-5 text-emerald-600" aria-hidden /> Professional councils
        </h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          {(councils ?? []).map((c) => {
            const registers = c.councilCode ? registersByCouncil[c.councilCode] ?? [] : [];
            return (
              <div key={c.councilCode} className="rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
                <p className="font-semibold text-slate-900">{c.name}</p>
                {c.councilType && <p className="text-xs text-slate-500">{c.councilType}</p>}
                {c.description && <p className="mt-1 text-[13px] leading-relaxed text-slate-600">{c.description}</p>}
                {registers.length > 0 ? (
                  <ul className="mt-3 space-y-1.5" data-testid={`reg-registers-${c.councilCode}`}>
                    {registers.map((reg) => (
                      <li
                        key={reg.registerCode ?? reg.name}
                        className="flex flex-wrap items-baseline justify-between gap-2 text-[13px]"
                      >
                        <span className="text-slate-800">
                          {reg.name ?? reg.registerCode}
                          {reg.registerCode ? (
                            <span className="ml-1 font-mono text-[11px] text-slate-500">
                              {reg.registerCode}
                            </span>
                          ) : null}
                        </span>
                        {reg.status ? (
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600">
                            {reg.status}
                          </span>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-2 text-[12px] text-slate-500">No public registers listed yet.</p>
                )}
                {c.website && (
                  <a href={c.website} className="mt-2 inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800">
                    Council website →
                  </a>
                )}
              </div>
            );
          })}
          {(councils ?? []).length === 0 && (
            <p className="text-sm text-slate-600">No councils are published yet.</p>
          )}
        </div>
      </section>

      {/* Rules */}
      {(req?.rules ?? []).length > 0 && (
        <section aria-labelledby="rules-h" data-testid="reg-rules">
          <h2 id="rules-h" className="text-lg font-bold text-slate-900">Key rules</h2>
          <ul className="mt-3 space-y-2">
            {(req?.rules ?? []).map((r, i) => (
              <li key={`${r.code}-${i}`} className="rounded-lg border border-slate-200 bg-white p-3 text-sm text-slate-700">
                {r.statement}
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
        <p className="text-sm font-medium text-emerald-900">Ready to apply?</p>
        <p className="mt-1 text-sm text-emerald-800">
          Sign in to start or manage an application, upload documents and pay fees securely.
        </p>
        <Link
          href="/auth/login?returnTo=%2Fhome"
          className="mt-2 inline-block rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-800"
        >
          Sign in to apply
        </Link>
      </div>
    </div>
  );
}
