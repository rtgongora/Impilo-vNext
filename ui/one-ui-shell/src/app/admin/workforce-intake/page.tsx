"use client";

/**
 * Workforce Intake — staged MoHCC staff bootstrap wizard.
 * Route: /admin/workforce-intake
 *
 * Upload staff CSV → column mapping + validation report → duplicate detection
 * + Health-ID matching preview → confirm & approve → execution (Provider IDs
 * via VARAPI + workforce profiles via Vashandi) → invitations & completion.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, Loader2, Upload, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  intakeAttributes,
  useApplyIntakeMapping,
  useApproveIntakeBatch,
  useCreateIntakeBatch,
  useExecuteIntakeBatch,
  useRunIntakeMatch,
  useWorkforceIntakeStatus,
  type IntakeMatchRow,
  type IntakeMatchSummary,
  type IntakeStatus,
  type IntakeValidationReport,
} from "@/hooks/queries/useWorkforceIntake";
import {
  CANONICAL_COLUMNS,
  STEP_LABELS,
  WIZARD_STEPS,
  countCsvDataRows,
  missingRequiredColumns,
  parseCsvHeaders,
  stepIndex,
  suggestMapping,
  type WizardStep,
} from "@/lib/workforce-intake/wizard";

const MAX_ROWS = 2000;

function matchBadgeClass(status: string): string {
  switch (status) {
    case "MATCHED":
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    case "AMBIGUOUS":
      return "bg-amber-50 text-amber-700 border-amber-200";
    case "DUPLICATE_IN_BATCH":
      return "bg-rose-50 text-rose-700 border-rose-200";
    default:
      return "bg-neutral-100 text-foreground border-border";
  }
}

function execBadgeClass(status: string): string {
  if (status === "DONE") return "bg-emerald-50 text-emerald-700 border-emerald-200";
  if (status.startsWith("FAILED_")) return "bg-rose-50 text-rose-700 border-rose-200";
  if (status.startsWith("BLOCKED_")) return "bg-amber-50 text-amber-700 border-amber-200";
  return "bg-neutral-100 text-foreground border-border";
}

export default function WorkforceIntakePage() {
  const [step, setStep] = useState<WizardStep>("upload");
  const [organisationId, setOrganisationId] = useState("");
  const [fileName, setFileName] = useState("workforce-intake.csv");
  const [csvContent, setCsvContent] = useState("");
  const [batchId, setBatchId] = useState<string | null>(null);
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const [validationReport, setValidationReport] = useState<IntakeValidationReport | null>(null);
  const [matchSummary, setMatchSummary] = useState<IntakeMatchSummary | null>(null);
  const [matchRows, setMatchRows] = useState<IntakeMatchRow[]>([]);
  const [journeyError, setJourneyError] = useState<string | null>(null);

  const createBatch = useCreateIntakeBatch();
  const applyMapping = useApplyIntakeMapping(batchId);
  const runMatch = useRunIntakeMatch(batchId);
  const approveBatch = useApproveIntakeBatch(batchId);
  const executeBatch = useExecuteIntakeBatch(batchId);
  const statusQuery = useWorkforceIntakeStatus(batchId, step === "execute");

  const sourceHeaders = useMemo(() => parseCsvHeaders(csvContent), [csvContent]);
  const rowCount = useMemo(() => countCsvDataRows(csvContent), [csvContent]);
  const missingRequired = useMemo(() => missingRequiredColumns(mapping), [mapping]);
  const statusData = intakeAttributes(statusQuery.data)?.data as IntakeStatus | undefined;

  const busy =
    createBatch.isPending || applyMapping.isPending || runMatch.isPending ||
    approveBatch.isPending || executeBatch.isPending;

  function fail(message?: string | null, fallback = "The step could not be completed.") {
    setJourneyError(message || fallback);
  }

  async function handleUpload() {
    setJourneyError(null);
    const response = await createBatch.mutateAsync({ organisationId, fileName, csvContent });
    const attrs = intakeAttributes(response);
    if (attrs?.integrationStatus !== "live") {
      fail(attrs?.friendlyMessage, "Upload failed.");
      return;
    }
    const batch = (attrs.data as { batch?: { id?: string } } | undefined)?.batch;
    if (!batch?.id) {
      fail("The batch was staged but no batch id was returned.");
      return;
    }
    setBatchId(batch.id);
    setMapping(suggestMapping(sourceHeaders));
    setStep("mapping");
  }

  async function handleMapping() {
    setJourneyError(null);
    const response = await applyMapping.mutateAsync(mapping);
    const attrs = intakeAttributes(response);
    if (attrs?.integrationStatus !== "live") {
      fail(attrs?.friendlyMessage, "Column mapping failed.");
      return;
    }
    const report = (attrs.data as { validationReport?: IntakeValidationReport } | undefined)
      ?.validationReport;
    setValidationReport(report ?? null);
  }

  async function handleMatch() {
    setJourneyError(null);
    const response = await runMatch.mutateAsync();
    const attrs = intakeAttributes(response);
    if (attrs?.integrationStatus !== "live") {
      fail(attrs?.friendlyMessage, "Matching failed.");
      return;
    }
    const data = attrs.data as
      | { summary?: IntakeMatchSummary; rows?: IntakeMatchRow[] }
      | undefined;
    setMatchSummary(data?.summary ?? null);
    setMatchRows(data?.rows ?? []);
    setStep("match");
  }

  async function handleApprove() {
    setJourneyError(null);
    const response = await approveBatch.mutateAsync();
    const attrs = intakeAttributes(response);
    if (attrs?.status !== "completed") {
      fail(attrs?.friendlyMessage ?? attrs?.blockedReason, "Approval failed.");
      return;
    }
    setStep("execute");
  }

  async function handleExecute() {
    setJourneyError(null);
    const response = await executeBatch.mutateAsync();
    const attrs = intakeAttributes(response);
    if (attrs?.integrationStatus !== "live") {
      fail(attrs?.friendlyMessage, "Execution failed to start.");
      return;
    }
    const stage = (attrs.data as { stage?: string } | undefined)?.stage;
    if (stage === "COMPLETED") setStep("complete");
  }

  return (
    <AppLayout>
      <PageShell
        title="Workforce Intake"
        subtitle="Bootstrap MoHCC staff at scale — Provider IDs, workforce profiles and activation invitations from one staged CSV journey"
      >
        <div className="mb-4 flex items-center justify-between">
          <FeatureMaturityBadge
            status="connected"
            detail="Staged in workforce-governance; executes against VARAPI + Vashandi"
          />
          <Link href="/work/vashandi/imports" className="text-sm text-primary hover:text-primary-hover">
            Advanced: raw import bridge
          </Link>
        </div>

        {/* Stepper */}
        <ol className="mb-6 flex flex-wrap gap-2">
          {WIZARD_STEPS.map((s, i) => {
            const active = s === step;
            const done = stepIndex(step) > i;
            return (
              <li
                key={s}
                className={`flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium ${
                  active
                    ? "border-primary bg-primary/10 text-primary"
                    : done
                      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                      : "border-border bg-card text-muted-foreground"
                }`}
              >
                {done ? <CheckCircle2 className="h-3.5 w-3.5" /> : <span>{i + 1}.</span>}
                {STEP_LABELS[s]}
              </li>
            );
          })}
        </ol>

        {journeyError ? (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-4 text-sm text-red-700">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{journeyError}</span>
          </div>
        ) : null}

        {/* Step 1 — Upload */}
        {step === "upload" ? (
          <section className="space-y-4 rounded-lg border border-border bg-card p-6">
            <p className="text-sm text-muted-foreground">
              Upload a staff list CSV. Expected columns: nationalId, givenName, familyName, profession
              (required) plus cadre, councilCode, councilNumber, facilityCode, email, phone, healthId
              (optional). Maximum {MAX_ROWS} rows per batch.
            </p>
            <label className="block space-y-1 text-sm">
              <span className="font-medium text-foreground">Organisation ID</span>
              <input
                className="w-full rounded-lg border border-border px-3 py-2"
                value={organisationId}
                onChange={(e) => setOrganisationId(e.target.value)}
                placeholder="Workforce-governance organisation UUID"
              />
            </label>
            <label className="block space-y-1 text-sm">
              <span className="font-medium text-foreground">Staff CSV file</span>
              <input
                type="file"
                accept=".csv,text/csv"
                className="block w-full text-sm text-muted-foreground"
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  setFileName(file.name);
                  setCsvContent(await file.text());
                }}
              />
            </label>
            <label className="block space-y-1 text-sm">
              <span className="font-medium text-foreground">Or paste CSV content</span>
              <textarea
                className="min-h-[160px] w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
                value={csvContent}
                onChange={(e) => setCsvContent(e.target.value)}
                placeholder={"nationalId,givenName,familyName,profession,email\n63-123456A63,Rudo,Moyo,NURSE,rudo.moyo@mohcc.gov.zw"}
              />
            </label>
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">
                {rowCount} data row{rowCount === 1 ? "" : "s"} detected
                {rowCount > MAX_ROWS ? ` — over the ${MAX_ROWS}-row cap, split the file` : ""}
              </span>
              <button
                onClick={handleUpload}
                disabled={busy || !organisationId.trim() || rowCount === 0 || rowCount > MAX_ROWS}
                className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {createBatch.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                Stage batch
              </button>
            </div>
          </section>
        ) : null}

        {/* Step 2 — Mapping & validation */}
        {step === "mapping" ? (
          <section className="space-y-4 rounded-lg border border-border bg-card p-6">
            <p className="text-sm text-muted-foreground">
              Map your CSV headers to the canonical intake columns, then validate. Required targets:
              nationalid, givenname, familyname, profession.
            </p>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-background text-left">
                    <th className="px-3 py-2 font-medium text-muted-foreground">CSV header</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Maps to</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {sourceHeaders.map((header) => (
                    <tr key={header}>
                      <td className="px-3 py-2 font-mono text-xs">{header}</td>
                      <td className="px-3 py-2">
                        <select
                          className="rounded-lg border border-border px-2 py-1 text-sm"
                          value={mapping[header] ?? ""}
                          onChange={(e) =>
                            setMapping((prev) => {
                              const next = { ...prev };
                              if (e.target.value) next[header] = e.target.value;
                              else delete next[header];
                              return next;
                            })
                          }
                        >
                          <option value="">(keep as-is)</option>
                          {CANONICAL_COLUMNS.map((column) => (
                            <option key={column} value={column}>
                              {column}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {missingRequired.length > 0 ? (
              <p className="text-xs text-amber-700">
                Not yet mapped: {missingRequired.join(", ")} — rows missing these will be flagged invalid.
              </p>
            ) : null}
            {validationReport ? (
              <div className="rounded-lg border border-border bg-background p-4 text-sm">
                <p className="font-medium text-foreground">Validation report</p>
                <p className="text-muted-foreground">
                  {validationReport.validRows} valid / {validationReport.invalidRows} invalid of{" "}
                  {validationReport.totalRows} rows.
                </p>
                {validationReport.invalidRows > 0 ? (
                  <ul className="mt-2 list-inside list-disc text-xs text-rose-700">
                    {validationReport.rows
                      .filter((row) => row.validationStatus !== "validated")
                      .slice(0, 10)
                      .map((row) => (
                        <li key={row.rowId}>
                          Row {row.rowNumber}: {row.outcome}
                        </li>
                      ))}
                  </ul>
                ) : null}
              </div>
            ) : null}
            <div className="flex justify-end gap-2">
              <button
                onClick={handleMapping}
                disabled={busy}
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground disabled:opacity-50"
              >
                {applyMapping.isPending ? "Validating…" : "Apply mapping & validate"}
              </button>
              <button
                onClick={handleMatch}
                disabled={busy || !validationReport}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {runMatch.isPending ? "Matching…" : "Run match & dedupe"}
              </button>
            </div>
          </section>
        ) : null}

        {/* Step 3 — Match & dedupe preview */}
        {step === "match" ? (
          <section className="space-y-4 rounded-lg border border-border bg-card p-6">
            {matchSummary ? (
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
                {(
                  [
                    ["Matched", matchSummary.matched],
                    ["New (no match)", matchSummary.noMatch],
                    ["Ambiguous", matchSummary.ambiguous],
                    ["Duplicates in batch", matchSummary.duplicatesInBatch],
                    ["Invalid", matchSummary.invalid],
                  ] as const
                ).map(([label, count]) => (
                  <div key={label} className="rounded-lg border border-border bg-background p-3 text-center">
                    <p className="text-lg font-semibold text-foreground">{count}</p>
                    <p className="text-xs text-muted-foreground">{label}</p>
                  </div>
                ))}
              </div>
            ) : null}
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-background text-left">
                    <th className="px-3 py-2 font-medium text-muted-foreground">Row</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Name</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">National ID</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Match</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Health ID</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {matchRows.map((row) => (
                    <tr key={row.rowId}>
                      <td className="px-3 py-2 text-muted-foreground">{row.rowNumber}</td>
                      <td className="px-3 py-2 text-foreground">
                        {row.payload.givenname} {row.payload.familyname}
                      </td>
                      <td className="px-3 py-2 font-mono text-xs">{row.payload.nationalid ?? "—"}</td>
                      <td className="px-3 py-2">
                        <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${matchBadgeClass(row.matchStatus)}`}>
                          {row.matchStatus}
                        </span>
                      </td>
                      <td className="px-3 py-2 font-mono text-xs">{row.matchedHealthId ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="text-xs text-muted-foreground">
              Ambiguous rows are blocked from execution until resolved; in-batch duplicates are skipped.
              Rows with no match are issued a new person anchor at execution.
            </p>
            <div className="flex justify-end">
              <button
                onClick={handleApprove}
                disabled={busy}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {approveBatch.isPending ? "Approving…" : "Confirm & approve batch"}
              </button>
            </div>
          </section>
        ) : null}

        {/* Steps 4/5 — Execution + tracker */}
        {step === "execute" || step === "complete" ? (
          <section className="space-y-4 rounded-lg border border-border bg-card p-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm text-foreground">
                <Users className="h-4 w-4" />
                <span>
                  Batch stage: <span className="font-semibold">{statusData?.stage ?? "…"}</span>
                </span>
              </div>
              {step === "execute" ? (
                <button
                  onClick={handleExecute}
                  disabled={busy}
                  className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  {executeBatch.isPending
                    ? "Executing…"
                    : statusData?.stage === "PARTIAL"
                      ? "Re-run failed rows"
                      : "Execute intake"}
                </button>
              ) : null}
            </div>
            {statusData?.executionTally && Object.keys(statusData.executionTally).length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {Object.entries(statusData.executionTally).map(([status, count]) => (
                  <span key={status} className={`rounded-full border px-2 py-0.5 text-xs font-medium ${execBadgeClass(status)}`}>
                    {status}: {count}
                  </span>
                ))}
              </div>
            ) : null}
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-background text-left">
                    <th className="px-3 py-2 font-medium text-muted-foreground">Row</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Name</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Provider ID</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Profile</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Invitation</th>
                    <th className="px-3 py-2 font-medium text-muted-foreground">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {(statusData?.rows ?? []).map((row) => (
                    <tr key={row.rowId}>
                      <td className="px-3 py-2 text-muted-foreground">{row.rowNumber}</td>
                      <td className="px-3 py-2 text-foreground">
                        {row.payload.givenname} {row.payload.familyname}
                      </td>
                      <td className="px-3 py-2 font-mono text-xs">{row.providerPublicId ?? "—"}</td>
                      <td className="px-3 py-2 font-mono text-xs">{row.vashandiProfileId ?? "—"}</td>
                      <td className="px-3 py-2 font-mono text-xs">{row.invitationId ?? "—"}</td>
                      <td className="px-3 py-2">
                        <span
                          className={`rounded-full border px-2 py-0.5 text-xs font-medium ${execBadgeClass(row.executionStatus || "")}`}
                          title={row.executionError ?? undefined}
                        >
                          {row.executionStatus || "PENDING"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {step === "complete" ? (
              <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
                <CheckCircle2 className="h-4 w-4" />
                Intake complete. Invited staff activate via the emailed invitation; rows without a
                contact are listed as BLOCKED_MISSING_CONTACT for manual follow-up.
              </div>
            ) : null}
            {statusData?.stage === "COMPLETED" && step === "execute" ? (
              <div className="flex justify-end">
                <button
                  onClick={() => setStep("complete")}
                  className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground"
                >
                  View completion summary
                </button>
              </div>
            ) : null}
          </section>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
