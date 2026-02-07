"use client";

import { useState, useEffect, useCallback } from "react";
import {
  ziboApi,
  type Artifact,
  type ArtifactStatus,
  type FhirType,
  type ArtifactCreateRequest,
} from "@/lib/ziboApi";

const STATUS_COLORS: Record<ArtifactStatus, string> = {
  DRAFT: "badge-status-draft",
  ACTIVE: "badge-status-active",
  DEPRECATED: "badge-status-deprecated",
  RETIRED: "badge-status-retired",
};

const FHIR_TYPES: FhirType[] = [
  "CodeSystem",
  "ValueSet",
  "ConceptMap",
  "NamingSystem",
  "StructureDefinition",
  "SearchParameter",
  "OperationDefinition",
  "CapabilityStatement",
  "ImplementationGuide",
  "Questionnaire",
];

export default function ArtifactsPage() {
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Filters
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [filterType, setFilterType] = useState<string>("");
  const [searchQuery, setSearchQuery] = useState<string>("");

  // Import form
  const [showImportForm, setShowImportForm] = useState(false);
  const [importContent, setImportContent] = useState("");
  const [importFormat, setImportFormat] = useState<"fhir-bundle" | "csv">("fhir-bundle");

  // Create form
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createData, setCreateData] = useState<ArtifactCreateRequest>({
    fhirType: "CodeSystem",
    canonicalUrl: "",
    name: "",
    title: "",
    version: "1.0.0",
    publisher: "",
    description: "",
    content: "",
    sourceFormat: "FHIR_JSON",
  });

  const loadArtifacts = useCallback(async () => {
    try {
      setLoading(true);
      const params: Record<string, string> = {};
      if (filterStatus) params.status = filterStatus;
      if (filterType) params.fhirType = filterType;
      if (searchQuery) params.q = searchQuery;
      const data = await ziboApi.listArtifacts(params);
      setArtifacts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load artifacts");
    } finally {
      setLoading(false);
    }
  }, [filterStatus, filterType, searchQuery]);

  useEffect(() => {
    loadArtifacts();
  }, [loadArtifacts]);

  const handlePublish = async (id: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await ziboApi.publishArtifact(id);
      setSuccessMessage("Artifact published successfully.");
      await loadArtifacts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to publish artifact");
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeprecate = async (id: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await ziboApi.deprecateArtifact(id);
      setSuccessMessage("Artifact deprecated.");
      await loadArtifacts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deprecate artifact");
    } finally {
      setActionLoading(false);
    }
  };

  const handleRetire = async (id: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await ziboApi.retireArtifact(id);
      setSuccessMessage("Artifact retired.");
      await loadArtifacts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to retire artifact");
    } finally {
      setActionLoading(false);
    }
  };

  const handleImport = async () => {
    if (!importContent.trim()) {
      setError("Import content is required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      if (importFormat === "fhir-bundle") {
        const bundle = JSON.parse(importContent);
        const result = await ziboApi.importFhirBundle(bundle);
        setSuccessMessage(
          `Import complete: ${result.imported} imported, ${result.skipped} skipped, ${result.errors.length} errors.`,
        );
      } else {
        setError("CSV import requires additional fields. Use the dedicated CSV import endpoint.");
      }

      setImportContent("");
      setShowImportForm(false);
      await loadArtifacts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!createData.canonicalUrl.trim() || !createData.name.trim() || !createData.title.trim()) {
      setError("Canonical URL, name, and title are required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await ziboApi.createArtifact(createData);
      setSuccessMessage("Artifact created successfully.");
      setShowCreateForm(false);
      setCreateData({
        fhirType: "CodeSystem",
        canonicalUrl: "",
        name: "",
        title: "",
        version: "1.0.0",
        publisher: "",
        description: "",
        content: "",
        sourceFormat: "FHIR_JSON",
      });
      await loadArtifacts();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create artifact");
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
          <h1 className="text-2xl font-semibold text-neutral-900">Artifacts</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Manage terminology artifacts: CodeSystems, ValueSets, ConceptMaps, and more.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setShowImportForm(!showImportForm)}
            className="btn-secondary"
          >
            Import
          </button>
          <button
            onClick={() => setShowCreateForm(!showCreateForm)}
            className="btn-primary"
          >
            Create Artifact
          </button>
        </div>
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

      {/* Import form */}
      {showImportForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Import Artifacts</h2>

          <div>
            <label htmlFor="importFormat" className="block text-sm font-medium text-neutral-700 mb-1">
              Format
            </label>
            <select
              id="importFormat"
              value={importFormat}
              onChange={(e) => setImportFormat(e.target.value as "fhir-bundle" | "csv")}
              className="select-field"
            >
              <option value="fhir-bundle">FHIR Bundle (JSON)</option>
              <option value="csv">CSV</option>
            </select>
          </div>

          <div>
            <label htmlFor="importContent" className="block text-sm font-medium text-neutral-700 mb-1">
              Content
            </label>
            <textarea
              id="importContent"
              value={importContent}
              onChange={(e) => setImportContent(e.target.value)}
              placeholder={importFormat === "fhir-bundle" ? '{"resourceType": "Bundle", ...}' : "code,display,system,..."}
              rows={10}
              className="input-field font-mono text-xs"
            />
          </div>

          <div className="flex gap-2">
            <button onClick={handleImport} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Importing..." : "Import"}
            </button>
            <button onClick={() => setShowImportForm(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Create form */}
      {showCreateForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Create Artifact</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="createType" className="block text-sm font-medium text-neutral-700 mb-1">
                FHIR Type
              </label>
              <select
                id="createType"
                value={createData.fhirType}
                onChange={(e) => setCreateData({ ...createData, fhirType: e.target.value as FhirType })}
                className="select-field"
              >
                {FHIR_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="createFormat" className="block text-sm font-medium text-neutral-700 mb-1">
                Source Format
              </label>
              <select
                id="createFormat"
                value={createData.sourceFormat}
                onChange={(e) =>
                  setCreateData({
                    ...createData,
                    sourceFormat: e.target.value as ArtifactCreateRequest["sourceFormat"],
                  })
                }
                className="select-field"
              >
                <option value="FHIR_JSON">FHIR JSON</option>
                <option value="FHIR_XML">FHIR XML</option>
                <option value="CSV">CSV</option>
                <option value="CUSTOM">Custom</option>
              </select>
            </div>
          </div>

          <div>
            <label htmlFor="createUrl" className="block text-sm font-medium text-neutral-700 mb-1">
              Canonical URL
            </label>
            <input
              id="createUrl"
              type="text"
              value={createData.canonicalUrl}
              onChange={(e) => setCreateData({ ...createData, canonicalUrl: e.target.value })}
              placeholder="http://example.org/fhir/CodeSystem/my-system"
              className="input-field"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="createName" className="block text-sm font-medium text-neutral-700 mb-1">
                Name
              </label>
              <input
                id="createName"
                type="text"
                value={createData.name}
                onChange={(e) => setCreateData({ ...createData, name: e.target.value })}
                placeholder="my-code-system"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="createTitle" className="block text-sm font-medium text-neutral-700 mb-1">
                Title
              </label>
              <input
                id="createTitle"
                type="text"
                value={createData.title}
                onChange={(e) => setCreateData({ ...createData, title: e.target.value })}
                placeholder="My Code System"
                className="input-field"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="createVersion" className="block text-sm font-medium text-neutral-700 mb-1">
                Version
              </label>
              <input
                id="createVersion"
                type="text"
                value={createData.version}
                onChange={(e) => setCreateData({ ...createData, version: e.target.value })}
                placeholder="1.0.0"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="createPublisher" className="block text-sm font-medium text-neutral-700 mb-1">
                Publisher
              </label>
              <input
                id="createPublisher"
                type="text"
                value={createData.publisher || ""}
                onChange={(e) => setCreateData({ ...createData, publisher: e.target.value })}
                placeholder="Organization name"
                className="input-field"
              />
            </div>
          </div>

          <div>
            <label htmlFor="createDesc" className="block text-sm font-medium text-neutral-700 mb-1">
              Description
            </label>
            <textarea
              id="createDesc"
              value={createData.description || ""}
              onChange={(e) => setCreateData({ ...createData, description: e.target.value })}
              placeholder="Describe this artifact..."
              rows={2}
              className="input-field"
            />
          </div>

          <div>
            <label htmlFor="createContent" className="block text-sm font-medium text-neutral-700 mb-1">
              Content (FHIR JSON / XML / CSV)
            </label>
            <textarea
              id="createContent"
              value={createData.content}
              onChange={(e) => setCreateData({ ...createData, content: e.target.value })}
              placeholder="Paste resource content here..."
              rows={8}
              className="input-field font-mono text-xs"
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

      {/* Filters */}
      <div className="flex gap-4 mb-4">
        <div className="flex-1">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by name, URL, or title..."
            className="input-field"
          />
        </div>
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value)}
          className="select-field w-48"
        >
          <option value="">All Types</option>
          {FHIR_TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="select-field w-40"
        >
          <option value="">All Statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="ACTIVE">Active</option>
          <option value="DEPRECATED">Deprecated</option>
          <option value="RETIRED">Retired</option>
        </select>
      </div>

      {/* Artifacts table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">FHIR Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Canonical URL</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Title</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Version</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Updated</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {artifacts.map((artifact) => (
              <tr key={artifact.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3">
                  <span className="badge bg-teal-100 text-teal-800">
                    {artifact.fhirType}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs max-w-xs truncate">
                  {artifact.canonicalUrl}
                </td>
                <td className="px-4 py-3 text-neutral-900">
                  {artifact.title}
                </td>
                <td className="px-4 py-3 text-neutral-600 font-mono text-xs">
                  {artifact.version}
                </td>
                <td className="px-4 py-3">
                  <span className={STATUS_COLORS[artifact.status]}>
                    {artifact.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(artifact.updatedAt).toLocaleDateString()}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    {artifact.status === "DRAFT" && (
                      <button
                        onClick={() => handlePublish(artifact.id)}
                        disabled={actionLoading}
                        className="text-xs text-emerald-600 hover:text-emerald-800 font-medium"
                      >
                        Publish
                      </button>
                    )}
                    {artifact.status === "ACTIVE" && (
                      <button
                        onClick={() => handleDeprecate(artifact.id)}
                        disabled={actionLoading}
                        className="text-xs text-yellow-600 hover:text-yellow-800 font-medium"
                      >
                        Deprecate
                      </button>
                    )}
                    {(artifact.status === "ACTIVE" || artifact.status === "DEPRECATED") && (
                      <button
                        onClick={() => handleRetire(artifact.id)}
                        disabled={actionLoading}
                        className="text-xs text-red-600 hover:text-red-800 font-medium"
                      >
                        Retire
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {artifacts.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  No artifacts found. Create one or import a FHIR Bundle.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {artifacts.length} artifact{artifacts.length !== 1 ? "s" : ""} total
      </div>
    </div>
  );
}
