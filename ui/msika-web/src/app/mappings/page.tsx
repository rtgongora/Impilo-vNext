"use client";
import { useState, useEffect } from "react";
import { apiClient, StepUpRequiredError } from "@/lib/apiClient";

interface Mapping {
  id: string;
  sourceId: string;
  externalCode: string;
  externalName: string;
  internalItemId: string | null;
  mapType: string;
  confidence: number;
  status: string;
  createdAt: string;
}

export default function MappingsPage() {
  const [mappings, setMappings] = useState<Mapping[]>([]);
  const [loading, setLoading] = useState(true);
  const [stepUp, setStepUp] = useState(false);

  const load = async () => {
    try {
      const data = await apiClient.get<any>("/v1/mappings/pending?size=50");
      setMappings(data?.items || []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const action = async (id: string, act: "approve" | "reject") => {
    try {
      setStepUp(false);
      await apiClient.post(`/v1/mappings/${id}/${act}`);
      load();
    } catch (e) {
      if (e instanceof StepUpRequiredError) setStepUp(true);
      else alert(String(e));
    }
  };

  return (
    <div>
      <h2 className="text-xl font-bold mb-4">Mapping Approval Queue</h2>

      {stepUp && (
        <div className="bg-amber-50 border border-amber-300 text-amber-800 px-4 py-3 rounded mb-4">
          Step-up authentication required for mapping approval. Please re-authenticate.
        </div>
      )}

      {loading ? (
        <p className="text-gray-500">Loading...</p>
      ) : mappings.length === 0 ? (
        <p className="text-gray-500">No pending mappings to review.</p>
      ) : (
        <table className="w-full bg-white rounded shadow text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left px-4 py-3">External Code</th>
              <th className="text-left px-4 py-3">External Name</th>
              <th className="text-left px-4 py-3">Internal Item</th>
              <th className="text-left px-4 py-3">Type</th>
              <th className="text-left px-4 py-3">Confidence</th>
              <th className="text-left px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {mappings.map(m => (
              <tr key={m.id} className="border-t hover:bg-gray-50">
                <td className="px-4 py-3 font-mono text-xs">{m.externalCode}</td>
                <td className="px-4 py-3">{m.externalName}</td>
                <td className="px-4 py-3 font-mono text-xs">{m.internalItemId || "—"}</td>
                <td className="px-4 py-3"><span className="px-2 py-0.5 rounded text-xs bg-gray-100">{m.mapType}</span></td>
                <td className="px-4 py-3">{(m.confidence * 100).toFixed(1)}%</td>
                <td className="px-4 py-3 space-x-2">
                  <button onClick={() => action(m.id, "approve")}
                    className="text-xs text-green-600 hover:underline font-medium">Approve</button>
                  <button onClick={() => action(m.id, "reject")}
                    className="text-xs text-red-600 hover:underline font-medium">Reject</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
