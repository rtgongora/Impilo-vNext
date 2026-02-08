"use client";

export default function AuditPage() {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold mb-1">Audit Log</h1>
      <p className="text-sm text-neutral-500 mb-6">
        Non-repudiation audit trail for all MSIKA Flow actions.
      </p>
      <div className="card p-12 text-center">
        <p className="text-neutral-500">
          Audit events stream from Kafka topic msika.flow.* and TSHEPO audit feed.
        </p>
      </div>
    </div>
  );
}
