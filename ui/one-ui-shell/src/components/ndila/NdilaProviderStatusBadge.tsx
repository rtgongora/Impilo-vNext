"use client";

/**
 * NdilaProviderStatusBadge — small status pill that surfaces which Ndila
 * provider was used for the most recent response, whether it was a fallback,
 * and whether the result came from cache. Helps operators and Nompilo
 * justify provider behaviour and lets QA confirm the provider policy.
 */

import { ShieldCheck, Wifi, WifiOff } from "lucide-react";

interface Props {
  provider?: string;
  fallbackUsed?: boolean;
  servedFromCache?: boolean;
  policyDecisionId?: string;
  productionSafe?: boolean;
}

export function NdilaProviderStatusBadge({
  provider, fallbackUsed, servedFromCache, policyDecisionId, productionSafe = true,
}: Props) {
  if (!provider) return null;
  return (
    <span
      title={policyDecisionId ? `Policy decision: ${policyDecisionId}` : undefined}
      className="inline-flex items-center gap-1 rounded-full bg-neutral-100 border border-border px-2 py-0.5 text-[10px] text-foreground"
    >
      {productionSafe ? <ShieldCheck className="h-3 w-3 text-primary" /> : <ShieldCheck className="h-3 w-3 text-red-600" />}
      <span className="font-medium">{provider}</span>
      {fallbackUsed && <span className="text-warning-foreground">fallback</span>}
      {servedFromCache ? <Wifi className="h-3 w-3 text-primary" /> : <WifiOff className="h-3 w-3 text-muted-foreground" />}
    </span>
  );
}
