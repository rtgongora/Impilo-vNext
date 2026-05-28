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
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface ProductDetail {
  id: string;
  type: "product";
  attributes: {
    name: string;
    code: string;
    category: string;
    manufacturer: string;
    status: string;
    description: string;
    unitOfMeasure: string;
    price: number;
    [key: string]: unknown;
  };
}

export default function ProductDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<ProductDetail>>({
    queryKey: ["registry-products", id],
    queryFn: () => apiClient.get<ApiResponse<ProductDetail>>(`/internal/v1/product-registry/items/${encodeURIComponent(id)}`),
    enabled: !!id,
  });

  const product = data?.data;

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
                  <h3 className="text-lg font-medium text-gray-900">{product.attributes.name}</h3>
                  <p className="text-sm text-gray-500">{product.attributes.code}</p>
                </div>
              </div>
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-gray-500">Category</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">{product.attributes.category}</dd>
                </div>
                <div>
                  <dt className="text-gray-500">Manufacturer</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">{product.attributes.manufacturer}</dd>
                </div>
                <div>
                  <dt className="text-gray-500">Status</dt>
                  <dd className="mt-0.5">
                    <span
                      className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                        product.attributes.status === "ACTIVE"
                          ? "bg-green-100 text-green-700"
                          : "bg-gray-100 text-gray-700"
                      }`}
                    >
                      {product.attributes.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Unit of Measure</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {product.attributes.unitOfMeasure || "\u2014"}
                  </dd>
                </div>
              </dl>
            </div>

            {product.attributes.description && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-2">Description</h3>
                <p className="text-sm text-gray-600">{product.attributes.description}</p>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
