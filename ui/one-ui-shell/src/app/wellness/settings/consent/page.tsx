"use client";

import { useEffect, useState } from "react";
import { Loader2, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useConsentPreferences,
  useUpdateConsentPreferences,
} from "@/hooks/queries/useSimbaWellnessCore";

const SENSITIVE_CATEGORIES: [string, string][] = [
  ["MENTAL", "Mental wellbeing"],
  ["SUBSTANCE", "Substance use"],
  ["SRH", "Sexual & reproductive health"],
  ["HIV", "HIV"],
  ["ADOLESCENT", "Adolescent health"],
];

const SCOPES: [string, string][] = [
  ["PRIVATE", "Private to me"],
  ["CARE_TEAM", "My wellness care team"],
  ["PROGRAMME", "My programme"],
  ["RESEARCH", "Anonymised research"],
];

/** Wellness consent preferences — the person controls who can be involved and what is shared. */
export default function WellnessConsentPage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id;

  const q = useConsentPreferences(cpid);
  const update = useUpdateConsentPreferences();

  const [provider, setProvider] = useState(true);
  const [caregiver, setCaregiver] = useState(false);
  const [programme, setProgramme] = useState(false);
  const [scope, setScope] = useState("CARE_TEAM");
  const [categories, setCategories] = useState<string[]>([]);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const data = q.data?.data;
    if (!data) return;
    setProvider(!!data.providerInvolvement);
    setCaregiver(!!data.caregiverInvolvement);
    setProgramme(!!data.programmeInvolvement);
    setScope(data.dataSharingScope ?? "CARE_TEAM");
    try {
      setCategories(JSON.parse(data.sensitiveCategories ?? "[]"));
    } catch {
      setCategories([]);
    }
  }, [q.data]);

  function toggleCategory(cat: string) {
    setCategories((prev) =>
      prev.includes(cat) ? prev.filter((c) => c !== cat) : [...prev, cat],
    );
  }

  function save() {
    if (!cpid) return;
    setSaved(false);
    update.mutate(
      {
        person_cpid: cpid,
        provider_involvement: provider,
        caregiver_involvement: caregiver,
        programme_involvement: programme,
        sensitive_categories: categories,
        data_sharing_scope: scope,
      },
      { onSuccess: () => setSaved(true) },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Consent & privacy"
        subtitle="You decide who is involved in your wellness and what is shared."
        icon={<ShieldCheck className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-6">
          {q.isLoading ? (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          ) : (
            <>
              <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
                <h2 className="text-lg font-semibold">Who can be involved</h2>
                <Toggle label="My wellness provider / coach" checked={provider} onChange={setProvider} />
                <Toggle label="My caregiver" checked={caregiver} onChange={setCaregiver} />
                <Toggle label="My wellness programme" checked={programme} onChange={setProgramme} />
              </section>

              <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-6">
                <h2 className="text-lg font-semibold">Sensitive topics I consent to share</h2>
                <p className="text-sm text-slate-500">
                  These stay hidden from anyone but you unless you opt in here.
                </p>
                <div className="grid grid-cols-2 gap-2">
                  {SENSITIVE_CATEGORIES.map(([v, label]) => (
                    <label key={v} className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={categories.includes(v)}
                        onChange={() => toggleCategory(v)}
                      />
                      {label}
                    </label>
                  ))}
                </div>
              </section>

              <section className="space-y-2 rounded-xl border border-slate-200 bg-white p-6">
                <h2 className="text-lg font-semibold">Data sharing scope</h2>
                <select
                  value={scope}
                  onChange={(e) => setScope(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                >
                  {SCOPES.map(([v, label]) => (
                    <option key={v} value={v}>
                      {label}
                    </option>
                  ))}
                </select>
              </section>

              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={save}
                  disabled={update.isPending}
                  className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  {update.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  Save preferences
                </button>
                {saved && <span className="text-sm text-emerald-600">Saved.</span>}
              </div>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label className="flex items-center justify-between text-sm">
      <span className="text-slate-700">{label}</span>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
    </label>
  );
}
