"use client";

import { useState } from "react";
import { PageShell } from "@/components/PageShell";
import {
  useAccreditProvider,
  useDecideProviderApplication,
  useFundoProviderApplications,
  useSubmitProviderApplication,
} from "@/hooks/queries/useFundoProviders";

type Application = {
  id: string;
  providerKind?: string;
  requestedName: string;
  targetIdentityRef?: string;
  status?: string;
  provisionedProviderId?: string;
};

const KINDS = ["INDIVIDUAL", "ORGANISATION", "FACILITY"];

export default function AccreditationPage() {
  const { data } = useFundoProviderApplications();
  const submit = useSubmitProviderApplication();
  const decide = useDecideProviderApplication();
  const accredit = useAccreditProvider();

  const [name, setName] = useState("");
  const [kind, setKind] = useState("ORGANISATION");
  const [ref, setRef] = useState("");

  const apps = (((data?.data as { items?: Application[] } | undefined)?.items) ?? []);

  return (
    <PageShell title="Provider accreditation" subtitle="Request → review → accredit → provision. Each kind is routed to its regulator.">
      <section className="rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Request to set up a learning provider</p>
        <div className="mt-2 grid gap-2 md:grid-cols-4">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Provider / academy name" className="rounded border border-border px-3 py-2 text-sm md:col-span-2" />
          <select value={kind} onChange={(e) => setKind(e.target.value)} className="rounded border border-border px-3 py-2 text-sm">
            {KINDS.map((k) => <option key={k}>{k}</option>)}
          </select>
          <input value={ref} onChange={(e) => setRef(e.target.value)} placeholder="Identity ref (SoR id)" className="rounded border border-border px-3 py-2 text-sm" />
        </div>
        <button
          onClick={async () => { await submit.mutateAsync({ providerKind: kind, requestedName: name, targetIdentityRef: ref }); setName(""); setRef(""); }}
          disabled={!name.trim() || submit.isPending}
          className="mt-2 rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60"
        >Submit request</button>
      </section>

      <section className="mt-4 rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Applications ({apps.length})</p>
        <ul className="mt-2 space-y-2 text-sm" data-testid="provider-application-list">
          {apps.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded border border-border px-3 py-2">
              <span>{a.requestedName} · <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{a.providerKind}</span> · {a.status ?? "SUBMITTED"}</span>
              <span className="flex gap-2">
                {a.status !== "ACCREDITED" && (
                  <>
                    <button onClick={() => decide.mutate({ applicationId: a.id, body: { status: "UNDER_REVIEW" } })} className="text-teal-700 hover:underline">Review</button>
                    <button onClick={() => decide.mutate({ applicationId: a.id, body: { status: "REJECTED" } })} className="text-rose-700 hover:underline">Reject</button>
                    <button onClick={() => accredit.mutate(a.id)} disabled={accredit.isPending} className="rounded bg-emerald-600 px-2 py-0.5 text-xs text-white disabled:opacity-60">Accredit</button>
                  </>
                )}
                {a.status === "ACCREDITED" && <span className="text-xs text-emerald-700">Provisioned</span>}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </PageShell>
  );
}
