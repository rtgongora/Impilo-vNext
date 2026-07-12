"use client";

/**
 * Public facility-directory search (no sign-in). Calls the anonymous gateway lane
 * GET /internal/v1/public/gateway/facilities/search and renders the governed public
 * disclosure only: name, type, level, district, province, status. Booking and
 * personalised features remain behind sign-in.
 */

import { useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";

const PROVINCES = [
  "Bulawayo",
  "Harare",
  "Manicaland",
  "Mashonaland Central",
  "Mashonaland East",
  "Mashonaland West",
  "Masvingo",
  "Matabeleland North",
  "Matabeleland South",
  "Midlands",
];

interface PublicFacilitySummary {
  id: number;
  name: string;
  type?: string | null;
  status?: string | null;
  district?: string | null;
  province?: string | null;
  level?: string | null;
}

interface PublicFacilityPage {
  items: PublicFacilitySummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export function PublicFacilitySearch() {
  const [query, setQuery] = useState("");
  const [province, setProvince] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PublicFacilityPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function search(nextPage: number) {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams();
      if (query.trim()) params.set("query", query.trim());
      if (province) params.set("province", province);
      params.set("page", String(nextPage));
      params.set("size", "10");
      const res = await apiClient.get<PublicFacilityPage>(
        `/internal/v1/public/gateway/facilities/search?${params.toString()}`,
      );
      setResult(res);
      setPage(nextPage);
    } catch {
      setError(
        "The facility search is temporarily unavailable. Please try again shortly — the reference list below is still accurate.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6">
      <h2 className="font-semibold text-slate-900">Search the national facility directory</h2>
      <p className="mt-1 text-sm text-slate-600">
        Find clinics, hospitals and health facilities by name, district or province. No sign-in
        needed.
      </p>

      <form
        className="mt-4 flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void search(0);
        }}
      >
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Facility name or district…"
          aria-label="Facility name or district"
          className="min-w-56 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
        />
        <select
          value={province}
          onChange={(e) => setProvince(e.target.value)}
          aria-label="Province"
          className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
        >
          <option value="">All provinces</option>
          {PROVINCES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
        >
          {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : "Search"}
        </button>
      </form>

      {error && <p className="mt-3 text-sm text-red-700">{error}</p>}

      {result && (
        <div className="mt-4">
          <p className="text-xs text-slate-500">
            {result.totalElements === 0
              ? "No facilities matched. Try a shorter name or a different province."
              : `${result.totalElements} facilit${result.totalElements === 1 ? "y" : "ies"} found`}
          </p>
          <ul className="mt-2 divide-y divide-slate-100">
            {result.items.map((f) => (
              <li key={f.id} className="flex flex-wrap items-baseline justify-between gap-2 py-3">
                <div>
                  <p className="font-medium text-slate-900">{f.name}</p>
                  <p className="text-sm text-slate-500">
                    {[f.type, f.level, f.district, f.province].filter(Boolean).join(" · ")}
                  </p>
                </div>
                {f.status && (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                    {f.status}
                  </span>
                )}
              </li>
            ))}
          </ul>
          {(page > 0 || result.hasNext) && (
            <div className="mt-3 flex items-center gap-2">
              <button
                type="button"
                onClick={() => void search(page - 1)}
                disabled={loading || page === 0}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => void search(page + 1)}
                disabled={loading || !result.hasNext}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
          <p className="mt-3 text-xs text-slate-500">
            To book an appointment or save a facility,{" "}
            <Link href="/auth/login" className="font-medium text-emerald-700 hover:text-emerald-800">
              sign in
            </Link>{" "}
            or{" "}
            <Link href="/auth/register" className="font-medium text-emerald-700 hover:text-emerald-800">
              create an account
            </Link>
            .
          </p>
        </div>
      )}
    </section>
  );
}
