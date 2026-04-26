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
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface ProductResource {
  id: string;
  type: "product";
  attributes: {
    name: string;
    code: string;
    category: string;
    manufacturer: string;
    status: string;
    [key: string]: unknown;
  };
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-700",
  DISCONTINUED: "bg-red-100 text-red-700",
};

export default function ProductRegistryPage() {
  const [searchTerm, setSearchTerm] = useState("");

  const { data, isLoading, error } = useQuery<ApiResponse<ProductResource[]>>({
    queryKey: ["registry-products", { search: searchTerm }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (searchTerm) params.set("search", searchTerm);
      const qs = params.toString();
      return apiClient.get<ApiResponse<ProductResource[]>>(
        `/internal/v1/registry/products${qs ? `?${qs}` : ""}`,
      );
    },
  });

  const products = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Product Registry" subtitle="Browse registered medical products">
        <div className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search products..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
            />
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading products...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load products</p>
          </div>
        ) : products.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Package className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No products found</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Product Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Code</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Category</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Manufacturer</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody>
                {products.map((product) => {
                  const statusStyle = STATUS_STYLES[product.attributes.status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={product.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">{product.attributes.name}</td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600">{product.attributes.code}</td>
                      <td className="px-4 py-3 text-gray-600">{product.attributes.category}</td>
                      <td className="px-4 py-3 text-gray-600">{product.attributes.manufacturer}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {product.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Link
                          href={`/registry/products/${product.id}`}
                          className="text-xs text-impilo-500 hover:text-impilo-700 font-medium"
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
