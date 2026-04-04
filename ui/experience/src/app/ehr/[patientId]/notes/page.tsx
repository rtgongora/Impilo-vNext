"use client";

/**
 * Clinical Notes — View, create, and sign clinical notes for a patient.
 * Route: /ehr/[patientId]/notes | pageTitle: "Clinical Notes"
 */

import { useParams } from "next/navigation";
import { useState } from "react";
import { FileText, Plus, Loader2, CheckCircle2} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClinicalNotes,
  useCreateNote,
  useSignNote } from "@/hooks/queries/useClinicalNotes";
import { useAuthStore } from "@/hooks/useAuthStore";

const NOTE_TYPES = ["PROGRESS", "ASSESSMENT", "DISCHARGE", "CONSULTATION"] as const;

const EMPTY_FORM = {
  noteType: "PROGRESS" as string,
  subjective: "",
  objective: "",
  assessment: "",
  plan: "",
  body: "",
  authorId: "",
  authorName: "" };

export default function ClinicalNotesPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const { data: notesData, isLoading } = useClinicalNotes(patientId);
  const createNote = useCreateNote();
  const signNote = useSignNote();

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    ...EMPTY_FORM,
    authorId: user?.id ?? "",
    authorName: user?.displayName ?? user?.email ?? "" });

  const notes = notesData?.data ?? [];

  function handleFieldChange(
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    createNote.mutate(
      {
        patientId,
        encounterId: "",
        noteType: form.noteType,
        subjective: form.subjective || null,
        objective: form.objective || null,
        assessment: form.assessment || null,
        plan: form.plan || null,
        body: form.body || null,
        authorId: form.authorId,
        authorName: form.authorName },
      {
        onSuccess: () => {
          setForm(EMPTY_FORM);
          setShowForm(false);
        } },
    );
  }

  function handleSign(noteId: string) {
    signNote.mutate({ id: noteId });
  }

  return (
    <EHRLayout>
      <PageShell title="Clinical Notes">

        {/* Header with Add Note button */}
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-900">Notes</h2>
          <button
            type="button"
            onClick={() => setShowForm((prev) => !prev)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Add Note
          </button>
        </div>

        {/* Create Note Form */}
        {showForm && (
          <div className="bg-white rounded-lg border border-gray-200 p-5 mb-6">
            <h3 className="font-medium text-gray-900 mb-4">New Clinical Note</h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Note Type */}
              <div>
                <label htmlFor="noteType" className="block text-sm font-medium text-gray-700 mb-1">
                  Note Type
                </label>
                <select
                  id="noteType"
                  name="noteType"
                  value={form.noteType}
                  onChange={handleFieldChange}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                >
                  {NOTE_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>

              {/* SOAP Fields */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="subjective" className="block text-sm font-medium text-gray-700 mb-1">
                    Subjective
                  </label>
                  <textarea
                    id="subjective"
                    name="subjective"
                    value={form.subjective}
                    onChange={handleFieldChange}
                    rows={3}
                    placeholder="Patient's reported symptoms and history..."
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
                <div>
                  <label htmlFor="objective" className="block text-sm font-medium text-gray-700 mb-1">
                    Objective
                  </label>
                  <textarea
                    id="objective"
                    name="objective"
                    value={form.objective}
                    onChange={handleFieldChange}
                    rows={3}
                    placeholder="Clinical observations and findings..."
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
                <div>
                  <label htmlFor="assessment" className="block text-sm font-medium text-gray-700 mb-1">
                    Assessment
                  </label>
                  <textarea
                    id="assessment"
                    name="assessment"
                    value={form.assessment}
                    onChange={handleFieldChange}
                    rows={3}
                    placeholder="Clinical assessment and diagnosis..."
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
                <div>
                  <label htmlFor="plan" className="block text-sm font-medium text-gray-700 mb-1">
                    Plan
                  </label>
                  <textarea
                    id="plan"
                    name="plan"
                    value={form.plan}
                    onChange={handleFieldChange}
                    rows={3}
                    placeholder="Treatment plan and next steps..."
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
              </div>

              {/* Free-text Body */}
              <div>
                <label htmlFor="body" className="block text-sm font-medium text-gray-700 mb-1">
                  Additional Notes
                </label>
                <textarea
                  id="body"
                  name="body"
                  value={form.body}
                  onChange={handleFieldChange}
                  rows={3}
                  placeholder="Free-text notes..."
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
              </div>

              {/* Author fields */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="authorId" className="block text-sm font-medium text-gray-700 mb-1">
                    Author ID
                  </label>
                  <input
                    id="authorId"
                    name="authorId"
                    type="text"
                    value={form.authorId}
                    onChange={handleFieldChange}
                    placeholder="e.g. prov-001"
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
                <div>
                  <label htmlFor="authorName" className="block text-sm font-medium text-gray-700 mb-1">
                    Author Name
                  </label>
                  <input
                    id="authorName"
                    name="authorName"
                    type="text"
                    value={form.authorName}
                    onChange={handleFieldChange}
                    placeholder="e.g. Dr. Smith"
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
              </div>

              {/* Submit */}
              <div className="flex items-center gap-3 pt-2">
                <button
                  type="submit"
                  disabled={createNote.isPending}
                  className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50"
                >
                  {createNote.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                  Save Note
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowForm(false);
                    setForm(EMPTY_FORM);
                  }}
                  className="px-4 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Notes List */}
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading clinical notes...</span>
          </div>
        ) : notes.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No clinical notes</p>
          </div>
        ) : (
          <div className="space-y-3">
            {notes.map((note) => {
              const attrs = note.attributes;
              const hasSoap = attrs.subjective || attrs.objective || attrs.assessment || attrs.plan;
              const isDraft = attrs.status === "DRAFT";

              return (
                <div
                  key={note.id}
                  className="bg-white rounded-lg border border-gray-200 p-5"
                >
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center">
                        <FileText className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900">{attrs.noteType}</p>
                        <p className="text-xs text-gray-500">{attrs.authorName}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`px-2 py-0.5 text-xs rounded-full ${
                          isDraft
                            ? "bg-yellow-100 text-yellow-700"
                            : "bg-green-100 text-green-700"
                        }`}
                      >
                        {attrs.status}
                      </span>
                      {isDraft && (
                        <button
                          type="button"
                          onClick={() => handleSign(note.id)}
                          disabled={signNote.isPending}
                          className="inline-flex items-center gap-1 px-3 py-1 bg-green-600 text-white text-xs font-medium rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50"
                        >
                          {signNote.isPending ? (
                            <Loader2 className="w-3 h-3 animate-spin" />
                          ) : (
                            <CheckCircle2 className="w-3 h-3" />
                          )}
                          Sign
                        </button>
                      )}
                    </div>
                  </div>

                  <p className="text-xs text-gray-500 mb-3">
                    {new Date(attrs.createdAt).toLocaleString()}
                    {attrs.signedAt && (
                      <span className="ml-2">
                        — Signed {new Date(attrs.signedAt).toLocaleString()}
                      </span>
                    )}
                  </p>

                  {/* SOAP fields */}
                  {hasSoap && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
                      {attrs.subjective && (
                        <div className="rounded-lg border border-gray-100 p-3">
                          <p className="text-xs font-medium text-gray-500 mb-1">Subjective</p>
                          <p className="text-sm text-gray-900">{attrs.subjective}</p>
                        </div>
                      )}
                      {attrs.objective && (
                        <div className="rounded-lg border border-gray-100 p-3">
                          <p className="text-xs font-medium text-gray-500 mb-1">Objective</p>
                          <p className="text-sm text-gray-900">{attrs.objective}</p>
                        </div>
                      )}
                      {attrs.assessment && (
                        <div className="rounded-lg border border-gray-100 p-3">
                          <p className="text-xs font-medium text-gray-500 mb-1">Assessment</p>
                          <p className="text-sm text-gray-900">{attrs.assessment}</p>
                        </div>
                      )}
                      {attrs.plan && (
                        <div className="rounded-lg border border-gray-100 p-3">
                          <p className="text-xs font-medium text-gray-500 mb-1">Plan</p>
                          <p className="text-sm text-gray-900">{attrs.plan}</p>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Free-text body */}
                  {attrs.body && (
                    <div className="rounded-lg border border-gray-100 p-3">
                      <p className="text-xs font-medium text-gray-500 mb-1">Notes</p>
                      <p className="text-sm text-gray-900 whitespace-pre-wrap">{attrs.body}</p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
