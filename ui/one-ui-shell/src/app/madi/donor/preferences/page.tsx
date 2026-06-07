"use client";

import { useState } from "react";
import Link from "next/link";
import { Settings, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDonorByPerson, useUpdateDonorPreferences } from "@/hooks/queries/useMadi";

const CHANNELS = ["SMS", "EMAIL", "PUSH", "WHATSAPP"];

export default function DonorPreferencesPage() {
  const user = useAuthStore((s) => s.user);
  const personCpid = user?.healthId || user?.id || "";
  const { data: donor } = useDonorByPerson(personCpid || undefined);
  const prefs = useUpdateDonorPreferences(donor?.donorId ?? "");
  const [channel, setChannel] = useState("SMS");
  const [enabled, setEnabled] = useState(true);
  const [saved, setSaved] = useState(false);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!donor?.donorId) return;
    prefs.mutate(
      { channel, enabled, updated_by: user?.displayName },
      { onSuccess: () => setSaved(true) },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Donor Preferences" subtitle="How we may contact you about drives" icon={<Settings className="h-6 w-6" />}>
        {!donor && (
          <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center">
            <p className="text-sm text-gray-600">Register as a donor to manage preferences.</p>
            <Link href="/madi/donor/register" className="mt-3 inline-block text-sm text-rose-600">Register →</Link>
          </div>
        )}

        {donor && (
          <form onSubmit={handleSubmit} className="max-w-md space-y-4 rounded-2xl border border-gray-200 bg-white p-6">
            <p className="text-sm text-gray-600">
              Choose how you want reminders about eligible donation windows. We never share your contact details with third parties.
            </p>
            <label className="block text-sm font-medium text-gray-700">
              Channel
              <select
                value={channel}
                onChange={(e) => setChannel(e.target.value)}
                className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm bg-white"
              >
                {CHANNELS.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </label>
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
              Receive notifications on this channel
            </label>
            <button
              type="submit"
              disabled={prefs.isPending}
              className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
            >
              {prefs.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Save preferences
            </button>
            {saved && <p className="text-sm text-green-700">Preferences saved.</p>}
          </form>
        )}
      </PageShell>
    </AppLayout>
  );
}
