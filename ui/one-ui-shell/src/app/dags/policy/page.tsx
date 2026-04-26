'use client';

import { useDagsPolicyHints } from '@/hooks/useDagsPolicyHints';

export default function DagsPolicyPage() {
  const d = useDagsPolicyHints();
  return (
    <main className="mx-auto max-w-2xl p-6">
      <p className="text-sm text-muted-foreground">Policy shell — {d.ref}</p>
    </main>
  );
}
