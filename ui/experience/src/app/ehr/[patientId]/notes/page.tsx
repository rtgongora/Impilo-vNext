"use client";

/**
 * Clinical Notes - Encounter-aware documentation workspace.
 * Route: /ehr/[patientId]/notes | pageTitle: "Clinical Notes"
 */

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  ArrowUpRight,
  CheckCircle2,
  FileText,
  Filter,
  Loader2,
  Plus,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClinicalNotes,
  useCreateNote,
  useSignNote,
} from "@/hooks/queries/useClinicalNotes";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useAuthStore } from "@/hooks/useAuthStore";
import { COORDINATION_COPY, parseConsultationCoordinationMeta } from "@/lib/consult-workflows";

const NOTE_TYPES = ["PROGRESS", "ASSESSMENT", "DISCHARGE", "CONSULTATION"] as const;
type NoteScope = "current" | "all";

const EMPTY_FORM = {
  noteType: "PROGRESS" as string,
  subjective: "",
  objective: "",
  assessment: "",
  plan: "",
  body: "",
  authorId: "",
  authorName: "",
};

function formatEncounterStatus(status: unknown) {
  if (status === "ACTIVE" || status === "IN_PROGRESS") {
    return "bg-emerald-100 text-emerald-700";
  }

  return "bg-slate-100 text-slate-600";
}

export default function ClinicalNotesPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const searchParams = useSearchParams();
  const noteTypeFilter = searchParams.get("noteType");
  const { user } = useAuthStore();
  const { data: notesData, isLoading } = useClinicalNotes(patientId);
  const { data: encountersData } = useEncounters(patientId);
  const createNote = useCreateNote();
  const signNote = useSignNote();

  const defaultAuthorId = user?.id ?? "";
  const defaultAuthorName = user?.displayName ?? user?.email ?? "";
  const buildEmptyForm = (noteType = noteTypeFilter ?? "PROGRESS") => ({
    ...EMPTY_FORM,
    noteType,
    authorId: defaultAuthorId,
    authorName: defaultAuthorName,
  });

  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "ACTIVE" || encounter.attributes.status === "IN_PROGRESS",
  );

  const [showForm, setShowForm] = useState(false);
  const [filterType, setFilterType] = useState<string>(noteTypeFilter ?? "");
  const [scope, setScope] = useState<NoteScope>("current");
  const [form, setForm] = useState(buildEmptyForm());

  useEffect(() => {
    setForm((prev) => ({
      ...prev,
      authorId: defaultAuthorId,
      authorName: defaultAuthorName,
    }));
  }, [defaultAuthorId, defaultAuthorName]);

  const allNotes = notesData?.data ?? [];
  const sortedNotes = useMemo(
    () =>
      [...allNotes].sort(
        (left, right) =>
          new Date(right.attributes.createdAt).getTime() -
          new Date(left.attributes.createdAt).getTime(),
      ),
    [allNotes],
  );

  const encounterNotes = useMemo(
    () =>
      activeEncounter
        ? sortedNotes.filter((note) => note.attributes.encounterId === activeEncounter.id)
        : [],
    [activeEncounter, sortedNotes],
  );

  const draftCount = useMemo(
    () => sortedNotes.filter((note) => note.attributes.status === "DRAFT").length,
    [sortedNotes],
  );

  const consultationCount = useMemo(
    () => sortedNotes.filter((note) => note.attributes.noteType === "CONSULTATION").length,
    [sortedNotes],
  );
  const referralLoopUpdateCount = useMemo(
    () =>
      sortedNotes.filter(
        (note) =>
          note.attributes.noteType === "CONSULTATION" &&
          parseConsultationCoordinationMeta(note.attributes.body).hasReferralLoopUpdate,
      ).length,
    [sortedNotes],
  );

  const notes = useMemo(() => {
    const scopedNotes = scope === "current" && activeEncounter ? encounterNotes : sortedNotes;

    if (!filterType) {
      return scopedNotes;
    }

    return scopedNotes.filter((note) => note.attributes.noteType === filterType);
  }, [activeEncounter, encounterNotes, filterType, scope, sortedNotes]);

  function resetForm(nextType = form.noteType) {
    setForm(buildEmptyForm(nextType));
  }

  function handleFieldChange(
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  function handleStartNote(nextType: string) {
    setShowForm(true);
    setForm(buildEmptyForm(nextType));
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();

    createNote.mutate(
      {
        patientId,
        encounterId: activeEncounter?.id ?? "",
        noteType: form.noteType,
        subjective: form.subjective || null,
        objective: form.objective || null,
        assessment: form.assessment || null,
        plan: form.plan || null,
        body: form.body || null,
        authorId: form.authorId,
        authorName: form.authorName,
      },
      {
        onSuccess: () => {
          resetForm(form.noteType);
          setShowForm(false);
          setScope(activeEncounter ? "current" : "all");
        },
      },
    );
  }

  function handleSign(noteId: string) {
    signNote.mutate({ id: noteId });
  }

  return (
    <EHRLayout>
      <PageShell
        title="Clinical Notes"
        subtitle="Capture encounter documentation, consultation responses, and chart-level clinical reasoning."
      >
        <div className="space-y-6">
          <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(280px,1fr)]">
            <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_50%,#f8fafc_100%)] p-5 shadow-sm">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-3">
                  <div className="inline-flex items-center gap-2 rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-slate-600">
                    <FileText className="h-3.5 w-3.5 text-indigo-500" />
                    Documentation workspace
                  </div>
                  <div>
                    <h2 className="text-xl font-semibold text-slate-900">
                      {activeEncounter
                        ? "Active encounter documentation is live"
                        : "Chart-level notes are available"}
                    </h2>
                    <p className="mt-1 max-w-2xl text-sm text-slate-600">
                      {activeEncounter
                        ? "New notes will attach to the current encounter by default so the chart, encounter workspace, and downstream services stay in sync."
                        : "There is no active encounter right now, so notes created here will remain chart-level until a live visit is started."}
                    </p>
                  </div>
                  {activeEncounter && (
                    <div className="flex flex-wrap items-center gap-2 text-xs">
                      <span className="rounded-full bg-white px-3 py-1 font-medium text-slate-700 shadow-sm">
                        {String(activeEncounter.attributes.encounterType)} encounter
                      </span>
                      <span
                        className={`rounded-full px-3 py-1 font-medium ${formatEncounterStatus(
                          activeEncounter.attributes.status,
                        )}`}
                      >
                        {String(activeEncounter.attributes.status)}
                      </span>
                      <span className="rounded-full bg-white px-3 py-1 text-slate-500 shadow-sm">
                        Started {new Date(activeEncounter.attributes.startedAt).toLocaleString()}
                      </span>
                    </div>
                  )}
                </div>

                <div className="flex flex-wrap gap-2">
                  {activeEncounter && (
                    <Link
                      href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                      className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
                    >
                      <Activity className="h-4 w-4" />
                      Resume Encounter
                    </Link>
                  )}
                  <Link
                    href={`/ehr/${patientId}/summary`}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                  >
                    <ArrowUpRight className="h-4 w-4" />
                    Summary
                  </Link>
                  <Link
                    href={`/ehr/${patientId}/discharge`}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                  >
                    <ArrowUpRight className="h-4 w-4" />
                    Visit Outcome
                  </Link>
                </div>
              </div>

              <div className="mt-5 grid gap-3 sm:grid-cols-4">
                <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                  <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                    Visible Notes
                  </p>
                  <p className="mt-2 text-2xl font-semibold text-slate-900">{notes.length}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {scope === "current" && activeEncounter
                      ? "Filtered to the active encounter."
                      : "Across the patient chart."}
                  </p>
                </div>
                <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                  <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                    Draft Notes
                  </p>
                  <p className="mt-2 text-2xl font-semibold text-slate-900">{draftCount}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Unsigned entries still need clinician sign-off.
                  </p>
                </div>
                <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                  <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                    Consultation Notes
                  </p>
                  <p className="mt-2 text-2xl font-semibold text-slate-900">
                    {consultationCount}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    Specialist and cross-team documentation on file.
                  </p>
                </div>
                <div className="rounded-2xl border border-white/70 bg-white/80 p-4">
                  <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                    Referral Handoffs
                  </p>
                  <p className="mt-2 text-2xl font-semibold text-slate-900">
                    {referralLoopUpdateCount}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    Consultation notes that include returned referral loop updates.
                  </p>
                </div>
              </div>
            </div>

            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-2">
                <Plus className="h-4 w-4 text-blue-600" />
                <h3 className="text-sm font-semibold text-slate-900">Quick start</h3>
              </div>
              <p className="mt-2 text-sm text-slate-600">
                Start the right note type without leaving the chart. The form will open
                preselected and use the live encounter when one is active.
              </p>
              <div className="mt-4 grid gap-2">
                {NOTE_TYPES.map((noteType) => (
                  <button
                    key={noteType}
                    type="button"
                    onClick={() => handleStartNote(noteType)}
                    className="flex items-center justify-between rounded-2xl border border-slate-200 px-4 py-3 text-left transition-colors hover:border-blue-200 hover:bg-blue-50"
                  >
                    <span className="text-sm font-medium text-slate-900">{noteType}</span>
                    <ArrowUpRight className="h-4 w-4 text-slate-400" />
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-3 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-wrap items-center gap-2">
              <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
                <Filter className="h-3.5 w-3.5" />
                View
              </div>
              <button
                type="button"
                onClick={() => setScope("current")}
                disabled={!activeEncounter}
                className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                  scope === "current" && activeEncounter
                    ? "bg-slate-900 text-white"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-50"
                }`}
              >
                Current encounter
              </button>
              <button
                type="button"
                onClick={() => setScope("all")}
                className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                  scope === "all" || !activeEncounter
                    ? "bg-slate-900 text-white"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                }`}
              >
                All notes
              </button>
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => setFilterType("")}
                className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                  filterType === ""
                    ? "bg-blue-600 text-white"
                    : "bg-blue-50 text-blue-700 hover:bg-blue-100"
                }`}
              >
                All types
              </button>
              {NOTE_TYPES.map((noteType) => (
                <button
                  key={noteType}
                  type="button"
                  onClick={() => setFilterType(noteType)}
                  className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                    filterType === noteType
                      ? "bg-blue-600 text-white"
                      : "bg-blue-50 text-blue-700 hover:bg-blue-100"
                  }`}
                >
                  {noteType}
                </button>
              ))}
            </div>
          </div>

          {showForm && (
            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex flex-col gap-3 border-b border-slate-100 pb-4 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">New Clinical Note</h3>
                  <p className="mt-1 text-sm text-slate-500">
                    {activeEncounter
                      ? `This note will be attached to encounter ${activeEncounter.id}.`
                      : "No active encounter is open, so this note will remain chart-level."}
                  </p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                  <p className="font-medium text-slate-900">Documentation target</p>
                  <p className="mt-1">
                    {activeEncounter
                      ? `${String(activeEncounter.attributes.encounterType)} encounter`
                      : "Patient chart"}
                  </p>
                </div>
              </div>

              <form onSubmit={handleSubmit} className="mt-5 space-y-4">
                <div>
                  <label htmlFor="noteType" className="mb-1 block text-sm font-medium text-slate-700">
                    Note Type
                  </label>
                  <select
                    id="noteType"
                    name="noteType"
                    value={form.noteType}
                    onChange={handleFieldChange}
                    className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {NOTE_TYPES.map((noteType) => (
                      <option key={noteType} value={noteType}>
                        {noteType}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div>
                    <label htmlFor="subjective" className="mb-1 block text-sm font-medium text-slate-700">
                      Subjective
                    </label>
                    <textarea
                      id="subjective"
                      name="subjective"
                      value={form.subjective}
                      onChange={handleFieldChange}
                      rows={4}
                      placeholder="Patient-reported symptoms, concerns, and history..."
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label htmlFor="objective" className="mb-1 block text-sm font-medium text-slate-700">
                      Objective
                    </label>
                    <textarea
                      id="objective"
                      name="objective"
                      value={form.objective}
                      onChange={handleFieldChange}
                      rows={4}
                      placeholder="Observed findings, vitals, and measurable data..."
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label htmlFor="assessment" className="mb-1 block text-sm font-medium text-slate-700">
                      Assessment
                    </label>
                    <textarea
                      id="assessment"
                      name="assessment"
                      value={form.assessment}
                      onChange={handleFieldChange}
                      rows={4}
                      placeholder="Clinical interpretation, diagnosis, and working impression..."
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label htmlFor="plan" className="mb-1 block text-sm font-medium text-slate-700">
                      Plan
                    </label>
                    <textarea
                      id="plan"
                      name="plan"
                      value={form.plan}
                      onChange={handleFieldChange}
                      rows={4}
                      placeholder="Orders, follow-up, treatment, and coordination steps..."
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="body" className="mb-1 block text-sm font-medium text-slate-700">
                    Additional Notes
                  </label>
                  <textarea
                    id="body"
                    name="body"
                    value={form.body}
                    onChange={handleFieldChange}
                    rows={4}
                    placeholder="Free-text details, coordination notes, or specialist context..."
                    className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div>
                    <label htmlFor="authorId" className="mb-1 block text-sm font-medium text-slate-700">
                      Author ID
                    </label>
                    <input
                      id="authorId"
                      name="authorId"
                      type="text"
                      value={form.authorId}
                      onChange={handleFieldChange}
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label htmlFor="authorName" className="mb-1 block text-sm font-medium text-slate-700">
                      Author Name
                    </label>
                    <input
                      id="authorName"
                      name="authorName"
                      type="text"
                      value={form.authorName}
                      onChange={handleFieldChange}
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-3 pt-2">
                  <button
                    type="submit"
                    disabled={createNote.isPending}
                    className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
                  >
                    {createNote.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Save Note
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setShowForm(false);
                      resetForm();
                    }}
                    className="rounded-xl px-4 py-2.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
              <span className="ml-2 text-sm text-slate-500">Loading clinical notes...</span>
            </div>
          ) : notes.length === 0 ? (
            <div className="rounded-3xl border border-slate-200 bg-white p-12 text-center shadow-sm">
              <FileText className="mx-auto mb-3 h-10 w-10 text-slate-300" />
              <p className="text-sm text-slate-500">
                {scope === "current" && activeEncounter
                  ? "No notes have been recorded for the active encounter yet."
                  : "No clinical notes have been recorded for this patient yet."}
              </p>
              <button
                type="button"
                onClick={() => handleStartNote(noteTypeFilter ?? "PROGRESS")}
                className="mt-4 inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700"
              >
                <Plus className="h-4 w-4" />
                Start First Note
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {notes.map((note) => {
                const attrs = note.attributes;
                const coordinationMeta = parseConsultationCoordinationMeta(attrs.body);
                const hasSoap =
                  attrs.subjective || attrs.objective || attrs.assessment || attrs.plan;
                const isDraft = attrs.status === "DRAFT";
                const isLinkedToActiveEncounter =
                  !!activeEncounter && attrs.encounterId === activeEncounter.id;

                return (
                  <div
                    key={note.id}
                    className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"
                  >
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div className="flex items-start gap-3">
                        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600">
                          <FileText className="h-5 w-5" />
                        </div>
                        <div>
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="text-sm font-semibold text-slate-900">{attrs.noteType}</p>
                            <span
                              className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                isDraft
                                  ? "bg-amber-100 text-amber-700"
                                  : "bg-emerald-100 text-emerald-700"
                              }`}
                            >
                              {attrs.status}
                            </span>
                            {attrs.encounterId ? (
                              <span
                                className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                                  isLinkedToActiveEncounter
                                    ? "bg-blue-100 text-blue-700"
                                    : "bg-slate-100 text-slate-600"
                                }`}
                              >
                                {isLinkedToActiveEncounter
                                  ? "Active encounter"
                                  : "Encounter-linked"}
                              </span>
                            ) : (
                              <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                                Chart-level
                              </span>
                            )}
                            {coordinationMeta.hasReferralLoopUpdate && (
                              <span className="rounded-full bg-purple-100 px-2.5 py-0.5 text-xs font-medium text-purple-700">
                                Referral loop update
                              </span>
                            )}
                          </div>
                          <p className="mt-1 text-xs text-slate-500">{attrs.authorName}</p>
                          <p className="mt-1 text-xs text-slate-500">
                            {new Date(attrs.createdAt).toLocaleString()}
                            {attrs.signedAt && (
                              <span className="ml-2">
                                Signed {new Date(attrs.signedAt).toLocaleString()}
                              </span>
                            )}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-2">
                        {attrs.encounterId && (
                          <Link
                            href={`/ehr/${patientId}/encounter/${attrs.encounterId}`}
                            className="inline-flex items-center gap-1 rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50"
                          >
                            <ArrowUpRight className="h-3.5 w-3.5" />
                            Encounter
                          </Link>
                        )}
                        {isDraft && (
                          <button
                            type="button"
                            onClick={() => handleSign(note.id)}
                            disabled={signNote.isPending}
                            className="inline-flex items-center gap-1 rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-emerald-700 disabled:opacity-50"
                          >
                            {signNote.isPending ? (
                              <Loader2 className="h-3 w-3 animate-spin" />
                            ) : (
                              <CheckCircle2 className="h-3 w-3" />
                            )}
                            Sign
                          </button>
                        )}
                      </div>
                    </div>

                    {coordinationMeta.hasReferralLoopUpdate && (
                      <div className="mt-4 rounded-2xl border border-purple-200 bg-purple-50 p-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-purple-700">
                            Consult Orchestration
                          </p>
                          {coordinationMeta.responseSentFromTeleconsult && (
                            <span className="rounded-full bg-white px-2.5 py-1 text-xs font-medium text-purple-700">
                              {COORDINATION_COPY.returnedFromTeleconsult}
                            </span>
                          )}
                        </div>
                        <div className="mt-3 grid gap-3 md:grid-cols-3">
                          {coordinationMeta.linkedReferralId && (
                            <div className="rounded-xl bg-white px-3 py-3">
                              <p className="text-xs font-medium text-slate-500">Linked referral</p>
                              <p className="mt-1 text-sm font-medium text-slate-900">
                                {coordinationMeta.linkedReferralId}
                              </p>
                            </div>
                          )}
                          {coordinationMeta.linkedTeleconsult && (
                            <div className="rounded-xl bg-white px-3 py-3">
                              <p className="text-xs font-medium text-slate-500">Linked teleconsult</p>
                              <p className="mt-1 text-sm font-medium text-slate-900">
                                {coordinationMeta.linkedTeleconsult}
                              </p>
                            </div>
                          )}
                          {coordinationMeta.nextWorkspaceAction && (
                            <div className="rounded-xl bg-white px-3 py-3">
                              <p className="text-xs font-medium text-slate-500">Next workspace action</p>
                              <p className="mt-1 text-sm font-medium text-slate-900">
                                {coordinationMeta.nextWorkspaceAction}
                              </p>
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {hasSoap && (
                      <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
                        {attrs.subjective && (
                          <div className="rounded-2xl border border-slate-100 p-3">
                            <p className="mb-1 text-xs font-medium text-slate-500">Subjective</p>
                            <p className="text-sm text-slate-900">{attrs.subjective}</p>
                          </div>
                        )}
                        {attrs.objective && (
                          <div className="rounded-2xl border border-slate-100 p-3">
                            <p className="mb-1 text-xs font-medium text-slate-500">Objective</p>
                            <p className="text-sm text-slate-900">{attrs.objective}</p>
                          </div>
                        )}
                        {attrs.assessment && (
                          <div className="rounded-2xl border border-slate-100 p-3">
                            <p className="mb-1 text-xs font-medium text-slate-500">Assessment</p>
                            <p className="text-sm text-slate-900">{attrs.assessment}</p>
                          </div>
                        )}
                        {attrs.plan && (
                          <div className="rounded-2xl border border-slate-100 p-3">
                            <p className="mb-1 text-xs font-medium text-slate-500">Plan</p>
                            <p className="text-sm text-slate-900">{attrs.plan}</p>
                          </div>
                        )}
                      </div>
                    )}

                    {attrs.body && (
                      <div className="mt-4 rounded-2xl border border-slate-100 p-3">
                        <p className="mb-1 text-xs font-medium text-slate-500">Additional Notes</p>
                        <p className="whitespace-pre-wrap text-sm text-slate-900">{attrs.body}</p>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </PageShell>
    </EHRLayout>
  );
}
