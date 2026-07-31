"use client";

/**
 * Specialty indication picker — fills the operative indication (and optional non-operative
 * alternatives) from the SB-4 catalogue. A failed catalogue read must never look like "no
 * indications for this specialty".
 */

import { AlertTriangle, Loader2 } from "lucide-react";
import {
  useSpecialtyIndications,
  type SpecialtyIndication,
} from "@/hooks/queries/useSurgeryEpisodes";

export function SpecialtyIndicationPicker({
  specialty,
  onPick,
}: {
  specialty: string;
  onPick: (indication: SpecialtyIndication) => void;
}) {
  const indicationsQ = useSpecialtyIndications(specialty || null);

  if (!specialty) {
    return (
      <p className="text-xs text-muted-foreground" data-testid="surgery-indication-picker-idle">
        Choose a specialty to load its catalogue indications.
      </p>
    );
  }

  if (indicationsQ.isLoading) {
    return (
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading indications…
      </div>
    );
  }

  if (indicationsQ.isError) {
    return (
      <div
        className="flex items-center gap-2 text-xs text-danger"
        role="alert"
        data-testid="surgery-indication-picker-unavailable"
      >
        <AlertTriangle className="h-4 w-4" />
        Could not read specialty indications. This is not the same as there being none.
      </div>
    );
  }

  const rows = Array.isArray(indicationsQ.data) ? indicationsQ.data : [];
  if (rows.length === 0) {
    return (
      <p className="text-xs text-muted-foreground" data-testid="surgery-indication-picker-empty">
        No catalogue indications for this specialty.
      </p>
    );
  }

  return (
    <label className="block text-xs" data-testid="surgery-indication-picker">
      <span className="font-semibold uppercase text-muted-foreground">
        Catalogue indication (optional)
      </span>
      <select
        className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
        defaultValue=""
        onChange={(e) => {
          const code = e.target.value;
          const hit = rows.find((r) => r.indicationCode === code);
          if (hit) onPick(hit);
        }}
        data-testid="surgery-indication-select"
      >
        <option value="">Pick from catalogue…</option>
        {rows.map((r) => (
          <option key={r.indicationCode} value={r.indicationCode}>
            {r.indicationCode} — {r.conditionDisplay ?? r.typicalProcedure ?? "indication"}
          </option>
        ))}
      </select>
    </label>
  );
}
