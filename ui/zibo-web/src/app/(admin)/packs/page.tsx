"use client";

import { useState, useEffect, useCallback } from "react";
import {
  ziboApi,
  type Pack,
  type PackStatus,
  type PackCreateRequest,
  type PackArtifact,
} from "@/lib/ziboApi";

const STATUS_COLORS: Record<PackStatus, string> = {
  DRAFT: "badge-status-draft",
  ACTIVE: "badge-status-active",
  DEPRECATED: "badge-status-deprecated",
  RETIRED: "badge-status-retired",
};

export default function PacksPage() {
  const [packs, setPacks] = useState<Pack[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Create form
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createData, setCreateData] = useState<PackCreateRequest>({
    name: "",
    version: "1.0.0",
    description: "",
  });

  // Pack detail
  const [selectedPackId, setSelectedPackId] = useState<string | null>(null);
  const [packArtifacts, setPackArtifacts] = useState<PackArtifact[]>([]);
  const [addArtifactId, setAddArtifactId] = useState("");

  const loadPacks = useCallback(async () => {
    try {
      setLoading(true);
      const data = await ziboApi.listPacks();
      setPacks(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load packs");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPackArtifacts = useCallback(async (packId: string) => {
    try {
      const data = await ziboApi.getPackArtifacts(packId);
      setPackArtifacts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load pack artifacts");
    }
  }, []);

  useEffect(() => {
    loadPacks();
  }, [loadPacks]);

  useEffect(() => {
    if (selectedPackId) {
      loadPackArtifacts(selectedPackId);
    } else {
      setPackArtifacts([]);
    }
  }, [selectedPackId, loadPackArtifacts]);

  const handleCreate = async () => {
    if (!createData.name.trim()) {
      setError("Pack name is required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.createPack(createData);
      setSuccessMessage("Pack created successfully.");
      setShowCreateForm(false);
      setCreateData({ name: "", version: "1.0.0", description: "" });
      await loadPacks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create pack");
    } finally {
      setActionLoading(false);
    }
  };

  const handlePublish = async (id: string) => {
    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.publishPack(id);
      setSuccessMessage("Pack published successfully.");
      await loadPacks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to publish pack");
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeprecate = async (id: string) => {
    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.deprecatePack(id);
      setSuccessMessage("Pack deprecated.");
      await loadPacks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deprecate pack");
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddArtifact = async () => {
    if (!selectedPackId || !addArtifactId.trim()) {
      setError("Artifact ID is required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.addArtifactToPack(selectedPackId, addArtifactId);
      setSuccessMessage("Artifact added to pack.");
      setAddArtifactId("");
      await loadPackArtifacts(selectedPackId);
      await loadPacks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add artifact to pack");
    } finally {
      setActionLoading(false);
    }
  };

  const handleRemoveArtifact = async (artifactId: string) => {
    if (!selectedPackId) return;

    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.removeArtifactFromPack(selectedPackId, artifactId);
      setSuccessMessage("Artifact removed from pack.");
      await loadPackArtifacts(selectedPackId);
      await loadPacks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to remove artifact");
    } finally {
      setActionLoading(false);
    }
  };

  const handleExport = async (packId: string) => {
    setActionLoading(true);
    setError(null);

    try {
      const bundle = await ziboApi.exportPackBundle(packId);
      const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `pack-${packId}-bundle.json`;
      a.click();
      URL.revokeObjectURL(url);
      setSuccessMessage("Pack exported successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to export pack");
    } finally {
      setActionLoading(false);
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

  const selectedPack = packs.find((p) => p.id === selectedPackId);

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Packs</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Group artifacts into distributable terminology packs.
          </p>
        </div>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="btn-primary"
        >
          Create Pack
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-success-soft border border-success/25 text-sm text-primary-hover">
          {successMessage}
        </div>
      )}

      {/* Create form */}
      {showCreateForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Create Pack</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="packName" className="block text-sm font-medium text-neutral-700 mb-1">
                Name
              </label>
              <input
                id="packName"
                type="text"
                value={createData.name}
                onChange={(e) => setCreateData({ ...createData, name: e.target.value })}
                placeholder="e.g., Zimbabwe Primary Care Pack"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="packVersion" className="block text-sm font-medium text-neutral-700 mb-1">
                Version
              </label>
              <input
                id="packVersion"
                type="text"
                value={createData.version}
                onChange={(e) => setCreateData({ ...createData, version: e.target.value })}
                placeholder="1.0.0"
                className="input-field"
              />
            </div>
          </div>

          <div>
            <label htmlFor="packDesc" className="block text-sm font-medium text-neutral-700 mb-1">
              Description
            </label>
            <textarea
              id="packDesc"
              value={createData.description || ""}
              onChange={(e) => setCreateData({ ...createData, description: e.target.value })}
              placeholder="Describe this terminology pack..."
              rows={3}
              className="input-field"
            />
          </div>

          <div className="flex gap-2">
            <button onClick={handleCreate} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Creating..." : "Create"}
            </button>
            <button onClick={() => setShowCreateForm(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Packs table */}
      <div className="card overflow-hidden mb-6">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Version</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Artifacts</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Updated</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {packs.map((pack) => (
              <tr
                key={pack.id}
                className={`hover:bg-neutral-50 transition-colors cursor-pointer ${
                  selectedPackId === pack.id ? "bg-teal-50" : ""
                }`}
                onClick={() => setSelectedPackId(selectedPackId === pack.id ? null : pack.id)}
              >
                <td className="px-4 py-3 text-neutral-900 font-medium">
                  {pack.name}
                </td>
                <td className="px-4 py-3 text-neutral-600 font-mono text-xs">
                  {pack.version}
                </td>
                <td className="px-4 py-3">
                  <span className={STATUS_COLORS[pack.status]}>
                    {pack.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-600">
                  {pack.artifactCount}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(pack.updatedAt).toLocaleDateString()}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2" onClick={(e) => e.stopPropagation()}>
                    {pack.status === "DRAFT" && (
                      <button
                        onClick={() => handlePublish(pack.id)}
                        disabled={actionLoading}
                        className="text-xs text-primary hover:text-primary-hover font-medium"
                      >
                        Publish
                      </button>
                    )}
                    {pack.status === "ACTIVE" && (
                      <button
                        onClick={() => handleDeprecate(pack.id)}
                        disabled={actionLoading}
                        className="text-xs text-yellow-600 hover:text-yellow-800 font-medium"
                      >
                        Deprecate
                      </button>
                    )}
                    <button
                      onClick={() => handleExport(pack.id)}
                      disabled={actionLoading}
                      className="text-xs text-primary hover:text-blue-800 font-medium"
                    >
                      Export
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {packs.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-neutral-500">
                  No packs found. Create one to start grouping artifacts.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Pack detail */}
      {selectedPack && (
        <div className="card p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-neutral-900">
              {selectedPack.name} &mdash; Artifacts
            </h2>
            <span className={STATUS_COLORS[selectedPack.status]}>
              {selectedPack.status}
            </span>
          </div>

          {selectedPack.description && (
            <p className="text-sm text-neutral-600">{selectedPack.description}</p>
          )}

          {/* Add artifact */}
          {selectedPack.status === "DRAFT" && (
            <div className="flex gap-2">
              <input
                type="text"
                value={addArtifactId}
                onChange={(e) => setAddArtifactId(e.target.value)}
                placeholder="Artifact ID to add..."
                className="input-field flex-1"
              />
              <button onClick={handleAddArtifact} disabled={actionLoading} className="btn-primary">
                {actionLoading ? "Adding..." : "Add"}
              </button>
            </div>
          )}

          {/* Pack artifacts table */}
          <div className="border border-neutral-200 rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 bg-neutral-50">
                  <th className="text-left px-4 py-2 font-medium text-neutral-600">Type</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600">Canonical URL</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600">Name</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600">Version</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600">Status</th>
                  {selectedPack.status === "DRAFT" && (
                    <th className="text-left px-4 py-2 font-medium text-neutral-600">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {packArtifacts.map((artifact) => (
                  <tr key={artifact.artifactId} className="hover:bg-neutral-50">
                    <td className="px-4 py-2">
                      <span className="badge bg-teal-100 text-teal-800">{artifact.fhirType}</span>
                    </td>
                    <td className="px-4 py-2 font-mono text-xs text-neutral-900 max-w-xs truncate">
                      {artifact.canonicalUrl}
                    </td>
                    <td className="px-4 py-2 text-neutral-900">{artifact.name}</td>
                    <td className="px-4 py-2 font-mono text-xs text-neutral-600">{artifact.version}</td>
                    <td className="px-4 py-2">
                      <span className={STATUS_COLORS[artifact.status]}>{artifact.status}</span>
                    </td>
                    {selectedPack.status === "DRAFT" && (
                      <td className="px-4 py-2">
                        <button
                          onClick={() => handleRemoveArtifact(artifact.artifactId)}
                          disabled={actionLoading}
                          className="text-xs text-red-600 hover:text-red-800 font-medium"
                        >
                          Remove
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
                {packArtifacts.length === 0 && (
                  <tr>
                    <td
                      colSpan={selectedPack.status === "DRAFT" ? 6 : 5}
                      className="px-4 py-8 text-center text-neutral-500"
                    >
                      No artifacts in this pack yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
