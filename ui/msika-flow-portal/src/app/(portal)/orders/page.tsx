"use client";

export default function OrdersPage() {
  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">My Orders</h1>
      <p className="text-sm text-neutral-500 mb-6">Track your health marketplace orders and pickups.</p>

      <div className="card p-12 text-center">
        <p className="text-neutral-500">No orders yet. Browse the marketplace to place your first order.</p>
      </div>
    </div>
  );
}
