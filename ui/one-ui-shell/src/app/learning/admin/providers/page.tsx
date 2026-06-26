"use client";

import { useState } from "react";
import { PageShell } from "@/components/PageShell";
import {
  useCreateFundoProvider,
  useCreateFundoSpace,
  useFundoProviders,
  useFundoProviderSpaces,
} from "@/hooks/queries/useFundoProviders";

type Provider = { id: string; providerKind?: string; displayName: string; accreditationStatus?: string; sorRef?: string };
type Space = { id: string; name: string; spaceKind?: string; orgUnitRef?: string };

const KINDS = ["INDIVIDUAL", "ORGANISATION", "FACILITY"];

export default function ProvidersAdminPage() {
  const { data } = useFundoProviders();
  const createProvider = useCreateFundoProvider();
  const [name, setName] = useState("");
  const [kind, setKind] = useState("ORGANISATION");
  const [sorRef, setSorRef] = useState("");
  const [selected, setSelected] = useState<string | undefined>(undefined);
  const { data: spaceData } = useFundoProviderSpaces(selected);
  const createSpace = useCreateFundoSpace(selected ?? "");
  const [spaceName, setSpaceName] = useState("");

  const providers = (((data?.data as { items?: Provider[] } | undefined)?.items) ?? []);
  const spaces = (((spaceData?.data as { items?: Space[] } | undefined)?.items) ?? []);

  return (
    <PageShell title="Learning providers & academies" subtitle="Individual, organisational and facility providers — each regulated by its own system of record.">
      <section className="rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Register provider</p>
        <div className="mt-2 grid gap-2 md:grid-cols-4">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Display name" className="rounded border border-border px-3 py-2 text-sm md:col-span-2" />
          <select value={kind} onChange={(e) => setKind(e.target.value)} className="rounded border border-border px-3 py-2 text-sm">
            {KINDS.map((k) => <option key={k}>{k}</option>)}
          </select>
          <input value={sorRef} onChange={(e) => setSorRef(e.target.value)} placeholder={kind === "INDIVIDUAL" ? "Varapi provider id" : kind === "FACILITY" ? "TUSO facility id" : "Governance org id"} className="rounded border border-border px-3 py-2 text-sm" />
        </div>
        <button
          onClick={async () => { await createProvider.mutateAsync({ providerKind: kind, displayName: name, sorRef }); setName(""); setSorRef(""); }}
          disabled={!name.trim() || createProvider.isPending}
          className="mt-2 rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60"
        >Register provider</button>
        <ul className="mt-3 space-y-2 text-sm" data-testid="provider-list">
          {providers.map((p) => (
            <li key={p.id} className="flex items-center justify-between rounded border border-border px-3 py-2">
              <span>{p.displayName} · <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{p.providerKind}</span> · {p.accreditationStatus ?? "UNACCREDITED"}</span>
              <button onClick={() => setSelected(p.id)} className="text-teal-700 hover:underline">Academies</button>
            </li>
          ))}
        </ul>
      </section>

      {selected && (
        <section className="mt-4 rounded-lg border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Academies / spaces for selected provider</p>
          <div className="mt-2 flex gap-2">
            <input value={spaceName} onChange={(e) => setSpaceName(e.target.value)} placeholder="Academy / school / centre name" className="flex-1 rounded border border-border px-3 py-2 text-sm" />
            <button onClick={async () => { await createSpace.mutateAsync({ name: spaceName, spaceKind: "ACADEMY" }); setSpaceName(""); }} disabled={!spaceName.trim() || createSpace.isPending} className="rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60">Add academy</button>
          </div>
          <ul className="mt-3 space-y-1 text-sm">
            {spaces.map((s) => <li key={s.id} className="rounded border border-border px-3 py-1.5">{s.name} · {s.spaceKind ?? "ACADEMY"}{s.orgUnitRef ? ` · unit ${s.orgUnitRef}` : ""}</li>)}
          </ul>
        </section>
      )}
    </PageShell>
  );
}
