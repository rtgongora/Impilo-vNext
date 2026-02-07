"use client";

import { useState, useEffect, useCallback } from "react";
import {
  ziboApi,
  type MapResult,
  type MapSource,
  type MapRequest,
} from "@/lib/ziboApi";

const EQUIVALENCE_COLORS: Record<string, string> = {
  equivalent: "bg-emerald-100 text-emerald-800",
  wider: "bg-blue-100 text-blue-800",
  narrower: "bg-yellow-100 text-yellow-800",
  inexact: "bg-orange-100 text-orange-800",
  unmatched: "bg-red-100 text-red-800",
  disjoint: "bg-neutral-100 text-neutral-600",
};

export default function MappingsPage() {
  const [mapSources, setMapSources] = useState<MapSource[]>([]);
  const [mapResults, setMapResults] = useState<MapResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchLoading, setSearchLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Lookup form
  const [sourceSystem, setSourceSystem] = useState("");
  const [sourceCode, setSourceCode] = useState("");
  const [targetSystem, setTargetSystem] = useState("");

  const loadMapSources = useCallback(async () => {
    try {
      setLoading(true);
      const data = await ziboApi.getMapSources();
      setMapSources(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load mapping sources");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMapSources();
  }, [loadMapSources]);

  const handleLookup = async () => {
    if (!sourceSystem.trim() || !sourceCode.trim()) {
      setError("Source system and code are required.");
      return;
    }

    setSearchLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const request: MapRequest = {
        sourceSystem,
        sourceCode,
      };
      if (targetSystem.trim()) {
        request.targetSystem = targetSystem;
      }

      const results = await ziboApi.mapCode(request);
      setMapResults(results);

      if (results.length === 0) {
        setSuccessMessage("No mappings found for the given code.");
      } else {
        setSuccessMessage(`Found ${results.length} mapping${results.length !== 1 ? "s" : ""}.`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Mapping lookup failed");
    } finally {
      setSearchLoading(false);
    }
  };

  const handleRebuildIndex = async () => {
    setSearchLoading(true);
    setError(null);

    try {
      await ziboApi.rebuildMapIndex();
      setSuccessMessage("Map index rebuild initiated.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to rebuild map index");
    } finally {
      setSearchLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3].map((i) => (
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
          <h1 className="text-2xl font-semibold text-neutral-900">Mappings</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Look up code mappings across terminology systems using ConceptMaps.
          </p>
        </div>
        <button
          onClick={handleRebuildIndex}
          disabled={searchLoading}
          className="btn-secondary"
        >
          Rebuild Index
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {successMessage}
        </div>
      )}

      {/* Lookup form */}
      <div className="card p-6 mb-6 space-y-4">
        <h2 className="text-lg font-semibold text-neutral-900">Code Lookup</h2>

        <div className="grid grid-cols-3 gap-4">
          <div>
            <label htmlFor="sourceSystem" className="block text-sm font-medium text-neutral-700 mb-1">
              Source System
            </label>
            <input
              id="sourceSystem"
              type="text"
              value={sourceSystem}
              onChange={(e) => setSourceSystem(e.target.value)}
              placeholder="http://snomed.info/sct"
              className="input-field"
            />
          </div>
          <div>
            <label htmlFor="sourceCode" className="block text-sm font-medium text-neutral-700 mb-1">
              Source Code
            </label>
            <input
              id="sourceCode"
              type="text"
              value={sourceCode}
              onChange={(e) => setSourceCode(e.target.value)}
              placeholder="e.g., 386661006"
              className="input-field"
            />
          </div>
          <div>
            <label htmlFor="targetSystem" className="block text-sm font-medium text-neutral-700 mb-1">
              Target System (optional)
            </label>
            <input
              id="targetSystem"
              type="text"
              value={targetSystem}
              onChange={(e) => setTargetSystem(e.target.value)}
              placeholder="http://hl7.org/fhir/sid/icd-10"
              className="input-field"
            />
          </div>
        </div>

        <button onClick={handleLookup} disabled={searchLoading} className="btn-primary">
          {searchLoading ? "Looking up..." : "Look Up"}
        </button>
      </div>

      {/* Map results */}
      {mapResults.length > 0 && (
        <div className="card overflow-hidden mb-6">
          <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
            <h3 className="text-sm font-semibold text-neutral-700">
              Mapping Results ({mapResults.length})
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200">
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Source System</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Source Code</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Target System</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Target Code</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Target Display</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Equivalence</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">ConceptMap</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {mapResults.map((result, idx) => (
                <tr key={idx} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-neutral-600 max-w-[200px] truncate">
                    {result.sourceSystem}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs font-bold text-neutral-900">
                    {result.sourceCode}
                    {result.sourceDisplay && (
                      <span className="block font-normal text-neutral-500 mt-0.5">
                        {result.sourceDisplay}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-neutral-600 max-w-[200px] truncate">
                    {result.targetSystem}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs font-bold text-teal-700">
                    {result.targetCode}
                  </td>
                  <td className="px-4 py-3 text-neutral-900">
                    {result.targetDisplay || "--"}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`badge ${EQUIVALENCE_COLORS[result.equivalence] || "bg-neutral-100 text-neutral-600"}`}>
                      {result.equivalence}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-neutral-500 max-w-[180px] truncate">
                    {result.conceptMapUrl}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Available mapping sources */}
      <div className="card overflow-hidden">
        <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
          <h3 className="text-sm font-semibold text-neutral-700">
            Available Mapping Sources ({mapSources.length})
          </h3>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">System</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Display</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">ConceptMaps</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {mapSources.map((source, idx) => (
              <tr key={idx} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 font-mono text-xs text-neutral-900">
                  {source.system}
                </td>
                <td className="px-4 py-3 text-neutral-700">
                  {source.display}
                </td>
                <td className="px-4 py-3 text-neutral-600">
                  {source.conceptMapCount}
                </td>
              </tr>
            ))}
            {mapSources.length === 0 && (
              <tr>
                <td colSpan={3} className="px-4 py-12 text-center text-neutral-500">
                  No mapping sources available. Import ConceptMap artifacts first.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
