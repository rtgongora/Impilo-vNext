"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { costaApi, type Tariff, type PagedResponse } from "@/lib/costaApi";

export default function TariffsPage() {
  const [tariffs, setTariffs] = useState<Tariff[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadTariffs = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data: PagedResponse<Tariff> = await costaApi.listTariffs({ page, size: 50 });
      setTariffs(data.items);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tariffs");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    loadTariffs();
  }, [loadTariffs]);

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setError(null);
      const formData = new FormData();
      formData.append("file", file);

      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:10000"}/costa/v1/tariffs/import`,
        { method: "POST", body: formData }
      );
      const envelope = await res.json();
      if (!envelope.success) {
        throw new Error(envelope.error?.message ?? "Import failed");
      }
      setSuccessMessage(`Imported ${envelope.data?.length ?? 0} tariffs successfully.`);
      await loadTariffs();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="h-10 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Tariffs</h1>
          <p className="text-sm text-neutral-500 mt-1">
            National tariff schedule management. Import CSV or review published tariffs.
          </p>
        </div>
        <div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            className="hidden"
            onChange={handleFileChange}
          />
          <button onClick={handleImportClick} className="btn-primary">
            Import CSV
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-success-soft border border-success/25 text-sm text-primary-hover">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-primary hover:text-primary-hover font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Code</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">MSIKA Code</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Description</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Price</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Currency</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Facility Cat.</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Patient Cat.</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Effective</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {tariffs.map((t) => (
              <tr key={t.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs font-medium">
                  {t.tariffCode}
                </td>
                <td className="px-4 py-3 text-neutral-600 font-mono text-xs">
                  {t.msikaCode || "—"}
                </td>
                <td className="px-4 py-3 text-neutral-900 text-xs">
                  {t.description}
                </td>
                <td className="px-4 py-3 text-right text-neutral-900 font-mono text-xs font-medium">
                  {Number(t.price).toFixed(2)}
                </td>
                <td className="px-4 py-3 text-neutral-600 text-xs">
                  {t.currency}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {t.facilityCategory || "All"}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {t.patientCategory || "All"}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {t.effectiveFrom}
                  {t.effectiveTo ? ` → ${t.effectiveTo}` : ""}
                </td>
              </tr>
            ))}
            {tariffs.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                  No tariffs found. Import a CSV to populate the tariff schedule.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-xs text-neutral-500">
            {totalElements} tariff{totalElements !== 1 ? "s" : ""} total
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
              className="btn-secondary text-xs"
            >
              Previous
            </button>
            <span className="text-xs text-neutral-600 self-center">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
              disabled={page >= totalPages - 1}
              className="btn-secondary text-xs"
            >
              Next
            </button>
          </div>
        </div>
      )}

      <div className="mt-3 text-xs text-neutral-400">
        {tariffs.length} tariff{tariffs.length !== 1 ? "s" : ""} on this page
      </div>
    </div>
  );
}
