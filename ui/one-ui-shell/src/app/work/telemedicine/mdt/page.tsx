"use client";

/**
 * TM-B15 (B15-2): MDT / specialist-board landing — convene a chaired board or open one by id.
 * The board itself is a pct-owned clinical record; this page is composition only.
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

export default function MdtLandingPage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [specialty, setSpecialty] = useState("");
  const [scribeId, setScribeId] = useState("");
  const [openId, setOpenId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function convene() {
    setBusy(true);
    setError(null);
    try {
      const res = await apiClient.post<{ data: { mdtSessionId: string } }>(
        "/internal/v1/teleconsult/mdt/sessions",
        { title: title.trim() || "MDT board", specialty: specialty.trim() || undefined, scribe_id: scribeId.trim() || undefined },
      );
      const id = res.data?.mdtSessionId;
      if (id) router.push(`/work/telemedicine/mdt/${id}`);
      else setError("Board created but no id returned.");
    } catch {
      setError("Could not convene the board — try again.");
      setBusy(false);
    }
  }

  return (
    <AppLayout>
      <PageShell title="MDT Board" subtitle="Convene a multidisciplinary team / specialist-board review">
        <div className="max-w-lg space-y-4">
          <div className="rounded-lg border border-border bg-card p-4 space-y-3">
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4 text-primary" />
              <h2 className="text-sm font-semibold text-foreground">Convene a board</h2>
            </div>
            <label className="block text-xs text-muted-foreground">
              Title
              <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Oncology MDT — weekly"
                className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
            </label>
            <label className="block text-xs text-muted-foreground">
              Specialty
              <input value={specialty} onChange={(e) => setSpecialty(e.target.value)} placeholder="ONCOLOGY"
                className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
            </label>
            <label className="block text-xs text-muted-foreground">
              Scribe (provider id, optional)
              <input value={scribeId} onChange={(e) => setScribeId(e.target.value)}
                className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
            </label>
            <p className="text-[11px] text-muted-foreground">
              You chair the board. Presentation defaults to pseudonymised — panel members see
              &ldquo;Case N&rdquo;, never patient identity; recommendations only, no orders.
            </p>
            <p className="text-[11px] text-muted-foreground">
              A board&rsquo;s recommendation is not itself the treatment-intent decision. Once the
              board has met, record the adopted decision on the patient&rsquo;s own Consultations
              page, where it is attributed and linked to their problem list.
            </p>
            <button type="button" disabled={busy} onClick={convene}
              className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50">
              {busy ? "Convening…" : "Convene board"}
            </button>
            {error && <p className="text-xs text-destructive">{error}</p>}
          </div>

          <div className="rounded-lg border border-border bg-card p-4 space-y-2">
            <h2 className="text-sm font-semibold text-foreground">Open an existing board</h2>
            <div className="flex gap-2">
              <input value={openId} onChange={(e) => setOpenId(e.target.value)} placeholder="Board session id"
                className="min-w-0 flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
              <button type="button" disabled={!openId.trim()}
                onClick={() => router.push(`/work/telemedicine/mdt/${openId.trim()}`)}
                className="rounded-md border border-border px-3 py-1.5 text-sm disabled:opacity-50">
                Open
              </button>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
