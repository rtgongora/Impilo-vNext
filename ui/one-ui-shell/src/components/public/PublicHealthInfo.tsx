"use client";

/**
 * Public health-information browser (no sign-in) — gateway pillar 3.
 * Calls the anonymous gateway lane GET /internal/v1/public/gateway/guidance/education**
 * and renders the governed public disclosure only: topic categories, article summaries,
 * and the plain-language article body. Personalised education, reminders and saved
 * content remain behind sign-in. No PII on this surface.
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { EMERGENCY_CONTACTS } from "@/config/emergency";

const CATEGORY_LABELS: Record<string, string> = {
  "when-to-seek-care": "When to seek care",
  "maternal-child-health": "Maternal & child health",
  vaccination: "Vaccination",
  "chronic-conditions": "Chronic conditions",
  "mental-health": "Mental health & wellbeing",
  "medicines-safety": "Medicines safety",
  "prevention-nutrition": "Prevention & nutrition",
  "outbreak-notices": "Outbreak notices",
};

function categoryLabel(slug: string): string {
  if (CATEGORY_LABELS[slug]) return CATEGORY_LABELS[slug];
  const words = slug.replace(/-/g, " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

interface PublicEducationCategory {
  category: string;
  count: number;
}

interface PublicEducationTopic {
  id: string;
  title: string;
  summary: string;
  category: string;
  domain?: string | null;
}

interface PublicEducationPage {
  data: PublicEducationTopic[];
  meta: { page: number; size: number; totalElements: number; totalPages: number };
}

interface PublicEducationArticle {
  id: string;
  title: string;
  summary: string;
  body: string;
  category: string;
  domain?: string | null;
  updatedAt?: string | null;
}

const UNAVAILABLE_MESSAGE =
  "Health information is temporarily unavailable. Please try again shortly — emergency numbers and guidance remain available on the Emergency page.";

export function PublicHealthInfo() {
  const [categories, setCategories] = useState<PublicEducationCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState("");
  const [query, setQuery] = useState("");
  const [activeQuery, setActiveQuery] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PublicEducationPage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState("");

  const [article, setArticle] = useState<PublicEducationArticle | null>(null);
  const [articleLoading, setArticleLoading] = useState(false);
  const [articleError, setArticleError] = useState("");

  const loadList = useCallback(async (category: string, nextPage: number, q = "") => {
    setListLoading(true);
    setListError("");
    try {
      const params = new URLSearchParams();
      // A text search spans all topics; category chips filter without a query.
      if (q.trim()) params.set("q", q.trim());
      else if (category) params.set("category", category);
      params.set("page", String(nextPage));
      params.set("size", "10");
      const res = await apiClient.get<PublicEducationPage>(
        `/internal/v1/public/gateway/guidance/education?${params.toString()}`,
      );
      setResult(res);
      setActiveCategory(q.trim() ? "" : category);
      setActiveQuery(q.trim());
      setPage(nextPage);
    } catch {
      setResult(null);
      setListError(UNAVAILABLE_MESSAGE);
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadList("", 0);
    void (async () => {
      try {
        const res = await apiClient.get<{ data: PublicEducationCategory[] }>(
          "/internal/v1/public/gateway/guidance/education/categories",
        );
        setCategories(res.data ?? []);
      } catch {
        // Topic chips are an enhancement; the list carries its own honest error state.
        setCategories([]);
      }
    })();
  }, [loadList]);

  async function openArticle(id: string) {
    setArticleLoading(true);
    setArticleError("");
    try {
      const res = await apiClient.get<{ data: PublicEducationArticle }>(
        `/internal/v1/public/gateway/guidance/education/${encodeURIComponent(id)}`,
      );
      setArticle(res.data);
      try {
        window.scrollTo({ top: 0 });
      } catch {
        // Non-browser environments (tests) do not implement scrollTo.
      }
    } catch {
      setArticleError(
        "This article could not be loaded right now. It may have been unpublished, or the service may be briefly unavailable.",
      );
    } finally {
      setArticleLoading(false);
    }
  }

  // ── Article read view ─────────────────────────────────────────────────────
  if (article) {
    return (
      <article className="rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-6">
        <button
          type="button"
          onClick={() => {
            setArticle(null);
            setArticleError("");
          }}
          className="text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          ← Back to all topics
        </button>
        <p className="mt-4 text-xs font-medium uppercase tracking-wide text-emerald-700">
          {categoryLabel(article.category)}
        </p>
        <h2 className="mt-1 text-2xl font-bold text-slate-900">{article.title}</h2>
        <p className="mt-2 text-slate-600">{article.summary}</p>
        <div className="mt-5 space-y-4 border-t border-slate-100 pt-5 text-slate-700">
          {article.body.split(/\n\n+/).map((paragraph, i) => (
            <p key={i} className="whitespace-pre-line text-[15px] leading-relaxed">
              {paragraph}
            </p>
          ))}
        </div>
        <p className="mt-6 border-t border-slate-100 pt-4 text-xs text-slate-500">
          This is general health information, not personal medical advice. For advice about your
          own health, speak to a health worker. In an emergency, call{" "}
          {EMERGENCY_CONTACTS.slice(0, 2)
            .map((c) => c.number)
            .join(" or ")}{" "}
          or go to the nearest health facility.
        </p>
      </article>
    );
  }

  // ── Category + list view ──────────────────────────────────────────────────
  return (
    <section className="rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-6">
      <h2 className="font-semibold text-slate-900">Browse health topics</h2>
      <p className="mt-1 text-sm text-slate-600">
        Trusted, plain-language health information from the Ministry of Health &amp; Child Care. No
        sign-in needed.
      </p>

      <form
        className="mt-4 flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void loadList("", 0, query);
        }}
      >
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search health topics… e.g. malaria, blood pressure"
          aria-label="Search health information"
          data-testid="health-info-search"
          className="min-w-56 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={listLoading}
          className="rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
        >
          Search
        </button>
        {activeQuery && (
          <button
            type="button"
            onClick={() => {
              setQuery("");
              void loadList("", 0);
            }}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            Clear
          </button>
        )}
      </form>

      {activeQuery && (
        <p className="mt-3 text-sm text-slate-600" data-testid="health-info-search-active">
          Showing results for “{activeQuery}”.
        </p>
      )}

      {categories.length > 0 && !activeQuery && (
        <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Health topics">
          <button
            type="button"
            aria-pressed={activeCategory === ""}
            onClick={() => void loadList("", 0)}
            className={`rounded-full border px-3 py-1.5 text-sm ${
              activeCategory === ""
                ? "border-emerald-600 bg-emerald-600 font-semibold text-white"
                : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
            }`}
          >
            All topics
          </button>
          {categories.map((c) => (
            <button
              key={c.category}
              type="button"
              aria-pressed={activeCategory === c.category}
              onClick={() => void loadList(c.category, 0)}
              className={`rounded-full border px-3 py-1.5 text-sm ${
                activeCategory === c.category
                  ? "border-emerald-600 bg-emerald-600 font-semibold text-white"
                  : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
              }`}
            >
              {categoryLabel(c.category)} ({c.count})
            </button>
          ))}
        </div>
      )}

      {listLoading && (
        <p className="mt-6 flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading health topics…
        </p>
      )}

      {!listLoading && listError && (
        <div className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-4">
          <p className="text-sm text-amber-800">{listError}</p>
          <Link
            href="/welcome/emergency"
            className="mt-2 inline-block text-sm font-medium text-amber-900 underline"
          >
            Emergency numbers and guidance →
          </Link>
        </div>
      )}

      {articleError && <p className="mt-4 text-sm text-red-700">{articleError}</p>}

      {!listLoading && !listError && result && (
        <div className="mt-4">
          <p className="text-xs text-slate-500">
            {result.meta.totalElements === 0
              ? "No articles are published under this topic yet. Choose another topic — more content is added over time."
              : `${result.meta.totalElements} article${result.meta.totalElements === 1 ? "" : "s"}`}
          </p>
          <ul className="mt-2 divide-y divide-slate-100">
            {result.data.map((topic) => (
              <li key={topic.id} className="py-3">
                <button
                  type="button"
                  onClick={() => void openArticle(topic.id)}
                  disabled={articleLoading}
                  className="w-full text-left"
                >
                  <span className="text-xs font-medium uppercase tracking-wide text-emerald-700">
                    {categoryLabel(topic.category)}
                  </span>
                  <span className="mt-0.5 block font-medium text-slate-900 hover:text-emerald-800">
                    {topic.title}
                  </span>
                  <span className="mt-0.5 block text-sm text-slate-600">{topic.summary}</span>
                </button>
              </li>
            ))}
          </ul>
          {(page > 0 || page < result.meta.totalPages - 1) && (
            <div className="mt-3 flex items-center gap-2">
              <button
                type="button"
                onClick={() => void loadList(activeCategory, page - 1, activeQuery)}
                disabled={listLoading || page === 0}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => void loadList(activeCategory, page + 1, activeQuery)}
                disabled={listLoading || page >= result.meta.totalPages - 1}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
          <p className="mt-3 text-xs text-slate-500">
            For personalised health education, reminders and saved content,{" "}
            <Link href="/auth/login" className="font-medium text-emerald-700 hover:text-emerald-800">
              sign in
            </Link>{" "}
            or{" "}
            <Link
              href="/auth/register"
              className="font-medium text-emerald-700 hover:text-emerald-800"
            >
              create an account
            </Link>
            .
          </p>
        </div>
      )}
    </section>
  );
}
