"use client";

/**
 * Register a site (SJ2 / D-L4/D-L5). Route: /site/register
 *
 * A person self-registers a public-health SITE (Indawo) that is not yet on the
 * platform. The applicant is the session Health ID (server-side). Registration
 * requires a proven personal identity (≥LOA2) and supporting evidence; the
 * registry silently blocks on a credible duplicate and routes to a steward. The
 * site is not listed or usable until confirmed — this screen never fakes it.
 *
 * Distinct from the regulatory Site Registry (licensing/inspection). This is the
 * open self-service front door.
 */

import { useState } from "react";
import { CheckCircle2, MapPinned, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  siteSelfServiceErrorMessage,
  useRegisterSite,
  type SiteRegistrationInput,
} from "@/hooks/queries/useSiteSelfService";

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";

const SITE_TYPES = [
  "SERVICE_POINT",
  "MOBILE_UNIT",
  "COMMUNITY_POST",
  "OUTREACH_SITE",
  "WAREHOUSE",
  "LABORATORY_POINT",
  "OTHER",
];

export default function SiteRegisterPage() {
  const register = useRegisterSite();

  const [form, setForm] = useState<SiteRegistrationInput>({
    name: "",
    type: "SERVICE_POINT",
    siteCategory: "",
    province: "",
    district: "",
    siteCode: "",
    evidenceRef: "",
    notes: "",
  });
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<{ caseRef?: string; note?: string } | null>(null);

  function set<K extends keyof SiteRegistrationInput>(key: K, value: SiteRegistrationInput[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!form.name.trim()) {
      setError("Enter the site name.");
      return;
    }
    if (!form.evidenceRef.trim()) {
      setError("Provide a supporting evidence reference.");
      return;
    }
    register.mutate(
      { ...form, name: form.name.trim(), evidenceRef: form.evidenceRef.trim() },
      {
        onSuccess: (res) => setReceipt({ caseRef: res.caseRef, note: res.note }),
        onError: (err) =>
          setError(siteSelfServiceErrorMessage(err, "Your site registration could not be submitted.")),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Register a site" subtitle="Add a public-health site that is not yet on the platform">
        <div className="mx-auto max-w-2xl space-y-6">
          {receipt && (
            <div
              className="flex items-start gap-2 rounded-lg border border-success/35 bg-success-soft p-4 text-sm"
              data-testid="site-registration-receipt"
            >
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
              <div>
                <p className="font-medium text-foreground">Registration received</p>
                <p className="mt-1 text-muted-foreground">{receipt.note}</p>
                {receipt.caseRef && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Reference: <span className="font-mono">{receipt.caseRef}</span>
                  </p>
                )}
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4 rounded-2xl border border-border bg-card p-5">
            <div>
              <label htmlFor="name" className="mb-1 block text-sm font-medium text-foreground">
                Site name
              </label>
              <input
                id="name"
                type="text"
                value={form.name}
                onChange={(e) => set("name", e.target.value)}
                className={inputClass}
                required
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="type" className="mb-1 block text-sm font-medium text-foreground">
                  Site type
                </label>
                <select
                  id="type"
                  value={form.type}
                  onChange={(e) => set("type", e.target.value)}
                  className={inputClass}
                >
                  {SITE_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="category" className="mb-1 block text-sm font-medium text-foreground">
                  Category (optional)
                </label>
                <input
                  id="category"
                  type="text"
                  value={form.siteCategory}
                  onChange={(e) => set("siteCategory", e.target.value)}
                  className={inputClass}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <label htmlFor="province" className="mb-1 block text-sm font-medium text-foreground">
                  Province (optional)
                </label>
                <input
                  id="province"
                  type="text"
                  value={form.province}
                  onChange={(e) => set("province", e.target.value)}
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="district" className="mb-1 block text-sm font-medium text-foreground">
                  District (optional)
                </label>
                <input
                  id="district"
                  type="text"
                  value={form.district}
                  onChange={(e) => set("district", e.target.value)}
                  className={inputClass}
                />
              </div>
            </div>

            <div>
              <label htmlFor="evidence" className="mb-1 block text-sm font-medium text-foreground">
                Supporting evidence reference
              </label>
              <input
                id="evidence"
                type="text"
                value={form.evidenceRef}
                onChange={(e) => set("evidenceRef", e.target.value)}
                placeholder="Document id supporting the site"
                className={inputClass}
                required
              />
              <p className="mt-1 text-xs text-muted-foreground">
                Registering requires a proven personal identity and supporting evidence. Knowing a
                site’s details is never enough on its own.
              </p>
            </div>

            {error && (
              <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
                <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={register.isPending}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:bg-primary-hover disabled:opacity-50"
            >
              <MapPinned className="h-4 w-4" />
              {register.isPending ? "Submitting…" : "Register site"}
            </button>
          </form>
        </div>
      </PageShell>
    </AppLayout>
  );
}
