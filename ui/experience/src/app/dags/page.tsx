'use client';

import { useDagsAccessGovernance } from '@/hooks/useDagsAccessGovernance';

export default function DagsShellPage() {
  const d = useDagsAccessGovernance();
  return (
    <main className="mx-auto max-w-2xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">DAGS</h1>
      <p className="text-sm text-muted-foreground">
        <code>{d.module}</code> — {d.labels.join(' · ')}
      </p>
    </main>
  );
}
