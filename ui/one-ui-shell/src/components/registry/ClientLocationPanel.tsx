"use client";

/**
 * G4 — Canonical location panel for the client-360 page.
 *
 * Surfaces the ndila location materialized from a client's delegated address: the normalized
 * address parts, coordinates, geocode verification status, and a map marker. Honest states:
 * the delegation is async + best-effort, so a location may not exist yet ("not materialized"),
 * or exist without coordinates yet ("awaiting geocode").
 */
import { Loader2, MapPin } from "lucide-react";
import { NdilaMap } from "@/components/ndila/NdilaMap";
import { useClientCanonicalLocation } from "@/hooks/queries/useClientCanonicalLocation";

const CARD = "rounded-2xl border border-border bg-card p-5";
const HEADING = "text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground";

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 font-medium text-foreground">{value && value.length > 0 ? value : "Not stated"}</dd>
    </div>
  );
}

export function ClientLocationPanel({ healthId }: { healthId: string }) {
  const { data: loc, isLoading, isError } = useClientCanonicalLocation(healthId);

  const header = (
    <div className="flex items-center gap-2">
      <MapPin className="h-4 w-4 text-muted-foreground" />
      <h3 className={HEADING}>Canonical location (ndila)</h3>
    </div>
  );

  if (isLoading) {
    return (
      <div className={CARD} data-testid="client-location-panel">
        {header}
        <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading location…
        </div>
      </div>
    );
  }

  // Fail-clean: ndila is a best-effort geography SoR, never authoritative for the client record.
  if (isError) {
    return (
      <div className={CARD} data-testid="client-location-panel">
        {header}
        <p className="mt-3 text-sm text-warning-foreground">
          The geography service is unavailable right now. The client&apos;s address is unaffected.
        </p>
      </div>
    );
  }

  if (!loc) {
    return (
      <div className={CARD} data-testid="client-location-panel">
        {header}
        <p className="mt-3 text-sm text-muted-foreground">
          No canonical location has been materialized for this client yet. Addresses are delegated to
          ndila asynchronously (best-effort) after registration or a demographics update — it appears
          here once ndila has processed it.
        </p>
      </div>
    );
  }

  const hasCoords = typeof loc.latitude === "number" && typeof loc.longitude === "number";

  return (
    <div className={CARD} data-testid="client-location-panel">
      <div className="flex items-center justify-between">
        {header}
        {loc.verificationStatus ? (
          <span className="rounded-full border border-border bg-background px-2.5 py-0.5 text-xs font-medium text-foreground">
            {loc.verificationStatus}
          </span>
        ) : null}
      </div>

      <dl className="mt-4 grid gap-4 md:grid-cols-3">
        <Field label="District" value={loc.district} />
        <Field label="Province" value={loc.province} />
        <Field label="Country" value={loc.country} />
        <Field
          label="Coordinates"
          value={hasCoords ? `${loc.latitude!.toFixed(5)}, ${loc.longitude!.toFixed(5)}` : undefined}
        />
        <Field label="Location type" value={loc.locationType} />
        <Field label="Sensitivity" value={loc.sensitivityLevel} />
      </dl>

      {hasCoords ? (
        <div className="mt-4 overflow-hidden rounded-xl border border-border" data-testid="client-location-map">
          <NdilaMap
            center={{ latitude: loc.latitude!, longitude: loc.longitude! }}
            zoom={14}
            height={260}
            mode="OPERATIONS"
            markers={[
              {
                id: loc.id,
                latitude: loc.latitude!,
                longitude: loc.longitude!,
                label: loc.name,
              },
            ]}
            fitToMarkers
          />
        </div>
      ) : (
        <p className="mt-4 rounded-xl border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
          Awaiting geocode — the location is registered with ndila but has no coordinates yet.
        </p>
      )}
    </div>
  );
}
