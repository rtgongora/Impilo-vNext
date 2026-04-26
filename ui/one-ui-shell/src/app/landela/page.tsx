'use client';

import { useLandelaAdapter } from '@/hooks/useLandelaAdapter';

export default function LandelaShellPage() {
  const l = useLandelaAdapter();
  return (
    <main className="mx-auto max-w-2xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">Landela adapter</h1>
      <p className="text-sm text-muted-foreground">
        <code>{l.module}</code> — {l.labels.join(' · ')}
      </p>
    </main>
  );
}
