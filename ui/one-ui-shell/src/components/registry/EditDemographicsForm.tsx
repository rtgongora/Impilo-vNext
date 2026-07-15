"use client";

/**
 * EditDemographicsForm — edit a client's demographics and save to VITO (the identity SoR)
 * via the BFF PUT /clients/{healthId}/demographics. Closes G055 (VITO's demographics-update
 * had no consumer above VITO — no BFF route, no UI edit flow).
 *
 * Also surfaces the G4 Ndila location (when delegated) and MEDICAL_AID_NUMBER identifier capture.
 */

import { useEffect, useState } from "react";
import { MapPin } from "lucide-react";
import {
  useUpdateClientDemographics,
  type DemographicsUpdate,
} from "@/hooks/queries/useClientRegistry";
import { apiClient } from "@/lib/api-client";
import { NdilaAddressSearchBox } from "@/components/ndila/NdilaAddressSearchBox";
import type { NdilaGeocodeResult } from "@/lib/ndila/ndila-client";

export interface EditDemographicsFormProps {
  healthId: string;
  initial?: Partial<DemographicsUpdate>;
  onSaved?: () => void;
}

const FIELDS: { key: keyof DemographicsUpdate; label: string; type?: string }[] = [
  { key: "givenName", label: "Given name" },
  { key: "middleName", label: "Middle name" },
  { key: "familyName", label: "Family name" },
  { key: "dateOfBirth", label: "Date of birth", type: "date" },
  { key: "sex", label: "Sex" },
  { key: "phone", label: "Phone", type: "tel" },
  { key: "email", label: "Email", type: "email" },
  { key: "medicalAidNumber", label: "Medical aid number (Health ID identifier)" },
  { key: "addressLine1", label: "Address line 1" },
  { key: "city", label: "City" },
  { key: "district", label: "District" },
  { key: "province", label: "Province" },
];

interface NdilaLocationSummary {
  id?: string;
  name?: string;
  addressLine1?: string;
  locality?: string;
  district?: string;
  province?: string;
  latitude?: number;
  longitude?: number;
}

export function EditDemographicsForm({ healthId, initial, onSaved }: EditDemographicsFormProps) {
  const [form, setForm] = useState<DemographicsUpdate>({ ...initial });
  const mutation = useUpdateClientDemographics(healthId);
  const [ndilaLocations, setNdilaLocations] = useState<NdilaLocationSummary[]>([]);
  const [ndilaError, setNdilaError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const res = await apiClient.get<{ data?: NdilaLocationSummary[] }>(
          `/internal/v1/ndila/locations/by-owner/vito/${encodeURIComponent(healthId)}`,
        );
        const data = (res as { data?: NdilaLocationSummary[] }).data;
        if (!cancelled) {
          setNdilaLocations(Array.isArray(data) ? data : []);
          setNdilaError(null);
        }
      } catch {
        if (!cancelled) {
          setNdilaLocations([]);
          setNdilaError("Ndila location not available yet for this client.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [healthId]);

  const onChange = (key: keyof DemographicsUpdate, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const onGeocodeSelect = (result: NdilaGeocodeResult) => {
    setForm((prev) => ({
      ...prev,
      addressLine1: result.address ?? result.name ?? prev.addressLine1,
      city: result.locality ?? prev.city,
      district: result.district ?? prev.district,
      province: result.province ?? prev.province,
    }));
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await mutation.mutateAsync(form);
      onSaved?.();
    } catch {
      /* surfaced via mutation.isError */
    }
  };

  const primaryLocation = ndilaLocations[0];

  return (
    <form onSubmit={onSubmit} aria-label="Edit demographics" className="space-y-3">
      <div className="rounded-lg border border-border bg-muted/30 p-3 space-y-2">
        <p className="text-xs font-medium text-foreground flex items-center gap-1.5">
          <MapPin className="h-3.5 w-3.5" /> Canonical location (Ndila)
        </p>
        {primaryLocation ? (
          <p className="text-sm text-foreground" data-testid="ndila-location-summary">
            {[primaryLocation.name, primaryLocation.addressLine1, primaryLocation.locality, primaryLocation.district, primaryLocation.province]
              .filter(Boolean)
              .join(" · ")}
            {primaryLocation.latitude != null && primaryLocation.longitude != null && (
              <span className="ml-2 text-xs text-muted-foreground">
                ({primaryLocation.latitude.toFixed(5)}, {primaryLocation.longitude.toFixed(5)})
              </span>
            )}
          </p>
        ) : (
          <p className="text-xs text-muted-foreground">
            {ndilaError ?? "No delegated Ndila location yet — save an address to trigger geography sync."}
          </p>
        )}
        <NdilaAddressSearchBox
          initialQuery={form.addressLine1 ?? ""}
          province={form.province}
          district={form.district}
          purposeOfUse="REGISTRATION"
          onSelect={onGeocodeSelect}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {FIELDS.map((f) => (
          <label key={f.key} className="flex flex-col gap-1 text-xs">
            <span className="text-muted-foreground">{f.label}</span>
            <input
              aria-label={f.label}
              type={f.type ?? "text"}
              value={(form[f.key] as string) ?? ""}
              onChange={(e) => onChange(f.key, e.target.value)}
              className="rounded-md border border-border bg-card px-2 py-1.5 text-sm"
            />
          </label>
        ))}
      </div>

      {mutation.isError && (
        <p role="alert" className="text-xs text-danger">
          Failed to save demographics. Please try again.
        </p>
      )}
      {mutation.isSuccess && (
        <p role="status" className="text-xs text-green-600">Demographics saved.</p>
      )}

      <button
        type="submit"
        disabled={mutation.isPending}
        className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm text-white hover:bg-primary-hover disabled:opacity-60"
      >
        {mutation.isPending ? "Saving…" : "Save demographics"}
      </button>
    </form>
  );
}
