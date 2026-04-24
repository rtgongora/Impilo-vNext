"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { mushexApi } from "@/lib/mushexApi";

export default function DashboardPage() {
  const [fraudCount, setFraudCount] = useState<number | null>(null);
  const [reviewCount, setReviewCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadCounts() {
      try {
        setLoading(true);
        setError(null);

        const [fraudData, reviewData] = await Promise.all([
          mushexApi.listFraudFlags({ page: 0, size: 1 }),
          mushexApi.listReviews({ page: 0, size: 1, status: "PENDING" }),
        ]);

        setFraudCount(fraudData.totalElements);
        setReviewCount(reviewData.totalElements);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load dashboard data");
      } finally {
        setLoading(false);
      }
    }

    loadCounts();
  }, []);

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">Ops Dashboard</h1>
        <p className="text-sm text-neutral-500 mt-1">
          MUSHEX payment operations overview: fraud flags, pending reviews, adapter status.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <div key={i} className="card p-6 animate-pulse">
              <div className="h-4 bg-neutral-200 rounded w-1/2 mb-3" />
              <div className="h-8 bg-neutral-200 rounded w-1/3" />
            </div>
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-6 mb-8">
          <Link href="/fraud" className="card p-6 hover:border-red-300 transition-colors">
            <h3 className="text-sm font-semibold text-neutral-600 mb-1">Fraud Flags</h3>
            <p className="text-3xl font-bold text-red-700">
              {fraudCount ?? "---"}
            </p>
            <p className="text-xs text-neutral-500 mt-1">Active fraud flags</p>
          </Link>

          <Link href="/reviews" className="card p-6 hover:border-amber-300 transition-colors">
            <h3 className="text-sm font-semibold text-neutral-600 mb-1">Pending Reviews</h3>
            <p className="text-3xl font-bold text-amber-700">
              {reviewCount ?? "---"}
            </p>
            <p className="text-xs text-neutral-500 mt-1">Awaiting operator action</p>
          </Link>

          <Link href="/adapters" className="card p-6 hover:border-blue-300 transition-colors">
            <h3 className="text-sm font-semibold text-neutral-600 mb-1">Adapters</h3>
            <p className="text-3xl font-bold text-blue-700">---</p>
            <p className="text-xs text-neutral-500 mt-1">Payment adapter status</p>
          </Link>
        </div>
      )}

      <div className="grid grid-cols-2 gap-6">
        <Link href="/claims" className="card p-5 hover:border-purple-300 transition-colors">
          <h3 className="text-sm font-semibold text-neutral-700">Claims Management</h3>
          <p className="text-xs text-neutral-500 mt-1">
            View, submit, and dispute insurance claims
          </p>
        </Link>
        <Link href="/fraud" className="card p-5 hover:border-red-300 transition-colors">
          <h3 className="text-sm font-semibold text-neutral-700">Fraud Investigation</h3>
          <p className="text-xs text-neutral-500 mt-1">
            Review flagged transactions and evidence
          </p>
        </Link>
      </div>
    </div>
  );
}
