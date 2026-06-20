'use client';

import Link from 'next/link';
import { useGovernancePolicies } from '@/hooks/queries/useDataAccessGovernance';

export default function DagsPolicyPage() {
  const { data, isLoading } = useGovernancePolicies();
  const policies = data?.data ?? [];

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-6">
      <Link href="/dags" className="text-sm text-muted-foreground hover:text-foreground">
        ← DAGS home
      </Link>
      <h1 className="text-xl font-semibold">Governance policy catalogue</h1>
      <p className="text-sm text-muted-foreground">
        Active and draft policies from the Experience BFF governance surface.
      </p>
      {isLoading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
      <ul className="space-y-2">
        {policies.map((policy) => (
          <li key={policy.id} className="rounded-lg border border-border bg-card p-3 text-sm">
            <div className="font-medium">{policy.attributes?.name ?? policy.id}</div>
            <div className="text-xs text-muted-foreground">{policy.attributes?.description ?? '—'}</div>
          </li>
        ))}
      </ul>
    </main>
  );
}
