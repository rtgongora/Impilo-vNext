"use client";

/**
 * Facility classification reconciliation — enrich the imported national facility estate against the
 * HPA classification model without discarding imported source truth.
 * Route: /registry/facility-classification | guard: REGISTRY_ADMIN
 *
 * Every field is live from TUSO via the policy-protected experience-bff admin proxy. A suggestion is
 * shown as a suggestion; a definitive HPA label appears only once the profile is confirmed or
 * deterministically resolved. Catalogue gaps and conflicts surface honestly — never a guessed label.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  useClassificationSummary,
  useClassificationProfiles,
  useClassificationProfile,
  useRunEnrichment,
  useSupplyFacts,
  useConfirmClassification,
  useCorrectClassification,
  useBulkConfirm,
  type ClassificationProfile,
} from "@/hooks/queries/useFacilityClassification";

const STATUS_ORDER = [
  "UNMAPPED",
  "MISSING_REQUIRED_FACTS",
  "AMBIGUOUS",
  "SUGGESTED_HIGH_CONFIDENCE",
  "DETERMINISTIC_MULTI_FIELD_MATCH",
  "EXACT_SOURCE_MATCH",
  "HUMAN_CONFIRMED",
  "REJECTED_OR_CORRECTED",
];

function StatusPill({ status }: { status: string }) {
  const tone =
    status === "HUMAN_CONFIRMED" || status === "DETERMINISTIC_MULTI_FIELD_MATCH" || status === "EXACT_SOURCE_MATCH"
      ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200"
      : status === "AMBIGUOUS" || status === "UNMAPPED"
        ? "bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200"
        : "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300";
  return <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${tone}`}>{status}</span>;
}

function ProfileDetail({ facilityId }: { facilityId: number }) {
  const { data, isLoading, isError } = useClassificationProfile(facilityId);
  const supplyFacts = useSupplyFacts(facilityId);
  const confirm = useConfirmClassification(facilityId);
  const correct = useCorrectClassification(facilityId);
  const [careSetting, setCareSetting] = useState("");
  const [bedCount, setBedCount] = useState("");
  const [evidence, setEvidence] = useState("");

  if (isLoading) return <div className="p-4 text-sm text-muted-foreground">Loading profile…</div>;
  if (isError || !data?.data) return <div className="p-4 text-sm text-red-600">Could not load the classification profile.</div>;

  const p = data.data.profile;
  const canConfirm = p.hpaClassificationLabel != null || (p.hpaClass === "NOT_APPLICABLE" && p.hpaClassJustification);

  return (
    <div className="space-y-4 p-4">
      <div className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <div className="font-semibold text-muted-foreground">Imported source (preserved)</div>
          <dl className="mt-1 space-y-0.5">
            <div>Type: <span className="font-mono">{p.sourceFacilityType ?? "—"}</span></div>
            <div>Ownership: <span className="font-mono">{p.sourceOwnership ?? "—"}</span></div>
            <div>Level: <span className="font-mono">{p.sourceLevel ?? "—"}</span></div>
            <div>Bed capacity: <span className="font-mono">{p.sourceBedCapacity ?? "—"}</span></div>
          </dl>
        </div>
        <div>
          <div className="font-semibold text-muted-foreground">Derived classification</div>
          <dl className="mt-1 space-y-0.5">
            <div>Canonical: <span className="font-mono">{p.canonicalType ?? "unresolved"}</span></div>
            <div>HPA class: <span className="font-mono">{p.hpaClass}</span></div>
            <div>Care setting: <span className="font-mono">{p.careSetting}</span></div>
            <div>Bed band: <span className="font-mono">{p.bedBand ?? "—"}</span> {p.bedBandBasis ? `(${p.bedBandBasis})` : ""}</div>
            <div>Label: <span className="font-mono">{p.hpaClassificationLabel ?? "— not resolved —"}</span></div>
            <div>Status: <StatusPill status={p.classificationStatus} /></div>
          </dl>
        </div>
      </div>

      {p.requiredFacts && p.requiredFacts.length > 0 && (
        <div className="rounded border border-amber-300 bg-amber-50 p-3 text-sm dark:border-amber-800 dark:bg-amber-950/40">
          <div className="font-semibold">Needs these facts before a definitive label</div>
          <div className="mt-1">{p.requiredFacts.join(", ")}</div>
        </div>
      )}

      {p.suggestion && p.suggestion.length > 0 && (
        <div className="rounded border border-slate-200 p-3 text-sm dark:border-slate-700">
          <div className="font-semibold text-muted-foreground">Suggestions (not applied)</div>
          <ul className="mt-1 list-disc pl-5">
            {p.suggestion.map((s, i) => (
              <li key={i}>
                Class {String(s.classCode)} — {String(s.label)} {s.condition ? `(${String(s.condition)})` : ""}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="rounded border border-slate-200 p-3 dark:border-slate-700">
        <div className="mb-2 text-sm font-semibold">Supply facts</div>
        <div className="flex flex-wrap items-end gap-2">
          <label className="text-xs">
            Care setting
            <select value={careSetting} onChange={(e) => setCareSetting(e.target.value)} className="mt-0.5 block rounded border px-2 py-1 text-sm">
              <option value="">—</option>
              <option value="OUTPATIENT_ONLY">OUTPATIENT_ONLY</option>
              <option value="INPATIENT">INPATIENT</option>
            </select>
          </label>
          <label className="text-xs">
            Confirmed bed count
            <input type="number" value={bedCount} onChange={(e) => setBedCount(e.target.value)} className="mt-0.5 block w-28 rounded border px-2 py-1 text-sm" />
          </label>
          <label className="text-xs grow">
            Evidence note
            <input value={evidence} onChange={(e) => setEvidence(e.target.value)} className="mt-0.5 block w-full rounded border px-2 py-1 text-sm" />
          </label>
          <button
            onClick={() =>
              supplyFacts.mutate({
                careSetting: careSetting || undefined,
                bedCountConfirmed: bedCount ? Number(bedCount) : undefined,
                evidenceNote: evidence || undefined,
              })
            }
            disabled={supplyFacts.isPending || (!careSetting && !bedCount)}
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
          >
            Save facts & re-evaluate
          </button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          onClick={() => confirm.mutate(undefined)}
          disabled={!canConfirm || confirm.isPending}
          title={canConfirm ? "Confirm this classification" : "Resolve the label first"}
          className="rounded bg-emerald-600 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        >
          Confirm classification
        </button>
        <button
          onClick={() => {
            const label = window.prompt("Corrected catalogue label (leave blank for NOT_APPLICABLE):", p.hpaClassificationLabel ?? "");
            if (label === null) return;
            const hpaClass = window.prompt("Corrected HPA class (A/B/C/NOT_APPLICABLE):", p.hpaClass) ?? p.hpaClass;
            const justification = hpaClass === "NOT_APPLICABLE" ? window.prompt("Justification (required for NOT_APPLICABLE):") ?? "" : undefined;
            correct.mutate({ hpaClass, hpaClassificationLabel: label || undefined, justification });
          }}
          disabled={correct.isPending}
          className="rounded border px-3 py-1.5 text-sm"
        >
          Correct…
        </button>
        {p.reclassificationReviewRequired && (
          <span className="self-center text-xs text-amber-700 dark:text-amber-300">
            Reclassification review flagged — certificate untouched
          </span>
        )}
      </div>

      <div>
        <div className="mb-1 text-sm font-semibold text-muted-foreground">History</div>
        <ul className="space-y-1 text-xs">
          {data.data.history.map((h) => (
            <li key={h.historyId} className="font-mono">
              {new Date(h.createdAt).toLocaleString()} · {h.changeKind} · {h.actor ?? "system"}
              {h.note ? ` · ${h.note}` : ""}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

export default function FacilityClassificationPage() {
  const [status, setStatus] = useState("");
  const [selected, setSelected] = useState<number | null>(null);
  const summary = useClassificationSummary();
  const profiles = useClassificationProfiles({ status: status || undefined, size: 100 });
  const runEnrichment = useRunEnrichment();
  const bulkConfirm = useBulkConfirm();

  const rows: ClassificationProfile[] = profiles.data?.data?.items ?? [];
  const counts = summary.data?.data?.byStatus ?? {};
  const orderedCounts = useMemo(
    () => STATUS_ORDER.filter((s) => counts[s] != null).map((s) => [s, counts[s]] as const),
    [counts],
  );

  return (
    <AppLayout>
      <PageShell
        title="Facility classification reconciliation"
        subtitle="Enrich the imported national estate against the HPA classification model — source truth preserved"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <FeatureMaturityBadge status="connected" detail="Backed by TUSO facility_regulatory_profile via the admin proxy" />
          <div className="flex gap-2">
            <button
              onClick={() => runEnrichment.mutate(false)}
              disabled={runEnrichment.isPending}
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            >
              {runEnrichment.isPending ? "Running…" : "Run enrichment backfill"}
            </button>
            <button
              onClick={() => bulkConfirm.mutate(["DETERMINISTIC_MULTI_FIELD_MATCH"])}
              disabled={bulkConfirm.isPending}
              title="Only deterministic matches can be bulk-confirmed"
              className="rounded border px-3 py-1.5 text-sm disabled:opacity-50"
            >
              Bulk-confirm deterministic
            </button>
          </div>
        </div>

        {/* Counts dashboard */}
        <div className="mb-4 flex flex-wrap gap-2">
          <button
            onClick={() => setStatus("")}
            className={`rounded border px-3 py-1.5 text-sm ${status === "" ? "border-primary bg-primary/10" : ""}`}
          >
            All {summary.data?.data?.total != null ? `(${summary.data.data.total})` : ""}
          </button>
          {orderedCounts.map(([s, n]) => (
            <button
              key={s}
              onClick={() => setStatus(s)}
              className={`rounded border px-3 py-1.5 text-sm ${status === s ? "border-primary bg-primary/10" : ""}`}
            >
              {s} ({n})
            </button>
          ))}
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {/* Queue */}
          <div className="overflow-x-auto rounded border border-slate-200 dark:border-slate-700">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase text-muted-foreground dark:bg-slate-800">
                <tr>
                  <th className="px-3 py-2">Facility</th>
                  <th className="px-3 py-2">Source type</th>
                  <th className="px-3 py-2">Class</th>
                  <th className="px-3 py-2">Status</th>
                </tr>
              </thead>
              <tbody>
                {profiles.isLoading && (
                  <tr><td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">Loading…</td></tr>
                )}
                {!profiles.isLoading && rows.length === 0 && (
                  <tr><td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">
                    No profiles yet. Run the enrichment backfill to populate the estate.
                  </td></tr>
                )}
                {rows.map((r) => (
                  <tr
                    key={r.facilityId}
                    onClick={() => setSelected(r.facilityId)}
                    className={`cursor-pointer border-t border-slate-100 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/50 ${
                      selected === r.facilityId ? "bg-primary/5" : ""
                    }`}
                  >
                    <td className="px-3 py-2 font-mono">#{r.facilityId}</td>
                    <td className="px-3 py-2 font-mono">{r.sourceFacilityType ?? "—"}</td>
                    <td className="px-3 py-2">{r.hpaClass}</td>
                    <td className="px-3 py-2"><StatusPill status={r.classificationStatus} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Detail */}
          <div className="rounded border border-slate-200 dark:border-slate-700">
            {selected == null ? (
              <div className="p-6 text-center text-sm text-muted-foreground">
                Select a facility to reconcile its classification.
              </div>
            ) : (
              <ProfileDetail facilityId={selected} />
            )}
          </div>
        </div>

        <div className="mt-4 text-xs text-muted-foreground">
          <Link href="/registry" className="hover:text-primary">← Registry hub</Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
