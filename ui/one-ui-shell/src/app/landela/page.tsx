'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export default function LandelaShellPage() {
  const templatesQ = useQuery({
    queryKey: ['landela', 'templates'],
    queryFn: () => apiClient.get<unknown>('/internal/v1/access/landela/templates'),
  });

  return (
    <main className="mx-auto max-w-2xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">Landela document gateway</h1>
      <p className="text-sm text-muted-foreground">
        Template catalogue via <code>/internal/v1/access/landela/templates</code>.
      </p>
      {templatesQ.isLoading ? <p className="text-sm text-muted-foreground">Loading templates…</p> : null}
      {templatesQ.isError ? <p className="text-sm text-destructive">Landela templates unavailable.</p> : null}
      {templatesQ.isSuccess ? (
        <pre className="overflow-auto rounded-lg border border-border bg-muted/30 p-3 text-xs">
          {JSON.stringify(templatesQ.data, null, 2)}
        </pre>
      ) : null}
    </main>
  );
}
