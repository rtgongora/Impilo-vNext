"use client";

import { useState, useEffect, useCallback } from "react";
import { costaApi, type Ruleset, type RulesetStatus, type PagedResponse } from "@/lib/costaApi";

const STATUS_BADGE: Record<RulesetStatus, string> = {
  DRAFT: "badge-draft",
  PUBLISHED: "badge-published",
  ARCHIVED: "badge-archived",
};

export default function RulesetsPage() {
  const [rulesets, setRulesets] = useState<Ruleset[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const loadRulesets = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data: PagedResponse<Ruleset> = await costaApi.listRulesets({ page: 0, size: 100 });
      setRulesets(data.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load rulesets");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRulesets();
  }, [loadRulesets]);

  const handlePublish = async (id: number) => {
    setActionLoading(true);
    setError(null);
    try {
      await costaApi.publishRuleset(id);
      setSuccessMessage("Ruleset published successfully.");
      await loadRulesets();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to publish ruleset");
    } finally {
      setActionLoading(false);
    }
  };

  const formatRulesPreview = (rulesJson: string): string => {
    try {
      const rules = JSON.parse(rulesJson);
      return `${rules.length} rule${rules.length !== 1 ? "s" : ""}`;
    } catch {
      return "Invalid JSON";
    }
  };

  const formatRulesDetail = (rulesJson: string): string => {
    try {
      return JSON.stringify(JSON.parse(rulesJson), null, 2);
    } catch {
      return rulesJson;
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-16 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Charging Rulesets</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Manage JSON-based charging rules. Publish to apply to new billing computations.
        </p>
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

      <div className="space-y-3">
        {rulesets.map((rs) => (
          <div key={rs.id} className="card p-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className={STATUS_BADGE[rs.status]}>{rs.status}</span>
                <h3 className="text-sm font-semibold text-neutral-900">{rs.name}</h3>
                <span className="text-xs text-neutral-500">v{rs.version}</span>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-xs text-neutral-400">
                  Effective: {rs.effectiveFrom}
                  {rs.effectiveTo ? ` → ${rs.effectiveTo}` : ""}
                </span>
                <button
                  onClick={() => setExpandedId(expandedId === rs.id ? null : rs.id)}
                  className="text-xs text-amber-600 hover:text-warning-foreground font-medium"
                >
                  {expandedId === rs.id ? "Collapse" : "View Rules"}
                </button>
                {rs.status === "DRAFT" && (
                  <button
                    onClick={() => handlePublish(rs.id)}
                    disabled={actionLoading}
                    className="btn-primary text-xs"
                  >
                    {actionLoading ? "Publishing..." : "Publish"}
                  </button>
                )}
              </div>
            </div>

            <div className="mt-2 text-xs text-neutral-500">
              {formatRulesPreview(rs.rules)} &middot; Created {new Date(rs.createdAt).toLocaleDateString()}
            </div>

            {expandedId === rs.id && (
              <div className="mt-3 bg-neutral-50 rounded-lg p-4 border border-neutral-200">
                <h4 className="text-xs font-semibold text-neutral-600 mb-2 uppercase tracking-wider">
                  Rule Definitions (JSON)
                </h4>
                <pre className="text-xs text-neutral-800 font-mono whitespace-pre-wrap overflow-x-auto max-h-80 overflow-y-auto">
                  {formatRulesDetail(rs.rules)}
                </pre>
              </div>
            )}
          </div>
        ))}

        {rulesets.length === 0 && (
          <div className="card p-12 text-center text-neutral-500 text-sm">
            No charging rulesets found. Create rulesets via the API to get started.
          </div>
        )}
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {rulesets.length} ruleset{rulesets.length !== 1 ? "s" : ""} total
      </div>
    </div>
  );
}
