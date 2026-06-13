"use client";

/**
 * Edit provider — admin form.
 * Route: /registry/providers/[id]/edit
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Loader2, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useProvider } from "@/hooks/queries/useRegistry";
import {
  useUpdateProvider,
  type ProviderAdminPayload,
  type ProviderSex,
} from "@/hooks/queries/useProviderAdmin";

const inputClass =
  "w-full px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500";

function splitDisplayName(displayName: string): { given: string; family: string } {
  const parts = displayName.trim().split(/\s+/);
  if (parts.length === 0) return { given: "", family: "" };
  if (parts.length === 1) return { given: parts[0], family: "" };
  return { given: parts[0], family: parts.slice(1).join(" ") };
}

function coerceSex(v: unknown): ProviderSex {
  const s = String(v ?? "").toUpperCase();
  if (s === "MALE" || s === "FEMALE" || s === "OTHER") return s;
  return "MALE";
}

function toDateInput(isoOrDate: unknown): string {
  if (isoOrDate == null || isoOrDate === "") return "";
  const s = String(isoOrDate);
  if (s.length >= 10 && s[4] === "-" && s[7] === "-") return s.slice(0, 10);
  try {
    const d = new Date(s);
    if (Number.isNaN(d.getTime())) return "";
    return d.toISOString().slice(0, 10);
  } catch {
    return "";
  }
}

export default function EditProviderPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;

  const { data, isLoading, error } = useProvider(id);
  const updateMutation = useUpdateProvider(id);

  const [form, setForm] = useState({
    givenName: "",
    familyName: "",
    profession: "",
    cadre: "",
    dateOfBirth: "",
    sex: "MALE" as ProviderSex,
    councilCode: "",
    registrationNumber: "",
  });

  const provider = data?.data;

  useEffect(() => {
    if (!provider) return;
    const attrs = provider.attributes as Record<string, unknown>;
    const split = splitDisplayName(provider.attributes.displayName ?? "");
    const given =
      (attrs.givenName as string) ||
      (attrs.given_name as string) ||
      split.given;
    const family =
      (attrs.familyName as string) ||
      (attrs.family_name as string) ||
      split.family;

    setForm({
      givenName: given,
      familyName: family,
      profession:
        (attrs.profession as string) ||
        (attrs.qualification as string) ||
        (provider.attributes.speciality as string) ||
        "",
      cadre: (attrs.cadre as string) || "",
      dateOfBirth: toDateInput(attrs.dateOfBirth ?? attrs.date_of_birth),
      sex: coerceSex(attrs.sex),
      councilCode: (attrs.councilCode as string) || (attrs.council_code as string) || "",
      registrationNumber: provider.attributes.registrationNumber ?? "",
    });
  }, [provider]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const payload: ProviderAdminPayload = {
      givenName: form.givenName.trim(),
      familyName: form.familyName.trim(),
      profession: form.profession.trim(),
      cadre: form.cadre.trim(),
      dateOfBirth: form.dateOfBirth,
      sex: form.sex,
      councilCode: form.councilCode.trim(),
      registrationNumber: form.registrationNumber.trim(),
    };
    updateMutation.mutate(payload, {
      onSuccess: () => {
        router.push(`/registry/providers/${id}`);
      },
    });
  }

  return (
    <AppLayout>
      <PageShell title="Edit Provider" subtitle="Update provider registration details">
        <div className="mb-4">
          <Link
            href={`/registry/providers/${id}`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to provider profile
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading provider...</span>
          </div>
        ) : error || !provider ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center text-sm text-red-600">
            Failed to load provider.
          </div>
        ) : (
          <form
            onSubmit={handleSubmit}
            className="max-w-2xl space-y-4 bg-card rounded-lg border border-border p-6"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">
                  Given name
                </label>
                <input
                  type="text"
                  required
                  value={form.givenName}
                  onChange={(e) => setForm({ ...form, givenName: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">
                  Family name
                </label>
                <input
                  type="text"
                  required
                  value={form.familyName}
                  onChange={(e) => setForm({ ...form, familyName: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">
                  Profession
                </label>
                <input
                  type="text"
                  required
                  value={form.profession}
                  onChange={(e) => setForm({ ...form, profession: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">Cadre</label>
                <input
                  type="text"
                  required
                  value={form.cadre}
                  onChange={(e) => setForm({ ...form, cadre: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">
                  Date of birth
                </label>
                <input
                  type="date"
                  value={form.dateOfBirth}
                  onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">Sex</label>
                <select
                  value={form.sex}
                  onChange={(e) => setForm({ ...form, sex: e.target.value as ProviderSex })}
                  className={inputClass}
                >
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">
                  Council code
                </label>
                <input
                  type="text"
                  required
                  value={form.councilCode}
                  onChange={(e) => setForm({ ...form, councilCode: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">
                  Registration number
                </label>
                <input
                  type="text"
                  required
                  value={form.registrationNumber}
                  onChange={(e) => setForm({ ...form, registrationNumber: e.target.value })}
                  className={inputClass}
                />
              </div>
            </div>

            {updateMutation.isError && (
              <p className="text-sm text-red-600">Could not save changes. Try again.</p>
            )}

            <div className="flex items-center gap-3 pt-2">
              <button
                type="submit"
                disabled={updateMutation.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                {updateMutation.isPending ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="w-4 h-4" />
                    Save changes
                  </>
                )}
              </button>
            </div>
          </form>
        )}
      </PageShell>
    </AppLayout>
  );
}
