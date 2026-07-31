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
  useCompetence,
  useSafetyPauseTemplate,
  useSedationLevel,
  useRecoverySetting,
  useAftercareTemplate,
  useAnalyticsIndicators,
  type AppropriatenessRequest,
} from "@/hooks/queries/useProceduresCatalogue";

const SIDE_OPTIONS = ["LEFT", "RIGHT", "BILATERAL", "MIDLINE", "NOT_APPLICABLE", "MULTIPLE"] as const;

const DISPOSITION_STYLE: Record<string, string> = {
  BLOCK: "border-danger/40 bg-danger/5 text-danger",
  PROPOSE_ALTERNATIVE: "border-amber-300 bg-amber-50 text-amber-800",
  CLARIFY: "border-blue-200 bg-blue-50 text-blue-800",
};

/**
 * Wave P-R2 — safety-pause, sedation, recovery and aftercare panels for the selected catalogue
 * entry. A separate component (not inlined into the page body) so the four hooks it calls are
 * scoped to the current selection and reset cleanly when `selectedCode` changes.
 *
 * Each panel renders one of three states, same discipline as the rest of this page:
 * unavailable (isError, a real fetch failure) vs not-declared (the linkage field itself is
 * null — an honest gap, not a failure) vs resolved. A panel never renders nothing silently for
 * an error; only for a genuinely undeclared linkage does it show the quieter "not declared" copy.
 */
function PostProcedurePanels({
  safetyPauseTemplateCode,
  defaultSedationLevelCode,
  defaultRecoverySettingCode,
  aftercareTemplateCode,
  defaultAftercareTemplateCode,
}: {
  safetyPauseTemplateCode: string | null;
  defaultSedationLevelCode: string | null;
  defaultRecoverySettingCode: string | null;
  aftercareTemplateCode: string | null;
  defaultAftercareTemplateCode: string | null;
}) {
  const safetyPauseQ = useSafetyPauseTemplate(safetyPauseTemplateCode);
  const sedationQ = useSedationLevel(defaultSedationLevelCode);
  const recoveryQ = useRecoverySetting(defaultRecoverySettingCode);

  // Two-step resolution, done here (not inside the hook) so the caller can see which code
  // actually answered: try the SPECIFIC per-procedure aftercare code first; only fall back to
  // the coarse setting-class code if the specific one is absent or fails to resolve (most of
  // the catalogue's remaining ~27 specific codes have no template row yet — see V007).
  const specificAftercareQ = useAftercareTemplate(aftercareTemplateCode);
  const useFallback = !aftercareTemplateCode || specificAftercareQ.isError;
  const fallbackAftercareQ = useAftercareTemplate(useFallback ? defaultAftercareTemplateCode : null);
  const aftercareResolved = specificAftercareQ.data ?? fallbackAftercareQ.data;
  const aftercareIsFromFallback = !specificAftercareQ.data && !!fallbackAftercareQ.data;
  const aftercareStillUnavailable =
    useFallback && defaultAftercareTemplateCode && fallbackAftercareQ.isError;

  return (
    <div className="mt-4 space-y-3" data-testid="procedures-postprocedure-panels">
      {safetyPauseTemplateCode && (
        <div className="rounded border border-gray-100 p-2 text-xs" data-testid="procedures-safety-pause-panel">
          <h5 className="font-semibold uppercase text-muted-foreground">Safety pause</h5>
          {safetyPauseQ.isLoading ? (
            <p className="mt-1 text-muted-foreground">Loading…</p>
          ) : safetyPauseQ.isError ? (
            <p className="mt-1 text-danger" role="alert">Could not load the safety-pause template.</p>
          ) : safetyPauseQ.data ? (
            <ul className="mt-1 space-y-0.5">
              {safetyPauseQ.data.confirmationItems.map((item) => (
                <li key={item.confirmationItem}>• {item.promptText}</li>
              ))}
            </ul>
          ) : null}
        </div>
      )}

      {defaultSedationLevelCode && (
        <div className="rounded border border-gray-100 p-2 text-xs" data-testid="procedures-sedation-panel">
          <h5 className="font-semibold uppercase text-muted-foreground">Sedation</h5>
          {sedationQ.isLoading ? (
            <p className="mt-1 text-muted-foreground">Loading…</p>
          ) : sedationQ.isError ? (
            <p className="mt-1 text-danger" role="alert">Could not load the sedation level.</p>
          ) : sedationQ.data ? (
            <div className="mt-1">
              <p>{sedationQ.data.levelLabel} — {sedationQ.data.monitoringRequired}</p>
              {sedationQ.data.rescueCapability && (
                <p className="mt-0.5 text-muted-foreground">
                  Rescue capability required: {sedationQ.data.rescueCapability.levelLabel}
                </p>
              )}
            </div>
          ) : null}
        </div>
      )}

      {defaultRecoverySettingCode && (
        <div className="rounded border border-gray-100 p-2 text-xs" data-testid="procedures-recovery-panel">
          <h5 className="font-semibold uppercase text-muted-foreground">Recovery</h5>
          {recoveryQ.isLoading ? (
            <p className="mt-1 text-muted-foreground">Loading…</p>
          ) : recoveryQ.isError ? (
            <p className="mt-1 text-danger" role="alert">Could not load the recovery setting.</p>
          ) : recoveryQ.data ? (
            <p className="mt-1">
              {recoveryQ.data.settingLabel} — {recoveryQ.data.dischargeReadinessCriteria}
            </p>
          ) : null}
        </div>
      )}

      {(aftercareTemplateCode || defaultAftercareTemplateCode) && (
        <div className="rounded border border-gray-100 p-2 text-xs" data-testid="procedures-aftercare-panel">
          <h5 className="font-semibold uppercase text-muted-foreground">Aftercare</h5>
          {specificAftercareQ.isLoading || fallbackAftercareQ.isLoading ? (
            <p className="mt-1 text-muted-foreground">Loading…</p>
          ) : aftercareStillUnavailable ? (
            <p className="mt-1 text-danger" role="alert">Could not load an aftercare template.</p>
          ) : aftercareResolved ? (
            <div className="mt-1">
              {aftercareIsFromFallback && (
                <p className="mb-1 text-[10px] uppercase text-muted-foreground">
                  Generic {aftercareResolved.applicableSetting ?? ""} guidance — a procedure-specific
                  template ({aftercareTemplateCode}) has not been authored yet.
                </p>
              )}
              {aftercareResolved.contentMaturity === "ENGINEERING_SEED" && (
                <p className="mb-1 text-[10px] uppercase text-amber-700">
                  Engineering seed content — pending MoHCC ratification of the wording.
                </p>
              )}
              <ul className="space-y-0.5">
                {aftercareResolved.instructions.map((i) => (
                  <li key={i.instructionKind}>• {i.instructionText}</li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="mt-1 text-muted-foreground">No aftercare template declared for this procedure yet.</p>
          )}
        </div>
      )}
    </div>
  );
}

const COMPUTATION_STATUS_STYLE: Record<string, string> = {
  COMPUTED: "bg-emerald-50 text-emerald-800",
  PARTIAL: "bg-amber-50 text-amber-800",
  NOT_YET_INSTRUMENTED: "bg-gray-100 text-gray-600",
};

/**
 * Wave SB-3 — P14 analytics indicator catalogue panel. Indicator DEFINITIONS and their honest
 * computation status, not computed numbers: computationStatus and gapReason are rendered
 * verbatim, because "this number does not exist yet, and here is why" is the deliverable —
 * hiding a NOT_YET_INSTRUMENTED row would fabricate an analytics capability that isn't there.
 */
function AnalyticsIndicatorsPanel() {
  const indicatorsQ = useAnalyticsIndicators();

  return (
    <div className="mt-6 rounded-lg border border-gray-100 p-4" data-testid="procedures-analytics-panel">
      <h3 className="text-sm font-semibold">Pipeline analytics indicators (§26)</h3>
      {indicatorsQ.isLoading ? (
        <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading indicator catalogue…
        </div>
      ) : indicatorsQ.isError ? (
        <div
          className="flex items-center gap-2 p-4 text-sm text-danger"
          role="alert"
          data-testid="procedures-analytics-unavailable"
        >
          <AlertTriangle className="h-5 w-5" />
          Could not reach the indicator catalogue. This is not the same as there being no
          indicators — try again, or report if it persists.
        </div>
      ) : indicatorsQ.data && !Array.isArray(indicatorsQ.data.indicators) ? (
        // A 200 whose body is not the indicator-summary shape is UNAVAILABLE, not empty — a
        // malformed upstream must never render as "no indicators" (and must never crash the
        // page; this branch was added after exactly that crash under a blanket fetch mock).
        <div
          className="flex items-center gap-2 p-4 text-sm text-danger"
          role="alert"
          data-testid="procedures-analytics-malformed"
        >
          <AlertTriangle className="h-5 w-5" />
          The indicator catalogue answered in an unexpected shape — treating it as unavailable,
          not as empty.
        </div>
      ) : indicatorsQ.data ? (
        <div className="mt-2">
          <div className="flex flex-wrap gap-4 text-sm" data-testid="procedures-analytics-summary">
            <span>Total: <strong>{indicatorsQ.data.total}</strong></span>
            <span className="text-emerald-800">Computed: <strong>{indicatorsQ.data.computed}</strong></span>
            <span className="text-amber-800">Partial: <strong>{indicatorsQ.data.partial}</strong></span>
            <span className="text-gray-600">
              Not yet instrumented: <strong>{indicatorsQ.data.notYetInstrumented}</strong>
            </span>
          </div>
          {indicatorsQ.data.indicators.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground" data-testid="procedures-analytics-empty">
              The indicator catalogue is genuinely empty.
            </p>
          ) : (
            <div className="mt-3 overflow-x-auto">
              <table className="w-full text-left text-xs" data-testid="procedures-analytics-table">
                <thead>
                  <tr className="border-b border-gray-100 uppercase text-muted-foreground">
                    <th className="py-1.5 pr-3">Indicator</th>
                    <th className="py-1.5 pr-3">Status</th>
                    <th className="py-1.5 pr-3">Executable via</th>
                    <th className="py-1.5">Gap reason</th>
                  </tr>
                </thead>
                <tbody>
                  {indicatorsQ.data.indicators.map((ind) => (
                    <tr key={ind.indicatorCode} className="border-b border-gray-50 align-top">
                      <td className="py-1.5 pr-3">
                        <div className="font-medium">{ind.indicatorName}</div>
                        <div className="text-muted-foreground">{ind.indicatorCode}</div>
                      </td>
                      <td className="py-1.5 pr-3">
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${
                            COMPUTATION_STATUS_STYLE[ind.computationStatus] ?? "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {ind.computationStatus}
                        </span>
                        {ind.delegatedOutOfScope && (
                          <span className="ml-1 text-[10px] uppercase text-muted-foreground">delegated</span>
                        )}
                      </td>
                      <td className="py-1.5 pr-3">{ind.executableVia ?? "—"}</td>
                      <td className="py-1.5 text-muted-foreground">{ind.gapReason ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}

export default function ProceduresCataloguePage() {
  const [specialty, setSpecialty] = useState("");
  const [category, setCategory] = useState("");
  const [q, setQ] = useState("");
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [side, setSide] = useState<string>("");
  const [checkRequest, setCheckRequest] = useState<AppropriatenessRequest | null>(null);
  /** Typed provider id — do not invent a session provider; competence stays unread until entered. */
  const [providerId, setProviderId] = useState("");

  const searchQ = useCatalogueSearch({ specialty, category, q });
  const detailQ = useCatalogueDetail(selectedCode);
  const checkQ = useAppropriatenessCheck(checkRequest);
  const competenceQ = useCompetence(providerId.trim() || null, selectedCode);

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

                <PostProcedurePanels
                  safetyPauseTemplateCode={detailQ.data.safetyPauseTemplate}
                  defaultSedationLevelCode={detailQ.data.defaultSedationLevelCode}
                  defaultRecoverySettingCode={detailQ.data.defaultRecoverySettingCode}
                  aftercareTemplateCode={detailQ.data.aftercareTemplate}
                  defaultAftercareTemplateCode={detailQ.data.defaultAftercareTemplateCode}
                />

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

                <div className="mt-4" data-testid="procedures-competence-panel">
                  <label className="text-xs font-semibold uppercase text-muted-foreground">
                    Provider competence check
                  </label>
                  <input
                    type="text"
                    value={providerId}
                    onChange={(e) => setProviderId(e.target.value)}
                    placeholder="Provider id (required — not invented from session)"
                    className="mt-1 block w-full rounded-md border border-gray-200 px-3 py-2 text-sm"
                    data-testid="procedures-competence-provider"
                  />
                  {!providerId.trim() ? (
                    <p className="mt-2 text-xs text-muted-foreground">
                      Enter a provider id to check competence for this catalogue entry. Until then,
                      competence is not established.
                    </p>
                  ) : competenceQ.isLoading ? (
                    <div className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" /> Checking competence…
                    </div>
                  ) : competenceQ.isError ? (
                    <p className="mt-2 text-sm text-danger" role="alert" data-testid="procedures-competence-unavailable">
                      Could not establish competence — do not treat this as permitted.
                    </p>
                  ) : competenceQ.data ? (
                    <div className="mt-2 rounded border border-gray-100 p-2 text-xs" data-testid="procedures-competence-result">
                      <p className="font-medium">
                        Capacity:{" "}
                        <span data-testid="procedures-competence-capacity">{competenceQ.data.capacity}</span>
                        {competenceQ.data.capacity === "UNKNOWN" || !competenceQ.data.mayProceedAlone ? (
                          <span className="ml-2 text-danger"> — not established to proceed alone</span>
                        ) : null}
                      </p>
                      <p className="mt-0.5 text-muted-foreground">
                        May proceed alone: {competenceQ.data.mayProceedAlone ? "yes" : "no"}
                        {competenceQ.data.countersignatureRequired ? " · countersignature required" : ""}
                      </p>
                      {competenceQ.data.reasons?.length ? (
                        <ul className="mt-1 list-inside list-disc text-muted-foreground">
                          {competenceQ.data.reasons.map((r) => (
                            <li key={r}>{r}</li>
                          ))}
                        </ul>
                      ) : null}
                    </div>
                  ) : null}
                </div>

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

        <AnalyticsIndicatorsPanel />
      </PageShell>
    </AppLayout>
  );
}
