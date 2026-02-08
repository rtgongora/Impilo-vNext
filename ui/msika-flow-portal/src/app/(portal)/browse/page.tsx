"use client";

import { useState } from "react";
import Link from "next/link";
import { msikaFlowApi } from "@/lib/msikaFlowApi";

interface CatalogItem {
  code: string;
  name: string;
  category: string;
  price: number;
}

const SAMPLE_ITEMS: CatalogItem[] = [
  { code: "OTC-PARA-500", name: "Paracetamol 500mg (20 tabs)", category: "OTC", price: 2.50 },
  { code: "OTC-IBUP-400", name: "Ibuprofen 400mg (16 tabs)", category: "OTC", price: 3.00 },
  { code: "OTC-ORS-SAC", name: "ORS Sachets (10 pack)", category: "OTC", price: 1.50 },
  { code: "OTC-ZINC-20", name: "Zinc 20mg Tablets (10)", category: "OTC", price: 2.00 },
  { code: "SVC-CONSULT-GP", name: "GP Consultation", category: "Service", price: 15.00 },
  { code: "SVC-LAB-FBC", name: "Full Blood Count", category: "Service", price: 12.00 },
  { code: "SVC-XRAY-CHEST", name: "Chest X-Ray", category: "Service", price: 25.00 },
  { code: "SVC-DENTAL-CHECK", name: "Dental Check-up", category: "Service", price: 20.00 },
];

export default function BrowsePage() {
  const [cart, setCart] = useState<Array<CatalogItem & { qty: number }>>([]);
  const [search, setSearch] = useState("");
  const [validating, setValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<string | null>(null);

  const filtered = SAMPLE_ITEMS.filter(
    (item) =>
      item.name.toLowerCase().includes(search.toLowerCase()) ||
      item.code.toLowerCase().includes(search.toLowerCase())
  );

  const addToCart = (item: CatalogItem) => {
    setCart((prev) => {
      const existing = prev.find((c) => c.code === item.code);
      if (existing) {
        return prev.map((c) => (c.code === item.code ? { ...c, qty: c.qty + 1 } : c));
      }
      return [...prev, { ...item, qty: 1 }];
    });
  };

  const removeFromCart = (code: string) => {
    setCart((prev) => prev.filter((c) => c.code !== code));
  };

  const validateCart = async () => {
    setValidating(true);
    setValidationResult(null);
    try {
      const items = cart.map((c) => ({ msikaCoreCode: c.code, qty: c.qty }));
      const result = await msikaFlowApi.validateCart(items, "portal");
      setValidationResult(result.valid ? "All items validated successfully!" : "Some items have restrictions. Check details.");
    } catch (err) {
      setValidationResult(err instanceof Error ? err.message : "Validation failed");
    } finally {
      setValidating(false);
    }
  };

  const cartTotal = cart.reduce((sum, c) => sum + c.price * c.qty, 0);

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">Browse Products & Services</h1>
      <p className="text-sm text-neutral-500 mb-6">Search the MSIKA catalog to find health products and services.</p>

      <div className="mb-6">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name or code..."
          className="input-field max-w-md"
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        {filtered.map((item) => (
          <div key={item.code} className="card p-4">
            <div className="flex justify-between items-start mb-2">
              <div>
                <h3 className="font-medium text-neutral-900">{item.name}</h3>
                <p className="text-xs text-neutral-500 font-mono">{item.code}</p>
              </div>
              <span className="badge bg-neutral-100 text-neutral-700">{item.category}</span>
            </div>
            <div className="flex justify-between items-center mt-3">
              <span className="text-lg font-semibold text-brand-primary">ZWG {item.price.toFixed(2)}</span>
              <button onClick={() => addToCart(item)} className="btn-primary text-xs px-3 py-1">
                Add to Cart
              </button>
            </div>
          </div>
        ))}
      </div>

      {cart.length > 0 && (
        <div className="card p-6">
          <h2 className="text-lg font-semibold mb-4">Cart ({cart.length} items)</h2>
          <div className="space-y-2 mb-4">
            {cart.map((item) => (
              <div key={item.code} className="flex justify-between items-center py-2 border-b border-neutral-100">
                <div>
                  <span className="text-sm font-medium">{item.name}</span>
                  <span className="text-xs text-neutral-500 ml-2">x{item.qty}</span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold">ZWG {(item.price * item.qty).toFixed(2)}</span>
                  <button onClick={() => removeFromCart(item.code)} className="text-xs text-red-600 hover:text-red-800">
                    Remove
                  </button>
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-between items-center pt-2 border-t border-neutral-200">
            <span className="font-semibold">Total: ZWG {cartTotal.toFixed(2)}</span>
            <div className="flex gap-2">
              <button onClick={validateCart} disabled={validating} className="btn-secondary">
                {validating ? "Validating..." : "Validate Cart"}
              </button>
              <Link href="/cart" className="btn-primary">Proceed to Checkout</Link>
            </div>
          </div>
          {validationResult && (
            <p className={`mt-3 text-sm ${validationResult.includes("success") ? "text-emerald-600" : "text-red-600"}`}>
              {validationResult}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
