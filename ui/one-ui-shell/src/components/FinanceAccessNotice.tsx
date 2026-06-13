/**
 * Route-level copy for MusheX-backed finance surfaces proxied by Experience BFF.
 * Aligns with experience-bff SecurityConfig role groups (JWT realm roles).
 */

export function FinancePayerOpsReconciliationNotice() {
  return (
    <div className="rounded-lg border border-border bg-background p-4 text-sm text-foreground">
      <p className="font-medium text-foreground">Finance / admin access boundary</p>
      <p className="mt-1 text-xs leading-relaxed">
        The Experience BFF allows these endpoints only for{" "}
        <strong className="font-semibold text-foreground">SYSTEM_ADMIN</strong>,{" "}
        <strong className="font-semibold text-foreground">FINANCE</strong>, and{" "}
        <strong className="font-semibold text-foreground">DEVELOPER</strong> (see experience-bff payer-ops and
        reconciliation matchers). <strong className="font-semibold text-foreground">FACILITY_ADMIN</strong> is not in that
        list. A 403 means your signed-in actor lacks the role — this UI does not grant or bypass server authorization.
      </p>
    </div>
  );
}

export function FinanceRefundsNotice() {
  return (
    <div className="rounded-lg border border-border bg-background p-4 text-sm text-foreground">
      <p className="font-medium text-foreground">Finance access boundary</p>
      <p className="mt-1 text-xs leading-relaxed">
        Refund endpoints live under <code className="text-[11px]">/internal/v1/finance/refunds/*</code>, which the BFF
        covers with the general finance rule: <strong className="font-semibold text-foreground">SYSTEM_ADMIN</strong>,{" "}
        <strong className="font-semibold text-foreground">FACILITY_ADMIN</strong>, or{" "}
        <strong className="font-semibold text-foreground">FINANCE</strong>. This page still performs sensitive money
        movement — use least privilege and audit trails outside Experience as required by policy.
      </p>
    </div>
  );
}
