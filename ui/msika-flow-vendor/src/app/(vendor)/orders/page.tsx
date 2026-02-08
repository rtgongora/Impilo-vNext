"use client";

export default function VendorOrdersPage() {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold mb-1">All Orders</h1>
      <p className="text-sm text-neutral-500 mb-6">
        Complete history of orders assigned to your vendor profile.
      </p>
      <div className="card p-12 text-center">
        <p className="text-neutral-500">No order history yet.</p>
      </div>
    </div>
  );
}
