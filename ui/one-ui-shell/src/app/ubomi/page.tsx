'use client';

import { AppLayout } from '@/components/AppLayout';
import { FeatureMaturityBadge } from '@/components/FeatureMaturityBadge';
import { PageShell } from '@/components/PageShell';
import { useUbomiRegistry } from '@/hooks/queries/useUbomiRegistry';
import { UBOMI_SERVICE_MODULE } from '@/hooks/useUbomiCivilRegistry';

/**
 * UBOMI civil registry — honest surfacing until BFF workflows are canonical.
 */
export default function UbomiShellPage() {
  const { data, isLoading, isError } = useUbomiRegistry();
  const available = data?.available === true;
  const maturity = available ? 'partial' : isLoading ? 'partial' : 'not_wired';

  return (
    <AppLayout>
      <PageShell
        title="UBOMI civil registry"
        subtitle="Birth and death notification (CRVS) — sovereign ubomi-service"
      >
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <FeatureMaturityBadge
              status={maturity}
              detail={
                available
                  ? 'BFF status probe succeeded; full workflows may still be partial.'
                  : 'No Experience BFF bridge for UBOMI workflows yet.'
              }
            />
            <span className="text-sm text-muted-foreground">
              Module <code>{UBOMI_SERVICE_MODULE}</code>
            </span>
          </div>

          {isLoading ? (
            <p className="text-sm text-muted-foreground">Checking registry availability…</p>
          ) : null}

          {isError ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
              Could not reach the UBOMI status probe. Civil registry workflows remain unavailable in
              this shell.
            </div>
          ) : null}

          {!isLoading && !available ? (
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-800">
              <p className="font-medium">Not wired in Experience shell</p>
              <p className="mt-2 text-muted-foreground">{data?.message}</p>
              <ul className="mt-3 list-inside list-disc text-muted-foreground">
                <li>Birth notification workflows</li>
                <li>Death notification and PCT linkage</li>
                <li>CRVS verification status</li>
              </ul>
              <p className="mt-3 text-muted-foreground">
                Related: PCT death cases may update UBOMI status via the separate{' '}
                <code className="text-xs">ui/pct-web</code> console when deployed.
              </p>
            </div>
          ) : null}

          {available ? (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
              UBOMI BFF bridge is reachable. Extend this page with birth/death workflows when
              contracts are finalized.
            </div>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
