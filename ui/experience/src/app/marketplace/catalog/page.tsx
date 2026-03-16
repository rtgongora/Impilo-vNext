"use client";

/**
 * Product Catalog — Browse marketplace products.
 * Route: /marketplace/catalog
 */

import { useState } from "react";
import { Loader2, AlertTriangle, ShoppingBag, Search, Package } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface CatalogItem {
  id: string;
  type: "catalog_item";
  attributes: {
    name: string;
    description: string;
    price: number;
    currency: string;
    category: string;
    availability: string;
    imageUrl: string | null;
    [key: string]: unknown;
  };
}

const CATEGORIES = ["All", "Medical Supplies", "Equipment", "Pharmaceuticals", "PPE", "Laboratory"];

export default function CatalogPage() {
  const [searchTerm, setSearchTerm] = useState("");
  const [category, setCategory] = useState("All");

  const { data, isLoading, error } = useQuery<ApiResponse<CatalogItem[]>>({
    queryKey: ["marketplace-catalog", { search: searchTerm, category }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (searchTerm) params.set("search", searchTerm);
      if (category !== "All") params.set("category", category);
      const qs = params.toString();
      return apiClient.get<ApiResponse<CatalogItem[]>>(
        `/internal/v1/marketplace/catalog${qs ? `?${qs}` : ""}`,
      );
    },
  });

  const items = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Product Catalog" subtitle="Browse available products and supplies">
        {/* Search and Filter */}
        <div className="mb-6 flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search products..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading catalog...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load catalog</p>
          </div>
        ) : items.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <ShoppingBag className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No products found</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {items.map((item) => (
              <div
                key={item.id}
                className="bg-white rounded-lg border border-gray-200 p-4 hover:shadow-md transition-shadow"
              >
                <div className="w-full h-32 bg-gray-100 rounded-lg flex items-center justify-center mb-3">
                  <Package className="w-10 h-10 text-gray-300" />
                </div>
                <h3 className="font-medium text-gray-900 text-sm">{item.attributes.name}</h3>
                <p className="text-xs text-gray-500 mt-1 line-clamp-2">
                  {item.attributes.description || "No description available"}
                </p>
                <div className="flex items-center justify-between mt-3">
                  <span className="text-sm font-bold text-gray-900">
                    {item.attributes.currency || "USD"} {item.attributes.price.toFixed(2)}
                  </span>
                  <span
                    className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                      item.attributes.availability === "IN_STOCK"
                        ? "bg-green-100 text-green-700"
                        : item.attributes.availability === "LOW_STOCK"
                        ? "bg-amber-100 text-amber-700"
                        : "bg-red-100 text-red-700"
                    }`}
                  >
                    {item.attributes.availability?.replace(/_/g, " ") || "Unknown"}
                  </span>
                </div>
                <p className="text-xs text-gray-400 mt-1">{item.attributes.category}</p>
              </div>
            ))}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
