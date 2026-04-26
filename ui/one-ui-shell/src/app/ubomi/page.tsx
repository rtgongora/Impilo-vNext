'use client';

import { useUbomiCivilRegistry } from '@/hooks/useUbomiCivilRegistry';

/**
 * UBOMI civil registry — minimal shell page (extend with workflows when wired to ubomi-service).
 */
export default function UbomiShellPage() {
  const ubomi = useUbomiCivilRegistry();
  return (
    <main className="mx-auto max-w-2xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">UBOMI civil registry</h1>
      <p className="text-sm text-muted-foreground">
        Module <code>{ubomi.module}</code> — {ubomi.labels.join(' · ')}
      </p>
    </main>
  );
}
