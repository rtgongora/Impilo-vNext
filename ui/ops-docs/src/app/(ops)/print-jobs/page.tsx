"use client";

import { useState, useEffect, useCallback } from "react";
import { Card } from "shared-ui";

interface PrintJob {
  jobId: string;
  jobType: string;
  subjectName: string;
  status: string;
  templateName: string;
  priority: number;
  createdAt: string;
}

export default function PrintQueuePage() {
  const [jobs, setJobs] = useState<PrintJob[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(false);

  const loadJobs = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (statusFilter) params.set("status", statusFilter);
      const res = await fetch(`/api/v1/print-jobs?${params}`);
      const data = await res.json();
      setJobs(data.data?.items ?? []);
    } catch {
      setJobs([]);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Print Queue</h1>

      <div className="flex gap-2 mb-4">
        {["", "QUEUED", "RENDERING", "RENDERED", "PRINTING", "PRINTED", "COLLECTED", "FAILED"].map((s) => (
          <button key={s} onClick={() => setStatusFilter(s)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              statusFilter === s ? "bg-brand-primary text-white" : "bg-card text-neutral-600 border border-neutral-300 hover:bg-neutral-50"
            }`}>
            {s || "All"}
          </button>
        ))}
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200">
                <th className="text-left p-3 font-medium text-neutral-500">Job ID</th>
                <th className="text-left p-3 font-medium text-neutral-500">Type</th>
                <th className="text-left p-3 font-medium text-neutral-500">Subject</th>
                <th className="text-left p-3 font-medium text-neutral-500">Template</th>
                <th className="text-left p-3 font-medium text-neutral-500">Priority</th>
                <th className="text-left p-3 font-medium text-neutral-500">Status</th>
                <th className="text-left p-3 font-medium text-neutral-500">Created</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.jobId} className="border-b border-neutral-100 hover:bg-neutral-50">
                  <td className="p-3 font-mono text-xs">{job.jobId.slice(0, 8)}...</td>
                  <td className="p-3">{job.jobType}</td>
                  <td className="p-3">{job.subjectName}</td>
                  <td className="p-3">{job.templateName}</td>
                  <td className="p-3">{job.priority}</td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                      job.status === "PRINTED" || job.status === "COLLECTED" ? "bg-green-100 text-green-800" :
                      job.status === "FAILED" ? "bg-red-100 text-red-800" :
                      job.status === "QUEUED" ? "bg-blue-100 text-blue-800" :
                      "bg-yellow-100 text-yellow-800"
                    }`}>{job.status}</span>
                  </td>
                  <td className="p-3 text-neutral-500 text-xs">{new Date(job.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {jobs.length === 0 && !loading && (
          <div className="text-center py-8 text-neutral-500 text-sm">No print jobs found</div>
        )}
      </Card>
    </div>
  );
}
