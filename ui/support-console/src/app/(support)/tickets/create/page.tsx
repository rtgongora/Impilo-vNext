"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { createTicket } from "@/lib/supportApi";
import type { TicketCategory, TicketPriority } from "@/types/support";

const CATEGORY_OPTIONS: TicketCategory[] = ["GENERAL", "INCIDENT", "BUG", "FEATURE_REQUEST", "ACCESS", "CONFIGURATION", "DATA"];
const PRIORITY_OPTIONS: TicketPriority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export default function CreateTicketPage() {
  const router = useRouter();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<TicketCategory>("GENERAL");
  const [priority, setPriority] = useState<TicketPriority>("MEDIUM");
  const [reporterRef, setReporterRef] = useState("");
  const [facilityRef, setFacilityRef] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !description.trim() || !reporterRef.trim()) return;

    setSubmitting(true);
    setError(null);
    try {
      const ticket = await createTicket({
        title: title.trim(),
        description: description.trim(),
        reporterRef: reporterRef.trim(),
        category,
        priority,
        facilityRef: facilityRef.trim() || undefined,
      });
      router.push(`/tickets/${ticket.ticketId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create ticket");
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-2xl">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Create Ticket</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Log a new support ticket for tracking and resolution
        </p>
      </div>

      {error && (
        <div className="bg-danger/10 border border-danger/20 rounded-lg p-4 text-sm text-danger mb-4">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6 space-y-5">
        <div>
          <label htmlFor="title" className="block text-sm font-medium text-neutral-700 mb-1.5">
            Title <span className="text-danger">*</span>
          </label>
          <input
            id="title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Brief summary of the issue"
            required
            className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
          />
        </div>

        <div>
          <label htmlFor="description" className="block text-sm font-medium text-neutral-700 mb-1.5">
            Description <span className="text-danger">*</span>
          </label>
          <textarea
            id="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Detailed description of the issue, including steps to reproduce if applicable"
            required
            rows={5}
            className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 resize-y"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="category" className="block text-sm font-medium text-neutral-700 mb-1.5">
              Category
            </label>
            <select
              id="category"
              value={category}
              onChange={(e) => setCategory(e.target.value as TicketCategory)}
              className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm bg-card focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
            >
              {CATEGORY_OPTIONS.map((c) => (
                <option key={c} value={c}>{c.replace("_", " ")}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="priority" className="block text-sm font-medium text-neutral-700 mb-1.5">
              Priority
            </label>
            <select
              id="priority"
              value={priority}
              onChange={(e) => setPriority(e.target.value as TicketPriority)}
              className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm bg-card focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
            >
              {PRIORITY_OPTIONS.map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label htmlFor="reporter" className="block text-sm font-medium text-neutral-700 mb-1.5">
            Reporter Reference <span className="text-danger">*</span>
          </label>
          <input
            id="reporter"
            type="text"
            value={reporterRef}
            onChange={(e) => setReporterRef(e.target.value)}
            placeholder="e.g. nurse-001, user-abc123"
            required
            className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
          />
        </div>

        <div>
          <label htmlFor="facility" className="block text-sm font-medium text-neutral-700 mb-1.5">
            Facility Reference
          </label>
          <input
            id="facility"
            type="text"
            value={facilityRef}
            onChange={(e) => setFacilityRef(e.target.value)}
            placeholder="e.g. FAC-001 (optional)"
            className="w-full px-4 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
          />
        </div>

        <div className="flex items-center gap-3 pt-2">
          <button
            type="submit"
            disabled={submitting || !title.trim() || !description.trim() || !reporterRef.trim()}
            className="px-6 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {submitting ? "Creating..." : "Create Ticket"}
          </button>
          <button
            type="button"
            onClick={() => router.back()}
            className="px-4 py-2 text-sm text-neutral-600 hover:text-neutral-900"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
