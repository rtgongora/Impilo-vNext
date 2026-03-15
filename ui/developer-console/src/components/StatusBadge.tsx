"use client";

export function ClientStatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    ACTIVE: "bg-success/10 text-success",
    SUSPENDED: "bg-warning/10 text-warning",
    REVOKED: "bg-danger/10 text-danger",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[status] ?? styles.ACTIVE}`}>
      {status}
    </span>
  );
}

export function KeyStatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    ACTIVE: "bg-success/10 text-success",
    ROTATED: "bg-info/10 text-info",
    REVOKED: "bg-danger/10 text-danger",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[status] ?? styles.ACTIVE}`}>
      {status}
    </span>
  );
}

export function CertResultBadge({ result }: { result: string | null }) {
  if (!result) return <span className="text-xs text-neutral-500">Pending</span>;
  const styles: Record<string, string> = {
    PASS: "bg-success/10 text-success",
    FAIL: "bg-danger/10 text-danger",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[result] ?? "bg-neutral-100 text-neutral-500"}`}>
      {result}
    </span>
  );
}

export function ReadinessBadge({ ready }: { ready: boolean }) {
  return ready ? (
    <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-success/10 text-success">Ready</span>
  ) : (
    <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-warning/10 text-warning">Not Ready</span>
  );
}

export function EnvironmentBadge({ env }: { env: string }) {
  const styles: Record<string, string> = {
    SANDBOX: "bg-info/10 text-info",
    PRODUCTION: "bg-brand-primary/10 text-brand-primary",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[env] ?? styles.SANDBOX}`}>
      {env}
    </span>
  );
}
