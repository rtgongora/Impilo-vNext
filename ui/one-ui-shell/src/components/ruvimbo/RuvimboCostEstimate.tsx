"use client";

import { useState } from "react";
import { Calculator, Loader2 } from "lucide-react";
import { useCostEstimate } from "@/hooks/queries/useRuvimbo";
import { RuvimboSection } from "./RuvimboSection";
import { asRecord, readNum, readStr } from "./util";

/**
 * Composed cost estimate widget — sources a service's price from COSTA and runs coverage
 * liability rules over it (BFF /coverage/cost-estimate). Reused by the citizen (their own
 * cpid) and the provider (any member). Clearly labelled an estimate, not a final decision.
 */
export function RuvimboCostEstimate({
  defaultCpid,
  editableCpid,
  heading = "Estimate my cost",
}: {
  defaultCpid?: string;
  editableCpid?: boolean;
  heading?: string;
}) {
  const [cpid, setCpid] = useState(defaultCpid ?? "");
  const [code, setCode] = useState("");
  const [submitted, setSubmitted] = useState<{ cpid: string; code: string } | null>(null);
  const q = useCostEstimate(submitted?.cpid, submitted?.code);
  const result = q.data ? asRecord(q.data) : null;
  const estimate = result ? asRecord(result.estimate) : null;

  const effectiveCpid = editableCpid ? cpid : defaultCpid ?? "";

  return (
    <RuvimboSection title={heading} icon={<Calculator className="h-4 w-4 text-violet-700" />}>
      <div className={`grid gap-2 ${editableCpid ? "sm:grid-cols-3" : "sm:grid-cols-2"}`}>
        {editableCpid ? (
          <input className="rounded-lg border border-border px-3 py-2 text-sm" placeholder="Member CPID *" value={cpid} onChange={(e) => setCpid(e.target.value)} />
        ) : null}
        <input className="rounded-lg border border-border px-3 py-2 text-sm" placeholder="Service / tariff code *" value={code} onChange={(e) => setCode(e.target.value)} />
        <button
          type="button"
          disabled={!effectiveCpid || !code || q.isFetching}
          onClick={() => setSubmitted({ cpid: effectiveCpid, code })}
          className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
        >
          {q.isFetching ? <Loader2 className="mr-1 inline h-4 w-4 animate-spin" /> : null}Estimate
        </button>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">Sources the price from the tariff schedule and applies your coverage rules. An estimate, not a final claim decision.</p>

      {submitted && q.isError ? <p className="mt-3 text-sm text-danger">Could not produce an estimate for that service.</p> : null}

      {result ? (
        <div className="mt-3 rounded-lg border border-border p-3 text-sm">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-foreground">{readStr(result, "description") || readStr(result, "serviceCode")}</span>
            <span className="text-muted-foreground">{readStr(result, "currency") || "USD"} {readNum(result, "price").toLocaleString()}</span>
          </div>
          {result.covered === false ? (
            <p className="mt-2 text-warning-foreground">{readStr(result, "message") || "No active coverage — full charge is the patient's responsibility."}</p>
          ) : estimate ? (
            <div className="mt-2 grid grid-cols-2 gap-1 sm:grid-cols-4">
              <Cell label="Allowed" v={readNum(estimate, "allowedAmount", "allowed")} />
              <Cell label="Payer" v={readNum(estimate, "payerEstimate", "payerAmount")} />
              <Cell label="Co-payment" v={readNum(estimate, "copay", "copayAmount")} />
              <Cell label="You pay" v={readNum(estimate, "patientResponsibility")} strong />
            </div>
          ) : null}
        </div>
      ) : null}
    </RuvimboSection>
  );
}

function Cell({ label, v, strong }: { label: string; v: number; strong?: boolean }) {
  return (
    <div className="text-muted-foreground">
      {label}
      <span className={`block ${strong ? "text-base font-bold text-foreground" : "font-semibold text-foreground"}`}>{v.toLocaleString()}</span>
    </div>
  );
}
