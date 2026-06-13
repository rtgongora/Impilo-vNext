"use client";
import { useState, useRef } from "react";
import { apiClient } from "@/lib/apiClient";

interface CsvImportResult {
  totalRows: number;
  importedRows: number;
  validRows: number;
  duplicateRows: number;
  errorRows: number;
  jobId: string;
  status: string;
}

export default function ImportPage() {
  const [catalogId, setCatalogId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<CsvImportResult | null>(null);
  const [loading, setLoading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const upload = async () => {
    if (!file || !catalogId) { alert("Select file and enter catalog ID"); return; }
    setLoading(true);
    try {
      const data = await apiClient.uploadCsv<CsvImportResult>("/v1/import/csv", file, { catalogId });
      setResult(data);
    } catch (e) {
      alert(String(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h2 className="text-xl font-bold mb-4">Import Data</h2>

      <div className="bg-card p-6 rounded shadow border mb-6">
        <h3 className="font-semibold mb-3">CSV Import</h3>
        <p className="text-sm text-muted-foreground mb-4">
          Upload a CSV file with columns: canonical_code, display_name, kind, description, tags, form, strength, route, uom, pack_size, barcode, manufacturer, atc_code
        </p>
        <div className="grid grid-cols-2 gap-4 mb-4">
          <input placeholder="Target Catalog ID" value={catalogId}
            onChange={e => setCatalogId(e.target.value)}
            className="border rounded px-3 py-2 text-sm" />
          <input type="file" accept=".csv" ref={fileRef}
            onChange={e => setFile(e.target.files?.[0] || null)}
            className="border rounded px-3 py-2 text-sm" />
        </div>
        <button onClick={upload} disabled={loading}
          className="bg-primary text-white px-4 py-2 rounded text-sm hover:bg-primary-dark disabled:opacity-50">
          {loading ? "Importing..." : "Upload & Import"}
        </button>
      </div>

      {result && (
        <div className="bg-card p-6 rounded shadow border">
          <h3 className="font-semibold mb-3">Import Result</h3>
          <div className="grid grid-cols-5 gap-4 text-center">
            <div className="p-3 bg-background rounded">
              <div className="text-2xl font-bold">{result.totalRows}</div>
              <div className="text-xs text-muted-foreground">Total</div>
            </div>
            <div className="p-3 bg-green-50 rounded">
              <div className="text-2xl font-bold text-green-700">{result.importedRows}</div>
              <div className="text-xs text-muted-foreground">Imported</div>
            </div>
            <div className="p-3 bg-info-soft rounded">
              <div className="text-2xl font-bold text-primary-hover">{result.validRows}</div>
              <div className="text-xs text-muted-foreground">Valid</div>
            </div>
            <div className="p-3 bg-yellow-50 rounded">
              <div className="text-2xl font-bold text-yellow-700">{result.duplicateRows}</div>
              <div className="text-xs text-muted-foreground">Duplicates</div>
            </div>
            <div className="p-3 bg-danger-soft rounded">
              <div className="text-2xl font-bold text-danger">{result.errorRows}</div>
              <div className="text-xs text-muted-foreground">Errors</div>
            </div>
          </div>
          <p className="mt-3 text-xs text-muted-foreground">Job ID: {result.jobId} | Status: {result.status}</p>
        </div>
      )}
    </div>
  );
}
