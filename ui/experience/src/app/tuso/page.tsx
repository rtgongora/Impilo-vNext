'use client';

import { useTusoRegistry } from '@/hooks/useTusoRegistry';

export default function TusoShellPage() {
  const t = useTusoRegistry();
  return (
    <main className="mx-auto max-w-2xl space-y-4 p-6">
      <h1 className="text-xl font-semibold">TUSO</h1>
      <p className="text-sm text-muted-foreground">
        <code>{t.module}</code> — {t.labels.join(' · ')}
      </p>
    </main>
  );
}
