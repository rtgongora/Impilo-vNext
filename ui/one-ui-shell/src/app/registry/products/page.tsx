"use client";

/**
 * Product Registry — Browse registered products.
 * Route: /registry/products
 */

import { useState } from "react";
import Link from "next/link";
import { Loader2, AlertTriangle, Package, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useProductRegistrySearch } from "@/hooks/queries/useProductRegistry";

type AnyRecord = Record<string, unknown>;

interface ProductRow {
  id: string;
  name: string;
  code: string;
  category: string;
  manufacturer: string;
  status: string;
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-neutral-100 text-foreground",
  DISCONTINUED: "bg-red-100 text-danger",
};

function unwrap(value: unknown): unknown {
  if (value && typeof value === "object" && "data" in value) {
    return unwrap((value as { data?: unknown }).data);
  }
  return value;
}

function collection(value: unknown): AnyRecord[] {
  const unwrapped = unwrap(value);
  if (Array.isArray(unwrapped)) return unwrapped.filter((item): item is AnyRecord => typeof item === "object" && item !== null);
  if (unwrapped && typeof unwrapped === "object") {
    const record = unwrapped as AnyRecord;
    if (Array.isArray(record.items)) return collection(record.items);
    if (Array.isArray(record.content)) return collection(record.content);
    if (Array.isArray(record.results)) return collection(record.results);
  }
  return [];
}

function text(record: AnyRecord, keys: string[], fallback = "") {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return fallback;
}

function normalizeProduct(record: AnyRecord, index: number): ProductRow {
  const attributes = record.attributes && typeof record.attributes === "object" ? (record.attributes as AnyRecord) : record;
  return {
    id: text(record, ["id", "itemId", "code", "sku"], `product-${index + 1}`),
    name: text(attributes, ["name", "displayName", "title", "label"], "Product"),
    code: text(attributes, ["code", "sku", "itemCode", "productCode"], "n/a"),
    category: text(attributes, ["category", "kind", "type", "classification"], "Unclassified"),
    manufacturer: text(attributes, ["manufacturer", "supplier", "brand"], "Not stated"),
    status: text(attributes, ["status", "lifecycleStatus"], "ACTIVE"),
  };
}

export default function ProductRegistryPage() {
  const [searchTerm, setSearchTerm] = useState("");
  const [submittedSearch, setSubmittedSearch] = useState("");

  const { data, isLoading, error } = useProductRegistrySearch({
    q: submittedSearch,
    size: 20,
    page: 0,
  });

  const products = collection(data).map(normalizeProduct);

  return (
    <AppLayout>
      <PageShell title="Product Registry" subtitle="Browse registered medical products">
        <div className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search products..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && setSubmittedSearch(searchTerm)}
              className="w-full pl-10 pr-4 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
            />
          </div>
          <button
            type="button"
            onClick={() => setSubmittedSearch(searchTerm)}
            className="mt-3 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
          >
            Search MSIKA registry
          </button>
          <p className="mt-2 text-xs text-muted-foreground">
            Uses canonical <code>/internal/v1/product-registry/search</code>, not the legacy registry product alias.
          </p>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading products...</span>
          </div>
        ) : error ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load products</p>
          </div>
        ) : products.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Package className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No products found</p>
          </div>
        ) : (
          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Product Name</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Code</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Category</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Manufacturer</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                  <th className="text-right px-4 py-3 font-medium text-muted-foreground">Action</th>
                </tr>
              </thead>
              <tbody>
                {products.map((product) => {
                  const statusStyle = STATUS_STYLES[product.status] ?? "bg-neutral-100 text-foreground";
                  return (
                    <tr key={product.id} className="border-b last:border-b-0 hover:bg-background">
                      <td className="px-4 py-3 font-medium text-foreground">{product.name}</td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{product.code}</td>
                      <td className="px-4 py-3 text-muted-foreground">{product.category}</td>
                      <td className="px-4 py-3 text-muted-foreground">{product.manufacturer}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {product.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Link
                          href={`/registry/products/${product.id}`}
                          className="text-xs text-primary hover:text-primary-hover font-medium"
                        >
                          View
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
