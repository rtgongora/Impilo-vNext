"use client";

import Link from "next/link";
import { ArrowRightLeft, BedDouble } from "lucide-react";

interface InpatientAdmissionHandoffBannerProps {
  patientId?: string | null;
  encounterId?: string | null;
  transactionId?: string | null;
  source?: string | null;
}

export function InpatientAdmissionHandoffBanner({
  patientId,
  encounterId,
  transactionId,
  source,
}: InpatientAdmissionHandoffBannerProps) {
  if (!patientId && !encounterId) {
    return null;
  }

  const wardBoardHref = patientId
    ? `/clinical/inpatient/ward-board?subjectCpid=${encodeURIComponent(patientId)}`
    : "/clinical/inpatient/ward-board";
  const bedsHref = patientId
    ? `/beds?patientId=${encodeURIComponent(patientId)}${encounterId ? `&encounterId=${encodeURIComponent(encounterId)}` : ""}`
    : "/beds";

  return (
    <div
      className="mb-4 rounded-xl border border-info/25 bg-info-soft px-4 py-3 text-sm text-primary-hover"
      data-testid="inpatient-admission-handoff-banner"
    >
      <div className="flex flex-wrap items-start gap-3">
        <BedDouble className="mt-0.5 h-5 w-5 shrink-0 text-indigo-600" aria-hidden />
        <div className="flex-1 space-y-1">
          <p className="font-medium">
            {source === "outpatient-discharge" ? "Outpatient admit handoff" : "Inpatient admission context"}
          </p>
          <p className="text-xs text-primary-hover/80">
            Continue ward allocation with active admission correlation
            {encounterId ? ` for encounter ${encounterId}` : ""}
            {transactionId ? ` (transaction ${transactionId})` : ""}.
          </p>
          <div className="flex flex-wrap gap-3 pt-1">
            <Link href={bedsHref} className="inline-flex items-center gap-1 text-xs font-medium text-primary-hover hover:underline">
              <ArrowRightLeft className="h-3.5 w-3.5" />
              Allocate bed & create admission
            </Link>
            <Link href={wardBoardHref} className="text-xs font-medium text-primary-hover hover:underline">
              Open ward board
            </Link>
            {patientId ? (
              <Link
                href={
                  encounterId
                    ? `/ehr/${patientId}/encounter/${encounterId}`
                    : `/ehr/${patientId}/encounters`
                }
                className="text-xs font-medium text-primary-hover hover:underline"
              >
                Return to encounter
              </Link>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
