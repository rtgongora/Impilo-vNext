"use client";

export default function ApprovalsPage() {
  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Approval Queue</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Bills pending approval. Review, approve, or reject bills before finalization.
        </p>
      </div>

      <div className="card p-12 text-center text-neutral-500 text-sm">
        <p className="mb-2">The approval queue automatically loads bills with status <strong>PENDING_APPROVAL</strong>.</p>
        <p>Use the <strong>Bills</strong> page to search for specific bills and perform approval actions.</p>
      </div>
    </div>
  );
}
