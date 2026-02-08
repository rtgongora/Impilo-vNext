"use client";

export default function VendorsPage() {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold mb-1">Vendor Management</h1>
      <p className="text-sm text-neutral-500 mb-6">
        View, approve, suspend, and audit vendor profiles.
      </p>
      <div className="card p-12 text-center">
        <p className="text-neutral-500">
          Vendor list will appear here. Use Reviews tab to approve pending vendors.
        </p>
      </div>
    </div>
  );
}
