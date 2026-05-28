"use client";

/**
 * Product Detail — View product information.
 * Route: /registry/products/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft, Package } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useProductRegistryItem } from "@/hooks/queries/useProductRegistry";

type AnyRecord = Record<string, unknown>;

interface ProductDetail {
  id: string;
  name: string;
  code: string;
  category: string;
  manufacturer: string;
  status: string;
  description: string;
  unitOfMeasure: string;
}

function unwrap(value: unknown): unknown {
  if (value && typeof value === "object" && "data" in value) {
    return unwrap((value as { data?: unknown }).data);
  }
  return value;
}

function text(record: AnyRecord, keys: string[], fallback = "") {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return fallback;
}

function normalizeProduct(value: unknown, id: string): ProductDetail | null {
  const unwrapped = unwrap(value);
  if (!unwrapped || typeof unwrapped !== "object") return null;
  const record = unwrapped as AnyRecord;
  const attributes = record.attributes && typeof record.attributes === "object" ? (record.attributes as AnyRecord) : record;
  return {
    id: text(record, ["id", "itemId", "code", "sku"], id),
    name: text(attributes, ["name", "displayName", "title", "label"], "Product"),
    code: text(attributes, ["code", "sku", "itemCode", "productCode"], "n/a"),
    category: text(attributes, ["category", "kind", "type", "classification"], "Unclassified"),
    manufacturer: text(attributes, ["manufacturer", "supplier", "brand"], "Not stated"),
    status: text(attributes, ["status", "lifecycleStatus"], "ACTIVE"),
    description: text(attributes, ["description", "summary"], ""),
    unitOfMeasure: text(attributes, ["unitOfMeasure", "uom", "unit"], ""),
  };
}

export default function ProductDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useProductRegistryItem(id);

  const product = normalizeProduct(data, id);

  return (
    <AppLayout>
      <PageShell title="Product Details" subtitle="Product information and specifications">
        <div className="mb-4">
          <Link
            href="/registry/products"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Products
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading product...</span>
          </div>
        ) : error || !product ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load product</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start gap-4 mb-4">
                <div className="w-12 h-12 rounded-lg bg-amber-100 flex items-center justify-center shrink-0">
                  <Package className="w-6 h-6 text-amber-600" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-gray-900">{product.name}</h3>
                  <p className="text-sm text-gray-500">{product.code}</p>
                </div>
              </div>
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-gray-500">Category</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">{product.category}</dd>
                </div>
                <div>
                  <dt className="text-gray-500">Manufacturer</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">{product.manufacturer}</dd>
                </div>
                <div>
                  <dt className="text-gray-500">Status</dt>
                  <dd className="mt-0.5">
                    <span
                      className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                        product.status === "ACTIVE"
                          ? "bg-green-100 text-green-700"
                          : "bg-gray-100 text-gray-700"
                      }`}
                    >
                      {product.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Unit of Measure</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {product.unitOfMeasure || "\u2014"}
                  </dd>
                </div>
              </dl>
            </div>

            {product.description && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-2">Description</h3>
                <p className="text-sm text-gray-600">{product.description}</p>
              </div>
            )}
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-xs text-amber-900">
              Loaded via canonical <code>/internal/v1/product-registry/items/{id}</code>.
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
