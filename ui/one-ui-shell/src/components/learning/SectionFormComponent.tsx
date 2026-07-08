"use client";

import { useState } from "react";
import { type Row } from "@/components/learning/learningUtils";
import { FileUploadField } from "./FileUploadField";

export function SectionFormComponent({
  onCancel,
  onSubmit,
  courseId,
  sequenceNo,
}: {
  onCancel: () => void;
  onSubmit: (section: Row) => void;
  courseId: string;
  sequenceNo: number;
}) {
  const [sectionType, setSectionType] = useState("");
  const [sectionTitle, setSectionTitle] = useState("");
  const [sectionContentRef, setSectionContentRef] = useState("");
  const [sectionTranscript, setSectionTranscript] = useState("");

  const handleSubmit = () => {
    if (!sectionType || !sectionTitle) {
      alert("Please fill in required fields");
      return;
    }
    if ((sectionType === "VIDEO" || sectionType === "DOCUMENT" || sectionType === "LINK" || sectionType === "TEXT") && !sectionContentRef && sectionType !== "TEXT") {
      alert("Please provide content reference for this section type");
      return;
    }

    const newSection: Row = {
      id: `section-${Date.now()}`,
      title: sectionTitle,
      name: sectionTitle,
      contentType: sectionType,
      type: sectionType,
      contentRef: sectionContentRef,
      transcript: sectionTranscript,
      courseId: courseId,
      sequenceNo: sequenceNo,
      status: "DRAFT",
      autoLinked: sectionType === "VIDEO" || sectionType === "DOCUMENT" || sectionType === "INTERACTIVE",
    };

    onSubmit(newSection);
  };

  return (
    <div className="mb-4 p-4 rounded-lg border border-teal-200 bg-teal-50">
      <h3 className="font-semibold text-slate-950 mb-3">Create New Section</h3>
      <div className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Section Type *</label>
          <select
            value={sectionType}
            onChange={(e) => setSectionType(e.target.value)}
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
          >
            <option value="">Select type...</option>
            <option value="TEXT">📝 Text Content</option>
            <option value="VIDEO">🎬 Video</option>
            <option value="DOCUMENT">📄 Document</option>
            <option value="LINK">🔗 External Link</option>
            <option value="INTERACTIVE">❓ Quiz/Assessment</option>
            <option value="PRACTICAL_TASK">🛠️ Practical Task</option>
          </select>
        </div>

        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Title *</label>
          <input
            type="text"
            placeholder="e.g., Introduction to Patient Care"
            value={sectionTitle}
            onChange={(e) => setSectionTitle(e.target.value)}
            className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
          />
        </div>

        {sectionType === "VIDEO" && (
          <>
            <FileUploadField
              name="videoFile"
              label="Video File"
              accept="video/*"
              maxSizeMB={500}
              help="Upload via document-service (MinIO backend). Max 500MB."
              required
              onFileUploaded={(storageRef) => setSectionContentRef(storageRef)}
              readOnly={false}
            />
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Transcript (optional)</label>
              <textarea
                placeholder="Video transcript or description..."
                value={sectionTranscript}
                onChange={(e) => setSectionTranscript(e.target.value)}
                rows={2}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </div>
            <div className="text-xs text-teal-700 bg-white border border-teal-100 p-2 rounded">
              ✨ This video will automatically appear in <strong>Media Assets</strong> for reuse in other courses.
            </div>
          </>
        )}

        {sectionType === "DOCUMENT" && (
          <>
            <FileUploadField
              name="documentFile"
              label="Document File"
              accept=".pdf,.doc,.docx,.txt,.xls,.xlsx,.ppt,.pptx"
              maxSizeMB={100}
              help="Upload via document-service (MinIO backend). Max 100MB."
              required
              onFileUploaded={(storageRef) => setSectionContentRef(storageRef)}
              readOnly={false}
            />
            <div className="text-xs text-teal-700 bg-white border border-teal-100 p-2 rounded">
              ✨ This document will automatically appear in <strong>Library Resources</strong> for reuse in other courses.
            </div>
          </>
        )}

        {sectionType === "LINK" && (
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">External URL *</label>
            <input
              type="url"
              placeholder="https://example.com"
              value={sectionContentRef}
              onChange={(e) => setSectionContentRef(e.target.value)}
              className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
            />
          </div>
        )}

        {sectionType === "INTERACTIVE" && (
          <>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Quiz Title or Name *</label>
              <input
                type="text"
                placeholder="e.g., Module 1 Assessment"
                value={sectionContentRef}
                onChange={(e) => setSectionContentRef(e.target.value)}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Question Type (optional)</label>
              <select className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100">
                <option value="">Leave blank to configure later</option>
                <option value="MULTIPLE_CHOICE">Multiple Choice</option>
                <option value="TRUE_FALSE">True/False</option>
                <option value="SHORT_ANSWER">Short Answer</option>
                <option value="ESSAY">Essay</option>
                <option value="MATCHING">Matching</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Description (optional)</label>
              <textarea
                placeholder="Quiz instructions or description..."
                value={sectionTranscript}
                onChange={(e) => setSectionTranscript(e.target.value)}
                rows={2}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </div>
            <div className="text-xs text-teal-700 bg-white border border-teal-100 p-2 rounded">
              ✨ This quiz will automatically appear in <strong>Interactive Activities</strong> for reuse in other courses.
            </div>
          </>
        )}

        {sectionType === "PRACTICAL_TASK" && (
          <>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Task Description *</label>
              <textarea
                placeholder="What should learners do for this practical task?"
                value={sectionContentRef}
                onChange={(e) => setSectionContentRef(e.target.value)}
                rows={3}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600 mb-1">Expected Duration (optional)</label>
              <input
                type="text"
                placeholder="e.g., 30 minutes, 1 hour"
                value={sectionTranscript}
                onChange={(e) => setSectionTranscript(e.target.value)}
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </div>
          </>
        )}

        {sectionType === "TEXT" && (
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Content (optional)</label>
            <textarea
              placeholder="Enter text content or leave empty to add later..."
              value={sectionContentRef}
              onChange={(e) => setSectionContentRef(e.target.value)}
              rows={3}
              className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
            />
          </div>
        )}

        <div className="flex gap-2">
          <button
            onClick={handleSubmit}
            className="flex-1 h-9 rounded-md bg-teal-700 hover:bg-teal-800 text-white text-xs font-semibold transition"
          >
            Create Section
          </button>
          <button
            onClick={onCancel}
            className="flex-1 h-9 rounded-md border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
