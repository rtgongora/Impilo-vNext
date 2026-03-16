"use client";

/**
 * Vendor Directory — Browse registered vendors.
 * Route: /marketplace/vendors
 */

import { Loader2, AlertTriangle, Store, Star } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface Vendor {
  id: string;
  type: "vendor";
  attributes: {
    name: string;
    contactEmail: string;
    contactPhone: string;
    productCount: number;
    status: string;
    rating: number | null;
    [key: string]: unknown;
  };
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-gray-100 text-gray-700",
  SUSPENDED: "bg-red-100 text-red-700",
};

export default function VendorsPage() {
  const { data, isLoading, error } = useQuery<ApiResponse<Vendor[]>>({
    queryKey: ["marketplace-vendors"],
    queryFn: () => apiClient.get<ApiResponse<Vendor[]>>("/internal/v1/marketplace/vendors"),
  });

  const vendors = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Vendor Directory" subtitle="Registered marketplace vendors">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading vendors...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load vendors</p>
          </div>
        ) : vendors.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Store className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No vendors registered</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Vendor</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Contact</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Products</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Rating</th>
                </tr>
              </thead>
              <tbody>
                {vendors.map((vendor) => {
                  const statusStyle = STATUS_STYLES[vendor.attributes.status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={vendor.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {vendor.attributes.name}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <div>{vendor.attributes.contactEmail}</div>
                        <div className="text-xs text-gray-400">{vendor.attributes.contactPhone}</div>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{vendor.attributes.productCount}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {vendor.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {vendor.attributes.rating != null ? (
                          <span className="inline-flex items-center gap-1">
                            <Star className="w-3.5 h-3.5 text-amber-400 fill-amber-400" />
                            {vendor.attributes.rating.toFixed(1)}
                          </span>
                        ) : (
                          "\u2014"
                        )}
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
