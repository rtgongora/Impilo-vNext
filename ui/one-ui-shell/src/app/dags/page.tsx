'use client';

import { useGovernancePolicies } from '@/hooks/queries/useDataAccessGovernance';

export default function DagsShellPage() {
  const { data, isLoading, isError } = useGovernancePolicies();
  const policies = data?.data ?? [];

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">Data Access Governance</h1>
      <p className="text-sm text-muted-foreground">
        Policies from <code>/internal/v1/governance/access/policies</code>.
      </p>
      {isLoading ? <p className="text-sm text-muted-foreground">Loading policies…</p> : null}
      {isError ? <p className="text-sm text-destructive">Unable to load governance policies.</p> : null}
      {!isLoading && !isError && policies.length === 0 ? (
        <p className="text-sm text-muted-foreground">No governance policies returned for your scope.</p>
      ) : null}
      <ul className="space-y-2">
        {policies.slice(0, 20).map((policy) => (
          <li key={policy.id} className="rounded-lg border border-border bg-card p-3 text-sm">
            <span className="font-medium">{policy.attributes?.name ?? policy.id}</span>
            <span className="ml-2 text-muted-foreground">{policy.attributes?.status ?? ''}</span>
          </li>
        ))}
      </ul>
    </main>
  );
}
