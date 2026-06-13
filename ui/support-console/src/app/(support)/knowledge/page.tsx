"use client";

import React, { useCallback, useEffect, useState } from "react";
import { listArticles, createArticle, updateArticle } from "@/lib/supportApi";
import { useSupportSession } from "@/stores/sessionStore";
import type { KnowledgeArticle, ArticleStatus } from "@/types/support";

const STATUS_OPTIONS: ArticleStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

export default function KnowledgePage() {
  const session = useSupportSession((s) => s.session);
  const [articles, setArticles] = useState<KnowledgeArticle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [searchTerm, setSearchTerm] = useState("");

  // Detail/edit state
  const [selectedArticle, setSelectedArticle] = useState<KnowledgeArticle | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  // Create form
  const [newTitle, setNewTitle] = useState("");
  const [newBody, setNewBody] = useState("");
  const [newCategory, setNewCategory] = useState("GENERAL");
  const [newTags, setNewTags] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const actorId = session?.actorId ?? "support-agent";

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listArticles(undefined, statusFilter || undefined);
      setArticles(data.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load articles");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => { load(); }, [load]);

  const filtered = articles.filter((a) =>
    !searchTerm || a.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
    a.body.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim() || !newBody.trim()) return;
    setSubmitting(true);
    try {
      const article = await createArticle({
        title: newTitle.trim(),
        body: newBody.trim(),
        authorRef: actorId,
        category: newCategory,
        tags: newTags.trim() || undefined,
      });
      setArticles((prev) => [article, ...prev]);
      setShowCreate(false);
      setNewTitle(""); setNewBody(""); setNewCategory("GENERAL"); setNewTags("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create article");
    } finally {
      setSubmitting(false);
    }
  };

  const handlePublish = async (articleId: string) => {
    try {
      const updated = await updateArticle(articleId, { status: "PUBLISHED" });
      setArticles((prev) => prev.map((a) => a.articleId === articleId ? updated : a));
      if (selectedArticle?.articleId === articleId) setSelectedArticle(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to publish article");
    }
  };

  const handleArchive = async (articleId: string) => {
    try {
      const updated = await updateArticle(articleId, { status: "ARCHIVED" });
      setArticles((prev) => prev.map((a) => a.articleId === articleId ? updated : a));
      if (selectedArticle?.articleId === articleId) setSelectedArticle(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to archive article");
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Knowledge Base</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Articles, runbooks, and troubleshooting guides for support agents
          </p>
        </div>
        <button
          onClick={() => { setShowCreate(true); setSelectedArticle(null); }}
          className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90"
        >
          New Article
        </button>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-4">
          {error}
          <button onClick={() => setError(null)} className="ml-2 underline">Dismiss</button>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-6">
        <input
          type="text"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Search articles..."
          className="flex-1 min-w-[200px] px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-neutral-200 rounded-lg text-sm bg-card focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
        >
          <option value="">All Statuses</option>
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Article list */}
        <div className="lg:col-span-1">
          <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100">
            {loading ? (
              <div className="p-6 text-center text-sm text-neutral-500">Loading articles...</div>
            ) : filtered.length === 0 ? (
              <div className="p-6 text-center text-sm text-neutral-500">No articles found</div>
            ) : (
              <div className="divide-y divide-neutral-50">
                {filtered.map((a) => (
                  <button
                    key={a.articleId}
                    onClick={() => { setSelectedArticle(a); setShowCreate(false); }}
                    className={`w-full text-left p-4 hover:bg-neutral-50/50 transition-colors ${
                      selectedArticle?.articleId === a.articleId ? "bg-brand-primary/5" : ""
                    }`}
                  >
                    <div className="flex items-center gap-2 mb-1">
                      <span className={`inline-flex px-1.5 py-0.5 rounded-full text-xs font-medium ${
                        a.status === "PUBLISHED" ? "bg-success/10 text-success" :
                        a.status === "DRAFT" ? "bg-warning/10 text-warning" :
                        "bg-neutral-100 text-neutral-500"
                      }`}>
                        {a.status}
                      </span>
                      <span className="text-xs text-neutral-400">{a.category}</span>
                    </div>
                    <p className="text-sm font-medium text-neutral-900 line-clamp-2">{a.title}</p>
                    <p className="text-xs text-neutral-500 mt-1">
                      by {a.authorRef} &middot; {new Date(a.updatedAt).toLocaleDateString()}
                    </p>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Article detail / create form */}
        <div className="lg:col-span-2">
          {showCreate ? (
            <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
              <h2 className="text-lg font-semibold text-neutral-900 mb-4">Create Article</h2>
              <form onSubmit={handleCreate} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-neutral-700 mb-1">Title</label>
                  <input
                    type="text"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    required
                    className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-neutral-700 mb-1">Body</label>
                  <textarea
                    value={newBody}
                    onChange={(e) => setNewBody(e.target.value)}
                    required
                    rows={10}
                    className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 resize-y font-mono"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-neutral-700 mb-1">Category</label>
                    <input
                      type="text"
                      value={newCategory}
                      onChange={(e) => setNewCategory(e.target.value)}
                      className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-neutral-700 mb-1">Tags (comma-separated)</label>
                    <input
                      type="text"
                      value={newTags}
                      onChange={(e) => setNewTags(e.target.value)}
                      placeholder="e.g. ehr,login,password"
                      className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
                    />
                  </div>
                </div>
                <div className="flex gap-3">
                  <button
                    type="submit"
                    disabled={submitting || !newTitle.trim() || !newBody.trim()}
                    className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
                  >
                    {submitting ? "Creating..." : "Create Article"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowCreate(false)}
                    className="px-4 py-2 text-sm text-neutral-600"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          ) : selectedArticle ? (
            <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h2 className="text-xl font-semibold text-neutral-900">{selectedArticle.title}</h2>
                  <div className="flex items-center gap-3 mt-2 text-xs text-neutral-500">
                    <span>Category: {selectedArticle.category}</span>
                    <span>Author: {selectedArticle.authorRef}</span>
                    <span>Updated: {new Date(selectedArticle.updatedAt).toLocaleString()}</span>
                    {selectedArticle.publishedAt && (
                      <span>Published: {new Date(selectedArticle.publishedAt).toLocaleString()}</span>
                    )}
                  </div>
                  {selectedArticle.tags && (
                    <div className="flex gap-1.5 mt-2">
                      {selectedArticle.tags.split(",").map((tag) => (
                        <span key={tag} className="px-2 py-0.5 bg-neutral-100 rounded-full text-xs text-neutral-600">
                          {tag.trim()}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex gap-2">
                  {selectedArticle.status === "DRAFT" && (
                    <button
                      onClick={() => handlePublish(selectedArticle.articleId)}
                      className="px-3 py-1.5 bg-success/10 text-success rounded-lg text-xs font-medium hover:bg-success/20"
                    >
                      Publish
                    </button>
                  )}
                  {selectedArticle.status === "PUBLISHED" && (
                    <button
                      onClick={() => handleArchive(selectedArticle.articleId)}
                      className="px-3 py-1.5 bg-neutral-100 text-neutral-600 rounded-lg text-xs font-medium hover:bg-neutral-200"
                    >
                      Archive
                    </button>
                  )}
                </div>
              </div>
              <div className="prose prose-sm max-w-none text-neutral-700 whitespace-pre-wrap border-t border-neutral-100 pt-4">
                {selectedArticle.body}
              </div>
            </div>
          ) : (
            <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-12 text-center">
              <p className="text-sm text-neutral-500">Select an article to view its contents</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
