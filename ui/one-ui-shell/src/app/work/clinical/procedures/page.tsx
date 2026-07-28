"use client";

/**
 * Clinical Procedures Pipeline — catalogue browser and appropriateness pre-check.
 * Route: /work/clinical/procedures | Zone: clinical
 *
 * The clinician surface that makes six waves of procedures-service capability reachable:
 * search the national catalogue, open a definition to see exactly what it requires and who
 * owns each requirement, and run an appropriateness/duplication check before requesting a
 * procedure. Read/evaluate only — procedures-service is engine-not-store, so nothing here
 * writes a clinical record; a request is still placed through the owning specialty's own
 * request path (OROS), this surface only informs it.
 *
 * EMPTY VS UNKNOWN VS UNAVAILABLE, rendered as three distinct states, not collapsed to one:
 *   - isError            → "could not reach the catalogue" (never rendered as an empty list)
 *   - catalogueSize === 0 → "the catalogue is genuinely empty"
 *   - matched === 0, catalogueSize > 0 → "no results for this filter"
 * This is not a style preference. A BFF failure once rendered as "no conditions" for every
 * patient on the adult problem list because a client fell through `data ?? []` on error; the
 * hooks this page uses (useProceduresCatalogue) are written specifically so that mistake is
 * hard to make here.
 */

import { useState } from "react";
import { AlertTriangle, Loader2, Search, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCatalogueSearch,
  useCatalogueDetail,
  useAppropriatenessCheck,
  type AppropriatenessRequest,
} from "@/hooks/queries/useProceduresCatalogue";

const SIDE_OPTIONS = ["LEFT", "RIGHT", "BILATERAL", "MIDLINE", "NOT_APPLICABLE", "MULTIPLE"] as const;

const DISPOSITION_STYLE: Record<string, string> = {
  BLOCK: "border-danger/40 bg-danger/5 text-danger",
  PROPOSE_ALTERNATIVE: "border-amber-300 bg-amber-50 text-amber-800",
  CLARIFY: "border-blue-200 bg-blue-50 text-blue-800",
};

export default function ProceduresCataloguePage() {
  const [specialty, setSpecialty] = useState("");
  const [category, setCategory] = useState("");
  const [q, setQ] = useState("");
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [side, setSide] = useState<string>("");
  const [checkRequest, setCheckRequest] = useState<AppropriatenessRequest | null>(null);

  const searchQ = useCatalogueSearch({ specialty, category, q });
  const detailQ = useCatalogueDetail(selectedCode);
  const checkQ = useAppropriatenessCheck(checkRequest);

  const runCheck = () => {
    if (!selectedCode) return;
    setCheckRequest({
      definitionCode: selectedCode,
      side: side || null,
      patientIdentityConfirmed: true,
    });
  };

  return (
    <AppLayout>
      <PageShell
        title="Procedure Catalogue"
        subtitle="National procedure catalogue, requirements and appropriateness pre-check"
      >
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search by name or code…"
              className="w-full rounded-md border border-gray-200 py-2 pl-9 pr-3 text-sm"
              data-testid="procedures-catalogue-search-input"
            />
          </div>
          <input
            type="text"
            value={specialty}
            onChange={(e) => setSpecialty(e.target.value)}
            placeholder="Specialty (e.g. SURGERY)"
            className="rounded-md border border-gray-200 px-3 py-2 text-sm"
          />
          <input
            type="text"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            placeholder="Class (e.g. THEATRE)"
            className="rounded-md border border-gray-200 px-3 py-2 text-sm"
          />
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {/* ── Catalogue list ─────────────────────────────────────────── */}
          <div className="rounded-lg border border-gray-100">
            {searchQ.isLoading ? (
              <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading catalogue…
              </div>
            ) : searchQ.isError ? (
              <div
                className="flex flex-col items-center gap-2 p-8 text-center text-sm text-danger"
                data-testid="procedures-catalogue-unavailable"
                role="alert"
              >
                <AlertTriangle className="h-6 w-6" />
                Could not reach the procedure catalogue. This is not the same as an empty
                catalogue — try again, or report if it persists.
              </div>
            ) : searchQ.data && searchQ.data.catalogueSize === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
                The catalogue has no published entries yet.
              </div>
            ) : searchQ.data && searchQ.data.matched === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground" data-testid="procedures-catalogue-no-match">
                No procedures match this filter ({searchQ.data.catalogueSize} in the catalogue overall).
              </div>
            ) : (
              <ul className="divide-y divide-gray-100" data-testid="procedures-catalogue-list">
                {searchQ.data?.items.map((item) => (
                  <li key={item.definitionCode}>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedCode(item.definitionCode);
                        setSide("");
                        setCheckRequest(null);
                      }}
                      className={`w-full px-4 py-3 text-left text-sm hover:bg-gray-50 ${
                        selectedCode === item.definitionCode ? "bg-gray-50" : ""
                      }`}
                      data-testid="procedures-catalogue-item"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-medium">{item.clinicalName}</span>
                        {item.requiresSiteSideVerification && (
                          <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-800">
                            site/side
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {item.definitionCode} · {item.owningSpecialty} · {item.category}
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* ── Detail + appropriateness check ────────────────────────── */}
          <div className="rounded-lg border border-gray-100 p-4">
            {!selectedCode ? (
              <p className="p-8 text-center text-sm text-muted-foreground">
                Select a procedure to see its requirements.
              </p>
            ) : detailQ.isLoading ? (
              <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading requirements…
              </div>
            ) : detailQ.isError ? (
              <div className="p-8 text-center text-sm text-danger" role="alert">
                Could not load this catalogue entry.
              </div>
            ) : detailQ.data ? (
              <div data-testid="procedures-catalogue-detail">
                <h3 className="text-base font-semibold">{detailQ.data.clinicalName}</h3>
                <p className="text-xs text-muted-foreground">
                  {detailQ.data.definitionCode} v{detailQ.data.version} · {detailQ.data.status}
                  {detailQ.data.approvingAuthority === "PENDING_MOHCC_RATIFICATION" && (
                    <span className="ml-2 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gray-600">
                      pending MoHCC ratification
                    </span>
                  )}
                </p>

                <h4 className="mt-4 text-xs font-semibold uppercase text-muted-foreground">
                  Requirements ({detailQ.data.requirements.length})
                </h4>
                <ul className="mt-1 space-y-1" data-testid="procedures-requirements-list">
                  {detailQ.data.requirements.map((r) => (
                    <li key={r.requirementCode} className="rounded border border-gray-100 p-2 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="font-medium">{r.requirementLabel}</span>
                        <span className="text-muted-foreground">{r.obligation}</span>
                      </div>
                      <div className="mt-0.5 text-muted-foreground">
                        Owner: {r.ownerRole}
                        {r.overridableInEmergency ? " · emergency-overridable" : " · not overridable"}
                      </div>
                    </li>
                  ))}
                </ul>

                {/* Site/side capture — mirrors the closed vocabulary inpatient.procedure_episode.laterality
                    enforces server-side, deliberately, so a value this UI could send is never one the
                    backend gate would reject. Full anatomical-map capture is S16 (surgical graphics); an
                    appropriateness pre-check needs only the side, not a marked finding. */}
                {detailQ.data.lateralityApplicability !== "NOT_APPLICABLE" && (
                  <div className="mt-4">
                    <label className="text-xs font-semibold uppercase text-muted-foreground">Side</label>
                    <select
                      value={side}
                      onChange={(e) => setSide(e.target.value)}
                      className="mt-1 block w-full rounded-md border border-gray-200 px-3 py-2 text-sm"
                      data-testid="procedures-side-select"
                    >
                      <option value="">Not yet confirmed</option>
                      {SIDE_OPTIONS.map((s) => (
                        <option key={s} value={s}>{s}</option>
                      ))}
                    </select>
                  </div>
                )}

                <button
                  type="button"
                  onClick={runCheck}
                  className="mt-4 w-full rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground"
                  data-testid="procedures-run-appropriateness-check"
                >
                  Run appropriateness check
                </button>

                {checkRequest && (
                  <div className="mt-4" data-testid="procedures-appropriateness-result">
                    {checkQ.isLoading ? (
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Loader2 className="h-4 w-4 animate-spin" /> Evaluating…
                      </div>
                    ) : checkQ.isError ? (
                      <p className="text-sm text-danger" role="alert">
                        Could not evaluate appropriateness — try again.
                      </p>
                    ) : checkQ.data ? (
                      <div>
                        <p className="text-sm font-medium">
                          Outcome: <span data-testid="procedures-appropriateness-outcome">{checkQ.data.outcome}</span>
                        </p>
                        {checkQ.data.detections.length === 0 ? (
                          <p className="mt-1 text-xs text-muted-foreground">No detections raised.</p>
                        ) : (
                          <ul className="mt-2 space-y-1.5">
                            {checkQ.data.detections.map((d) => (
                              <li
                                key={d.code}
                                className={`rounded border p-2 text-xs ${DISPOSITION_STYLE[d.disposition] ?? "border-gray-200 bg-gray-50"}`}
                              >
                                <div className="flex items-center gap-1 font-medium">
                                  {d.disposition === "BLOCK" && <ShieldAlert className="h-3.5 w-3.5" />}
                                  {d.message}
                                </div>
                                <div className="mt-0.5">Next: {d.suggestedAction}</div>
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    ) : null}
                  </div>
                )}
              </div>
            ) : null}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
