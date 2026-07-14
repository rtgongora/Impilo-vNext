"use client";

/**
 * RecognisedProviderChip — display-only badge for recognised health
 * workers/providers when THEY are the subject of a journey (check-in,
 * registration banner, booking).
 *
 * Doctrine: the badge is a courtesy signal for administrative/financial
 * advantages (fee exemptions, subsidy programmes). It MUST NOT reorder
 * queues, change triage, or grant clinical priority — it renders text only.
 * Renders nothing while loading, on error, or when not recognised
 * (fail-quiet: recognition is a perk, never a gate).
 */

import { BadgeCheck } from "lucide-react";
import {
  recognitionLabel,
  useRecognition,
  type RecognitionView,
} from "@/hooks/queries/useRecognition";

export function RecognisedProviderChipView({
  recognition,
  className = "",
}: {
  recognition: RecognitionView | null | undefined;
  className?: string;
}) {
  if (!recognition?.recognised || !recognition.recognitionClass) return null;
  const label = recognitionLabel(recognition.recognitionClass);
  const detail = recognition.profession ?? recognition.cadre ?? null;
  return (
    <span
      data-testid="recognised-provider-chip"
      title={detail ? `${label} · ${detail}` : label}
      className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full border bg-teal-50 text-teal-800 border-teal-200 ${className}`}
    >
      <BadgeCheck className="w-3.5 h-3.5 text-teal-600" aria-hidden />
      {label}
    </span>
  );
}

export function RecognisedProviderChip({
  healthId,
  className = "",
}: {
  healthId: string | null | undefined;
  className?: string;
}) {
  const { data } = useRecognition(healthId);
  return <RecognisedProviderChipView recognition={data} className={className} />;
}
