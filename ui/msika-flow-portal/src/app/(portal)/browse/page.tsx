"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { msikaFlowApi, type CatalogItem } from "@/lib/msikaFlowApi";

export default function BrowsePage() {
  const [items, setItems] = useState<CatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [kindFilter, setKindFilter] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [cart, setCart] = useState<Array<CatalogItem & { qty: number }>>([]);
  const [validating, setValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<string | null>(null);

  const loadCatalog = useCallback(async (p = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data = await msikaFlowApi.searchCatalog({
        q: search || undefined,
        kind: kindFilter || undefined,
        page: p,
        size: 20,
      });
      setItems(data.content);
      setTotalPages(data.totalPages);
      setPage(p);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load catalog");
    } finally {
      setLoading(false);
    }
  }, [search, kindFilter]);

  useEffect(() => {
    loadCatalog(0);
  }, [loadCatalog]);

  const addToCart = (item: CatalogItem) => {
    setCart((prev) => {
      const existing = prev.find((c) => c.canonicalCode === item.canonicalCode);
      if (existing) {
        return prev.map((c) =>
          c.canonicalCode === item.canonicalCode ? { ...c, qty: c.qty + 1 } : c
        );
      }
      return [...prev, { ...item, qty: 1 }];
    });
  };

  const removeFromCart = (code: string) => {
    setCart((prev) => prev.filter((c) => c.canonicalCode !== code));
  };

  const validateCart = async () => {
    setValidating(true);
    setValidationResult(null);
    try {
      const cartItems = cart.map((c) => ({ msikaCoreCode: c.canonicalCode, qty: c.qty }));
      const result = await msikaFlowApi.validateCart(cartItems, "portal");
      setValidationResult(
        result.valid
          ? "All items validated successfully!"
          : "Some items have restrictions. Check details."
      );
    } catch (err) {
      setValidationResult(err instanceof Error ? err.message : "Validation failed");
    } finally {
      setValidating(false);
    }
  };

  const cartTotal = cart.reduce((sum, c) => sum + c.price * c.qty, 0);
  const cartCurrency = cart.length > 0 ? cart[0].currency : "ZWG";

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">Browse Products & Services</h1>
      <p className="text-sm text-neutral-500 mb-6">Search the MSIKA catalog to find health products and services.</p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && loadCatalog(0)}
          placeholder="Search by name or code..."
          className="input-field flex-1 max-w-md"
        />
        <select
          value={kindFilter}
          onChange={(e) => setKindFilter(e.target.value)}
          className="input-field w-40"
        >
          <option value="">All Types</option>
          <option value="PRODUCT">Products</option>
          <option value="SERVICE">Services</option>
        </select>
        <button onClick={() => loadCatalog(0)} className="btn-primary">
          Search
        </button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="card p-4 animate-pulse">
              <div className="h-4 bg-neutral-200 rounded w-3/4 mb-2" />
              <div className="h-3 bg-neutral-100 rounded w-1/2 mb-4" />
              <div className="h-6 bg-neutral-200 rounded w-1/3" />
            </div>
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="card p-12 text-center mb-8">
          <p className="text-neutral-500">No catalog items found. Try a different search.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
          {items.map((item) => (
            <div key={item.itemId} className="card p-4">
              <div className="flex justify-between items-start mb-2">
                <div>
                  <h3 className="font-medium text-neutral-900">{item.displayName}</h3>
                  <p className="text-xs text-neutral-500 font-mono">{item.canonicalCode}</p>
                  {item.description && (
                    <p className="text-xs text-neutral-400 mt-1 line-clamp-2">{item.description}</p>
                  )}
                </div>
                <span className="badge bg-neutral-100 text-neutral-700 text-xs">{item.kind}</span>
              </div>
              <div className="flex justify-between items-center mt-3">
                <span className="text-lg font-semibold text-brand-primary">
                  {item.currency} {item.price.toFixed(2)}
                </span>
                <button
                  onClick={() => addToCart(item)}
                  disabled={!item.available}
                  className="btn-primary text-xs px-3 py-1 disabled:opacity-50"
                >
                  {item.available ? "Add to Cart" : "Unavailable"}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mb-8">
          <button
            onClick={() => loadCatalog(page - 1)}
            disabled={page === 0 || loading}
            className="btn-secondary text-xs px-3 py-1 disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-neutral-500">Page {page + 1} of {totalPages}</span>
          <button
            onClick={() => loadCatalog(page + 1)}
            disabled={page >= totalPages - 1 || loading}
            className="btn-secondary text-xs px-3 py-1 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}

      {cart.length > 0 && (
        <div className="card p-6">
          <h2 className="text-lg font-semibold mb-4">Cart ({cart.length} items)</h2>
          <div className="space-y-2 mb-4">
            {cart.map((item) => (
              <div key={item.canonicalCode} className="flex justify-between items-center py-2 border-b border-neutral-100">
                <div>
                  <span className="text-sm font-medium">{item.displayName}</span>
                  <span className="text-xs text-neutral-500 ml-2">x{item.qty}</span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold">{item.currency} {(item.price * item.qty).toFixed(2)}</span>
                  <button onClick={() => removeFromCart(item.canonicalCode)} className="text-xs text-red-600 hover:text-red-800">
                    Remove
                  </button>
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-between items-center pt-2 border-t border-neutral-200">
            <span className="font-semibold">Total: {cartCurrency} {cartTotal.toFixed(2)}</span>
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
