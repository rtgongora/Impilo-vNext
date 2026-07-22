"use client";

/**
 * Practice & Facility Regulation — the applicant/owner side of establishing a licensed health
 * practice (ROM). A prospective practice owner builds a PRACTICE ESTABLISHMENT case BEFORE any
 * facility exists; the canonical facility is created only when the regulator APPROVES the
 * application. Reads /internal/v1/me/regulatory/practice-establishments — which resolves the
 * caller's OWN establishment cases from the trust context (never a providerId param), so this is
 * genuinely self-service.
 *
 * Route: /professional/practice-regulation
 */

import { useCallback, useEffect, useState } from "react";
import {
  Building2,
  CheckCircle2,
  Info,
  Loader2,
  PlusCircle,
  ScrollText,
  Send,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface PracticeEstablishment {
  establishmentId: string;
  proposedName: string;
  categoryCode?: string | null;
  proposedProvince?: string | null;
  status: string;
  createdFacilityUuid?: string | null;
}

function unwrapList<T>(payload: unknown): T[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  return Array.isArray(p) ? (p as T[]) : [];
}

function unwrapEstablishment(payload: unknown): Partial<PracticeEstablishment> {
  const p = (payload as { data?: unknown })?.data ?? payload;
  return (p ?? {}) as Partial<PracticeEstablishment>;
}

const CATEGORY_CODES = ["PHARMACY", "SURGERY", "CLINIC", "LABORATORY"] as const;

const PROVINCES = [
  "Bulawayo",
  "Harare",
  "Manicaland",
  "Mashonaland Central",
  "Mashonaland East",
  "Mashonaland West",
  "Masvingo",
  "Matabeleland North",
  "Matabeleland South",
  "Midlands",
  "NATIONAL",
] as const;

const STATUS_STYLE: Record<string, string> = {
  DRAFT: "bg-muted text-muted-foreground border-border",
  SUBMITTED: "bg-sky-50 text-sky-700 border-sky-200",
  UNDER_REVIEW: "bg-sky-50 text-sky-700 border-sky-200",
  RFI_OPEN: "bg-amber-50 text-amber-800 border-amber-200",
  INSPECTION_PENDING: "bg-amber-50 text-amber-800 border-amber-200",
  APPROVED: "bg-teal-50 text-teal-700 border-teal-200",
  REJECTED: "bg-red-50 text-red-700 border-red-200",
  WITHDRAWN: "bg-muted text-muted-foreground border-border",
};

function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${
        STATUS_STYLE[status?.toUpperCase()] ?? "border-border text-muted-foreground"
      }`}
    >
      {status?.replace(/_/g, " ").toLowerCase()}
    </span>
  );
}

export default function PracticeRegulationPage() {
  const [establishments, setEstablishments] = useState<PracticeEstablishment[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>(
        "/internal/v1/me/regulatory/practice-establishments",
      );
      setEstablishments(unwrapList<PracticeEstablishment>(res));
    } catch {
      setError("Could not load your practice applications.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Practice & Facility Regulation"
        subtitle="Establish and license a health practice"
        serviceSlug="tuso"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <section className="flex items-start gap-2 rounded-2xl border border-teal-200 bg-teal-50/60 p-4 text-sm text-teal-900">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
            <div className="space-y-1.5">
              <p>
                You can build your practice profile before the facility exists. The licensed facility
                is created only when your application is approved.
              </p>
              <p className="text-xs text-teal-800">
                Legally-reserved declarations must be completed by the owner, director, or
                practitioner-in-charge — managers may prepare the application, but cannot make those
                declarations on their behalf.
              </p>
            </div>
          </section>

          <NewApplicationForm onCreated={load} />

          <section className="space-y-3">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Building2 className="h-4 w-4 text-teal-600" /> My practice applications
            </h2>

            {loading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </div>
            ) : error ? (
              <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {error}
              </div>
            ) : establishments.length === 0 ? (
              <p className="rounded-lg border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
                No practice applications yet — start one above.
              </p>
            ) : (
              <ul className="space-y-2">
                {establishments.map((e) => (
                  <EstablishmentRow key={e.establishmentId} establishment={e} onSubmitted={load} />
                ))}
              </ul>
            )}
          </section>

          <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
            <ScrollText className="h-3.5 w-3.5" /> The facility is licensed and created by the
            regulator on approval — Impilo prepares and submits your application; it does not decide
            it.
          </p>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function NewApplicationForm({ onCreated }: { onCreated: () => void }) {
  const [proposedName, setProposedName] = useState("");
  const [categoryCode, setCategoryCode] = useState<string>("");
  const [proposedProvince, setProposedProvince] = useState<string>("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const submit = async () => {
    if (!proposedName.trim()) return;
    setSaving(true);
    setFormError(null);
    try {
      const body: {
        proposedName: string;
        categoryCode?: string;
        proposedProvince?: string;
      } = { proposedName: proposedName.trim() };
      if (categoryCode) body.categoryCode = categoryCode;
      if (proposedProvince) body.proposedProvince = proposedProvince;
      await apiClient.post<unknown>("/internal/v1/me/regulatory/practice-establishments", body);
      setProposedName("");
      setCategoryCode("");
      setProposedProvince("");
      onCreated();
    } catch {
      setFormError("Could not start your application. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-3 rounded-2xl border border-border bg-card p-4">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <PlusCircle className="h-4 w-4 text-teal-600" /> Start a new practice application
      </h2>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="space-y-1 text-xs sm:col-span-2">
          <span className="font-medium text-muted-foreground">Proposed practice name</span>
          <input
            type="text"
            value={proposedName}
            onChange={(e) => setProposedName(e.target.value)}
            placeholder="e.g. Harare Family Pharmacy"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
          />
        </label>

        <label className="space-y-1 text-xs">
          <span className="font-medium text-muted-foreground">Category</span>
          <select
            value={categoryCode}
            onChange={(e) => setCategoryCode(e.target.value)}
            className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
          >
            <option value="">Select a category…</option>
            {CATEGORY_CODES.map((c) => (
              <option key={c} value={c}>
                {c.charAt(0) + c.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </label>

        <label className="space-y-1 text-xs">
          <span className="font-medium text-muted-foreground">Province</span>
          <select
            value={proposedProvince}
            onChange={(e) => setProposedProvince(e.target.value)}
            className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm"
          >
            <option value="">Select a province…</option>
            {PROVINCES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
      </div>

      {formError && <p className="text-xs text-red-700">{formError}</p>}

      <button
        type="button"
        onClick={submit}
        disabled={saving || !proposedName.trim()}
        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <PlusCircle className="h-3.5 w-3.5" />}
        {saving ? "Starting…" : "Start application"}
      </button>
      <p className="text-[11px] text-muted-foreground">
        This creates a draft you can complete and submit. Nothing is filed with the regulator until
        you submit.
      </p>
    </section>
  );
}

function EstablishmentRow({
  establishment,
  onSubmitted,
}: {
  establishment: PracticeEstablishment;
  onSubmitted: () => void;
}) {
  const [saving, setSaving] = useState(false);
  const status = establishment.status?.toUpperCase();
  const isDraft = status === "DRAFT";
  const isApproved = status === "APPROVED";

  const submit = async () => {
    setSaving(true);
    try {
      await apiClient.post<unknown>(
        `/internal/v1/me/regulatory/practice-establishments/${encodeURIComponent(
          establishment.establishmentId,
        )}/submit`,
      );
      onSubmitted();
    } finally {
      setSaving(false);
    }
  };

  return (
    <li className="rounded-xl border border-border bg-card p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-sm font-medium text-foreground">
            {establishment.proposedName || "Untitled practice"}
          </div>
          <div className="mt-0.5 text-xs text-muted-foreground">
            {establishment.categoryCode
              ? establishment.categoryCode.charAt(0) +
                establishment.categoryCode.slice(1).toLowerCase()
              : "Uncategorised"}
            {establishment.proposedProvince ? ` · ${establishment.proposedProvince}` : ""}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={establishment.status} />
          {isDraft && (
            <button
              type="button"
              onClick={submit}
              disabled={saving}
              className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
            >
              {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
              {saving ? "Submitting…" : "Submit"}
            </button>
          )}
        </div>
      </div>

      {isApproved && establishment.createdFacilityUuid && (
        <div className="mt-2 flex items-start gap-2 rounded-lg border border-teal-200 bg-teal-50 p-2.5 text-xs text-teal-800">
          <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
          <div>
            <div className="font-medium">Facility licensed — created on approval</div>
            <div className="mt-0.5 font-mono text-[11px] text-teal-700">
              {establishment.createdFacilityUuid}
            </div>
          </div>
        </div>
      )}
    </li>
  );
}
