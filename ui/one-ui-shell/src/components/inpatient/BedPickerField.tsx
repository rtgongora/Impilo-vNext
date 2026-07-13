"use client";

import { useMemo, useState } from "react";
import { Loader2, BedDouble } from "lucide-react";
import { useWards, useBeds, type BedResource } from "@/hooks/queries/useBeds";

const ASSIGNABLE = new Set(["AVAILABLE", "CLEANING", "RESERVED"]);

/**
 * Live bed picker for admission creation. Lists wards for the facility and their beds;
 * only AVAILABLE/CLEANING/RESERVED beds are selectable (an already-OCCUPIED or
 * out-of-service bed is shown disabled). Honest empty states — a ward with no free beds
 * says so rather than rendering nothing.
 */
export function BedPickerField({
  facilityId,
  value,
  onChange,
}: {
  facilityId: string;
  value: string | null;
  onChange: (bedId: string, wardId: string) => void;
}) {
  const { data: wardsData, isPending: wardsPending, isError: wardsError } = useWards(facilityId);
  const { data: bedsData, isPending: bedsPending, isError: bedsError } = useBeds(facilityId);

  const wards = useMemo(() => wardsData?.data ?? [], [wardsData?.data]);
  const beds = useMemo(() => bedsData?.data ?? [], [bedsData?.data]);
  const [activeWard, setActiveWard] = useState<string | null>(null);

  const wardId = activeWard ?? wards[0]?.id ?? null;
  const wardBeds = beds.filter((b) => b.attributes.wardId === wardId);

  if (wardsPending || bedsPending) {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-border p-4 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading wards and beds…
      </div>
    );
  }
  if (wardsError || bedsError) {
    return (
      <div className="rounded-xl border border-danger/30 bg-danger-soft p-4 text-sm text-rose-800">
        Could not load beds. The inpatient service may be unavailable — you can still admit without a bed and assign one later.
      </div>
    );
  }
  if (wards.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border p-4 text-sm text-muted-foreground">
        No wards configured for this facility.
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-border">
      <div className="flex flex-wrap gap-1 border-b border-border p-2">
        {wards.map((w) => {
          const available = Number(w.attributes.availableBeds ?? 0);
          const total = Number(w.attributes.totalBeds ?? 0);
          const isActive = w.id === wardId;
          return (
            <button
              key={w.id}
              type="button"
              onClick={() => setActiveWard(w.id)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium ${
                isActive ? "bg-primary text-white" : "text-foreground hover:bg-muted"
              }`}
            >
              {w.attributes.name}{" "}
              <span className={isActive ? "opacity-80" : "text-muted-foreground"}>
                ({available}/{total})
              </span>
            </button>
          );
        })}
      </div>

      <div className="p-3">
        {wardBeds.length === 0 ? (
          <p className="py-4 text-center text-sm text-muted-foreground">No beds in this ward.</p>
        ) : wardBeds.every((b) => !ASSIGNABLE.has(String(b.attributes.status).toUpperCase())) ? (
          <p className="py-4 text-center text-sm text-muted-foreground">
            No free beds in this ward — check another ward or escalate capacity.
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
            {wardBeds.map((b: BedResource) => {
              const status = String(b.attributes.status).toUpperCase();
              const assignable = ASSIGNABLE.has(status);
              const selected = b.id === value;
              return (
                <button
                  key={b.id}
                  type="button"
                  disabled={!assignable}
                  title={assignable ? undefined : `Bed ${b.attributes.bedNumber} is ${status.toLowerCase()}`}
                  onClick={() => onChange(b.id, b.attributes.wardId)}
                  className={`flex flex-col items-start gap-0.5 rounded-lg border p-2 text-left text-xs ${
                    selected
                      ? "border-primary bg-primary-soft text-primary-hover"
                      : assignable
                        ? "border-border bg-card hover:border-primary/50"
                        : "cursor-not-allowed border-dashed border-border bg-background text-muted-foreground opacity-60"
                  }`}
                >
                  <span className="flex items-center gap-1 font-medium">
                    <BedDouble className="h-3.5 w-3.5" /> {b.attributes.bedNumber}
                  </span>
                  <span className="capitalize">{status.toLowerCase()}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
