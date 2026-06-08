"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { CheckCircle2, Loader2, ScanLine } from "lucide-react";
import { useCorrelateStudy, useImagingStudies } from "@/hooks/queries/useImaging";

interface ImagingOrderCorrelatePanelProps {
  patientId: string;
  orderId: string;
}

function readStudyId(study: unknown): string | null {
  if (!study || typeof study !== "object") return null;
  const row = study as Record<string, unknown>;
  const attrs = (row.attributes as Record<string, unknown> | undefined) ?? row;
  const id = attrs.id ?? row.id ?? attrs.studyId ?? attrs.study_id;
  return id != null ? String(id) : null;
}

function readStudyLabel(study: unknown): string {
  if (!study || typeof study !== "object") return "Imaging study";
  const row = study as Record<string, unknown>;
  const attrs = (row.attributes as Record<string, unknown> | undefined) ?? row;
  const label =
    attrs.modality ??
    attrs.bodyPart ??
    attrs.body_part ??
    attrs.studyDescription ??
    attrs.study_description ??
    attrs.accessionNumber ??
    attrs.accession_number;
  return label != null ? String(label) : "Imaging study";
}

export function ImagingOrderCorrelatePanel({ patientId, orderId }: ImagingOrderCorrelatePanelProps) {
  const { data, isLoading } = useImagingStudies(patientId);
  const correlate = useCorrelateStudy();
  const [selectedStudyId, setSelectedStudyId] = useState<string>("");
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const studies = useMemo(() => {
    const payload = data as { data?: unknown[] } | undefined;
    return payload?.data ?? [];
  }, [data]);

  async function handleCorrelate() {
    if (!selectedStudyId) {
      setError("Select a governed study to correlate.");
      return;
    }
    setError(null);
    setSuccess(null);
    try {
      await correlate.mutateAsync({
        studyId: selectedStudyId,
        body: { oros_order_id: orderId, orosOrderId: orderId },
      });
      setSuccess("Order linked to imaging study — open viewer to review.");
    } catch {
      setError("Correlation failed — verify PACS adapter and order id.");
    }
  }

  return (
    <section
      className="mb-4 rounded-xl border border-sky-200 bg-sky-50 px-4 py-3"
      data-testid="imaging-order-correlate-panel"
    >
      <div className="flex items-start gap-2">
        <ScanLine className="mt-0.5 h-4 w-4 text-sky-700" aria-hidden />
        <div className="flex-1 space-y-2">
          <p className="text-sm font-medium text-sky-950">
            Correlate imaging order <code className="rounded bg-white/80 px-1">{orderId}</code> to PACS study
          </p>
          <p className="text-xs text-sky-900/80">
            Posts to <code className="rounded bg-white/80 px-1">POST /internal/v1/imaging/studies/{"{id}"}/correlate</code>{" "}
            with the OROS order anchor, then launch the viewer for result review.
          </p>
          {isLoading ? (
            <div className="flex items-center gap-2 text-xs text-sky-800">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Loading governed studies…
            </div>
          ) : studies.length === 0 ? (
            <p className="text-xs text-sky-800">
              No governed studies yet — sync hierarchy from PACS or complete image acquisition first.
            </p>
          ) : (
            <div className="flex flex-wrap items-end gap-2">
              <label className="text-xs font-medium text-sky-900">
                Study
                <select
                  value={selectedStudyId}
                  onChange={(e) => setSelectedStudyId(e.target.value)}
                  className="mt-1 block min-w-[220px] rounded-lg border border-sky-200 bg-white px-3 py-2 text-sm"
                >
                  <option value="">Select study…</option>
                  {studies.map((study) => {
                    const id = readStudyId(study);
                    if (!id) return null;
                    return (
                      <option key={id} value={id}>
                        {readStudyLabel(study)}
                      </option>
                    );
                  })}
                </select>
              </label>
              <button
                type="button"
                onClick={() => void handleCorrelate()}
                disabled={correlate.isPending}
                className="inline-flex items-center gap-1 rounded-lg bg-sky-700 px-3 py-2 text-xs font-medium text-white hover:bg-sky-800 disabled:opacity-50"
              >
                {correlate.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
                Correlate order
              </button>
              {selectedStudyId ? (
                <Link
                  href={`/ehr/${patientId}/imaging/viewer?governedStudyId=${encodeURIComponent(selectedStudyId)}`}
                  className="text-xs font-medium text-sky-800 hover:underline"
                >
                  Open viewer
                </Link>
              ) : null}
            </div>
          )}
          {error ? <p className="text-xs text-red-700">{error}</p> : null}
          {success ? <p className="text-xs text-emerald-800">{success}</p> : null}
        </div>
      </div>
    </section>
  );
}
