'use client';

import { useFacilities } from '@/hooks/queries/useFacilities';

export default function TusoShellPage() {
  const { data, isLoading, isError } = useFacilities({ page: 0, size: 25 });
  const facilities = data?.data ?? [];

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">Facility registry (TUSO)</h1>
      <p className="text-sm text-muted-foreground">
        Facility catalogue via <code>/internal/v1/facilities</code>.
      </p>
      {isLoading ? <p className="text-sm text-muted-foreground">Loading facilities…</p> : null}
      {isError ? <p className="text-sm text-destructive">Facility registry unavailable.</p> : null}
      <ul className="space-y-2">
        {facilities.slice(0, 25).map((facility) => (
          <li key={facility.id} className="rounded-lg border border-border bg-card p-3 text-sm">
            {facility.attributes?.name ?? facility.id}
          </li>
        ))}
      </ul>
    </main>
  );
}
