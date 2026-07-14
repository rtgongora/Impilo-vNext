"use client";

/**
 * EditDemographicsForm — edit a client's demographics and save to VITO (the identity SoR)
 * via the BFF PUT /clients/{healthId}/demographics. Closes G055 (VITO's demographics-update
 * had no consumer above VITO — no BFF route, no UI edit flow).
 */

import { useState } from "react";
import {
  useUpdateClientDemographics,
  type DemographicsUpdate,
} from "@/hooks/queries/useClientRegistry";

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
  { key: "addressLine1", label: "Address line 1" },
  { key: "city", label: "City" },
  { key: "district", label: "District" },
  { key: "province", label: "Province" },
];

export function EditDemographicsForm({ healthId, initial, onSaved }: EditDemographicsFormProps) {
  const [form, setForm] = useState<DemographicsUpdate>({ ...initial });
  const mutation = useUpdateClientDemographics(healthId);

  const onChange = (key: keyof DemographicsUpdate, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await mutation.mutateAsync(form);
      onSaved?.();
    } catch {
      /* surfaced via mutation.isError */
    }
  };

  return (
    <form onSubmit={onSubmit} aria-label="Edit demographics" className="space-y-3">
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
