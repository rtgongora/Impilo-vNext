"use client";

export default function OpsOrdersPage() {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold mb-1">All Orders</h1>
      <p className="text-sm text-neutral-500 mb-6">Cross-tenant order search and audit.</p>
      <div className="card p-12 text-center">
        <p className="text-neutral-500">
          Use the search bar to find orders by ID, patient CPID, or status.
        </p>
      </div>
    </div>
  );
}
