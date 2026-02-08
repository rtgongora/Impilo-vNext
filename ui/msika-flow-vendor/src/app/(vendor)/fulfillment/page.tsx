"use client";

export default function FulfillmentPage() {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold mb-1">Fulfillment</h1>
      <p className="text-sm text-neutral-500 mb-6">
        Track orders through fulfillment stages: picking, packing, ready for
        pickup/delivery.
      </p>
      <div className="card p-12 text-center">
        <p className="text-neutral-500">No active fulfillment tasks.</p>
      </div>
    </div>
  );
}
