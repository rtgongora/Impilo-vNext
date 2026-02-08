"use client";

import { useState } from "react";

export default function CartPage() {
  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">Checkout</h1>
      <p className="text-sm text-neutral-500 mb-6">Review your cart, confirm pricing, and proceed to payment.</p>

      <div className="card p-12 text-center">
        <p className="text-neutral-500 mb-4">Your cart is empty.</p>
        <a href="/browse" className="btn-primary">Browse Products</a>
      </div>
    </div>
  );
}
