"use client";

import { useState } from "react";
import { AlertCircle, Loader2, X, Plus, ChevronDown } from "lucide-react";
import { type Row } from "@/components/learning/learningUtils";
import { FileUploadField } from "./FileUploadField";
import { apiClient } from "@/lib/api-client";

const FUNDO = "/internal/v1/learning/fundo";

export interface QuizQuestion {
  id: string;
  type: "TRUE_FALSE" | "SINGLE_SELECT" | "MULTIPLE_SELECT";
  prompt: string;
  options: string[];
  correctAnswers: string[];
}

export function SectionFormComponent({
  onCancel,
  onSubmit,
  courseId,
  sequenceNo,
  initialData,
}: {
  onCancel: () => void;
  onSubmit: (section: Row) => void;
  courseId: string;
  sequenceNo: number;
  initialData?: Row;
}) {
  const [sectionType, setSectionType] = useState<string>(
    (initialData?.contentType as string) || (initialData?.type as string) || ""
  );
  const [sectionTitle, setSectionTitle] = useState<string>(
    (initialData?.title as string) || (initialData?.name as string) || ""
  );
  const [sectionContentRef, setSectionContentRef] = useState<string>(
    (initialData?.contentRef as string) || (initialData?.contentBody as string) || ""
  );
  const [sectionTranscript, setSectionTranscript] = useState<string>(
    (initialData?.transcript as string) || ""
  );
  const [error, setError] = useState<string | null>(null);
  const [quizQuestions, setQuizQuestions] = useState<QuizQuestion[]>(() => {
    if (initialData?.questions) {
      return initialData.questions as QuizQuestion[];
    }
    if (initialData?.contentBlocksJson) {
      try {
        const parsed = JSON.parse(initialData.contentBlocksJson as string);
        return parsed.questions || [];
      } catch {
        return [];
      }
    }
    return [];
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [expandedQuestion, setExpandedQuestion] = useState<string | null>(null);

  const handleAddQuestion = () => {
    const newQuestion: QuizQuestion = {
      id: `q-${Date.now()}`,
      type: "SINGLE_SELECT",
      prompt: "",
      options: [""],
      correctAnswers: [],
    };
    setQuizQuestions([...quizQuestions, newQuestion]);
    setExpandedQuestion(newQuestion.id);
  };

  const handleRemoveQuestion = (id: string) => {
    setQuizQuestions(quizQuestions.filter((q) => q.id !== id));
    if (expandedQuestion === id) setExpandedQuestion(null);
  };

  const handleQuestionChange = (id: string, field: string, value: unknown) => {
    setQuizQuestions(
      quizQuestions.map((q) =>
        q.id === id ? { ...q, [field]: value } : q
      )
    );
  };

  const handleOptionChange = (questionId: string, optionIndex: number, value: string) => {
    setQuizQuestions(
      quizQuestions.map((q) =>
        q.id === questionId
          ? { ...q, options: q.options.map((opt, idx) => (idx === optionIndex ? value : opt)) }
          : q
      )
    );
  };

  const handleAddOption = (questionId: string) => {
    setQuizQuestions(
      quizQuestions.map((q) =>
        q.id === questionId ? { ...q, options: [...q.options, ""] } : q
      )
    );
  };

  const handleRemoveOption = (questionId: string, optionIndex: number) => {
    setQuizQuestions(
      quizQuestions.map((q) =>
        q.id === questionId
          ? {
              ...q,
              options: q.options.filter((_, idx) => idx !== optionIndex),
              correctAnswers: q.correctAnswers.filter((_, idx) => idx !== optionIndex),
            }
          : q
      )
    );
  };

  const toggleCorrectAnswer = (questionId: string, optionIndex: number) => {
    setQuizQuestions(
      quizQuestions.map((q) => {
        if (q.id !== questionId) return q;

        const optionValue = String(optionIndex);
        let newCorrectAnswers = [...q.correctAnswers];

        if (q.type === "TRUE_FALSE" || q.type === "SINGLE_SELECT") {
          // Single select: only one correct answer
          newCorrectAnswers = [optionValue];
        } else {
          // Multiple select: toggle
          if (newCorrectAnswers.includes(optionValue)) {
            newCorrectAnswers = newCorrectAnswers.filter((a) => a !== optionValue);
          } else {
            newCorrectAnswers.push(optionValue);
          }
        }

        return { ...q, correctAnswers: newCorrectAnswers };
      })
    );
  };

  const handleSubmit = async () => {
    setError(null);

    if (!sectionType || !sectionTitle) {
      setError("Please fill in required fields: Section Type and Title");
      return;
    }

    // Validate file-based sections have content reference
    if ((sectionType === "VIDEO" || sectionType === "DOCUMENT" || sectionType === "LINK") && !sectionContentRef) {
      setError(`Please upload or provide content reference for ${sectionType.toLowerCase()}`);
      return;
    }

    // Validate quiz sections have questions with correct answers
    if (sectionType === "INTERACTIVE") {
      if (quizQuestions.length === 0) {
        setError("Quiz must have at least one question");
        return;
      }
      for (const q of quizQuestions) {
        if (!q.prompt.trim()) {
          setError("All questions must have a prompt/text");
          return;
        }
        if (q.options.some((o) => !o.trim())) {
          setError("All question options must be filled");
          return;
        }
        if (q.correctAnswers.length === 0) {
          setError("Each question must have at least one correct answer selected");
          return;
        }
      }
    }

    setIsSubmitting(true);

    try {
      // Prepare payload for backend
      const lessonPayload: Row = {
        title: sectionTitle,
        contentType: sectionType,
        contentBody: sectionType === "TEXT" ? sectionContentRef : null,
        contentRef: sectionType !== "TEXT" ? sectionContentRef : null,
        contentFormat: sectionType === "TEXT" ? "PLAIN_TEXT" : null,
        sequence: sequenceNo,
        required: true,
        status: "DRAFT",
      };

      // For interactive sections, include questions
      if (sectionType === "INTERACTIVE") {
        lessonPayload.contentBlocksJson = JSON.stringify({
          title: sectionTitle,
          description: sectionTranscript,
          questions: quizQuestions.map((q) => ({
            type: q.type,
            prompt: q.prompt,
            options: q.options,
            correctAnswers: q.correctAnswers.map((idx) => q.options[parseInt(idx)]),
          })),
        });
      } else if (sectionTranscript) {
        lessonPayload.contentBlocksJson = JSON.stringify({
          transcript: sectionTranscript,
        });
      }

      // Create the section data to pass to parent component
      // Parent will handle the backend API call to POST /modules/{moduleId}/lessons
      const newSection: Row = {
        id: `section-${Date.now()}`,
        title: sectionTitle,
        name: sectionTitle,
        contentType: sectionType,
        type: sectionType,
        contentRef: sectionContentRef,
        contentBody: lessonPayload.contentBody,
        contentFormat: lessonPayload.contentFormat,
        contentBlocksJson: lessonPayload.contentBlocksJson,
        transcript: sectionTranscript,
        courseId: courseId,
        sequenceNo: sequenceNo,
        status: "DRAFT",
        required: true,
        questions: sectionType === "INTERACTIVE" ? quizQuestions : undefined,
      };

      onSubmit(newSection);

      // Reset form
      setSectionType("");
      setSectionTitle("");
      setSectionContentRef("");
      setSectionTranscript("");
      setQuizQuestions([]);
      setExpandedQuestion(null);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "Failed to create section";
      setError(errorMessage);
      console.error("Section creation error:", err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mb-4 p-4 rounded-lg border border-teal-200 bg-teal-50">
      <h3 className="font-semibold text-slate-950 mb-3">{initialData ? "Edit Section" : "Create New Section"}</h3>

      {error && (
        <div className="mb-3 flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3">
          <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" />
          <span className="text-xs text-red-700">{error}</span>
        </div>
      )}

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
                value={sectionTitle}
                disabled
                className="w-full rounded-md border border-slate-200 px-3 py-2 text-sm outline-none bg-slate-100 text-slate-500 cursor-not-allowed"
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-xs font-medium text-slate-600">Questions *</label>
                <span className="text-xs text-slate-500">{quizQuestions.length} question(s)</span>
              </div>

              <div className="space-y-2 mb-2">
                {quizQuestions.length > 0 ? (
                  quizQuestions.map((question, idx) => (
                    <div key={question.id} className="rounded-md border border-slate-200 bg-white overflow-hidden">
                      {/* Question Header */}
                      <button
                        type="button"
                        onClick={() =>
                          setExpandedQuestion(expandedQuestion === question.id ? null : question.id)
                        }
                        className="w-full flex items-center justify-between gap-2 p-3 hover:bg-slate-50 transition"
                      >
                        <div className="flex items-center gap-2 min-w-0 text-left">
                          <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-teal-100 text-teal-700 text-xs font-bold shrink-0">
                            {idx + 1}
                          </span>
                          <div className="min-w-0">
                            <p className="text-xs font-medium text-slate-900 truncate">
                              {question.prompt || "New question"}
                            </p>
                            <p className="text-xs text-slate-500">
                              {question.type === "TRUE_FALSE"
                                ? "True/False"
                                : question.type === "SINGLE_SELECT"
                                  ? "Single Select"
                                  : "Multiple Select"}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <ChevronDown
                            className={`h-4 w-4 text-slate-400 transition ${
                              expandedQuestion === question.id ? "rotate-180" : ""
                            }`}
                          />
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleRemoveQuestion(question.id);
                            }}
                            className="p-1 rounded hover:bg-red-100 text-red-600"
                            title="Delete question"
                          >
                            <X className="h-4 w-4" />
                          </button>
                        </div>
                      </button>

                      {/* Question Details */}
                      {expandedQuestion === question.id && (
                        <div className="border-t border-slate-200 p-3 space-y-3 bg-slate-50">
                          <div>
                            <label className="block text-xs font-medium text-slate-600 mb-1">
                              Question Type
                            </label>
                            <select
                              value={question.type}
                              onChange={(e) =>
                                handleQuestionChange(
                                  question.id,
                                  "type",
                                  e.target.value as QuizQuestion["type"]
                                )
                              }
                              className="w-full rounded-md border border-slate-200 px-2 py-1 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                            >
                              <option value="TRUE_FALSE">True/False</option>
                              <option value="SINGLE_SELECT">Single Select</option>
                              <option value="MULTIPLE_SELECT">Multiple Select</option>
                            </select>
                          </div>

                          <div>
                            <label className="block text-xs font-medium text-slate-600 mb-1">
                              Question Text *
                            </label>
                            <textarea
                              value={question.prompt}
                              onChange={(e) =>
                                handleQuestionChange(question.id, "prompt", e.target.value)
                              }
                              placeholder="Enter the question..."
                              rows={2}
                              className="w-full rounded-md border border-slate-200 px-2 py-1 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                            />
                          </div>

                          <div>
                            <div className="flex items-center justify-between mb-2">
                              <label className="block text-xs font-medium text-slate-600">
                                Options *
                              </label>
                              {question.type !== "TRUE_FALSE" && (
                                <button
                                  type="button"
                                  onClick={() => handleAddOption(question.id)}
                                  className="text-xs text-teal-600 hover:text-teal-700 font-medium inline-flex items-center gap-1"
                                >
                                  <Plus className="h-3 w-3" />
                                  Add Option
                                </button>
                              )}
                            </div>

                            <div className="space-y-2">
                              {question.options.map((option, optIdx) => {
                                const isCorrect = question.correctAnswers.includes(String(optIdx));
                                return (
                                  <div
                                    key={optIdx}
                                    className={`flex items-center gap-2 rounded-md border p-2 transition ${
                                      isCorrect
                                        ? "border-emerald-300 bg-emerald-50"
                                        : "border-slate-200 bg-white"
                                    }`}
                                  >
                                    <input
                                      type="checkbox"
                                      checked={isCorrect}
                                      onChange={() =>
                                        toggleCorrectAnswer(question.id, optIdx)
                                      }
                                      className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer"
                                      title="Mark as correct answer"
                                    />
                                    <input
                                      type="text"
                                      value={option}
                                      onChange={(e) =>
                                        handleOptionChange(question.id, optIdx, e.target.value)
                                      }
                                      placeholder={
                                        question.type === "TRUE_FALSE"
                                          ? optIdx === 0
                                            ? "True"
                                            : "False"
                                          : `Option ${optIdx + 1}`
                                      }
                                      disabled={
                                        question.type === "TRUE_FALSE"
                                      }
                                      className="flex-1 rounded-md border border-slate-200 px-2 py-1 text-xs outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-100 disabled:cursor-not-allowed"
                                    />
                                    {question.type !== "TRUE_FALSE" && (
                                      <button
                                        type="button"
                                        onClick={() =>
                                          handleRemoveOption(question.id, optIdx)
                                        }
                                        className="p-1 rounded hover:bg-red-100 text-red-600 shrink-0"
                                        title="Delete option"
                                      >
                                        <X className="h-3.5 w-3.5" />
                                      </button>
                                    )}
                                  </div>
                                );
                              })}
                            </div>

                            {question.correctAnswers.length === 0 && (
                              <p className="text-xs text-red-600 mt-1">⚠️ Select at least one correct answer</p>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  ))
                ) : (
                  <p className="text-xs text-slate-500 italic">No questions yet. Click "+ Add Question" to start.</p>
                )}
              </div>

              <button
                type="button"
                onClick={handleAddQuestion}
                className="w-full h-8 rounded-md bg-slate-100 text-slate-700 text-xs font-semibold hover:bg-slate-200 transition inline-flex items-center justify-center gap-1"
              >
                <Plus className="h-3.5 w-3.5" />
                Add Question
              </button>
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
            disabled={isSubmitting}
            className="flex-1 h-9 rounded-md bg-teal-700 hover:bg-teal-800 text-white text-xs font-semibold transition disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center justify-center gap-2"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                <span>{initialData ? "Updating..." : "Creating..."}</span>
              </>
            ) : (
              initialData ? "Update Section" : "Create Section"
            )}
          </button>
          <button
            onClick={onCancel}
            disabled={isSubmitting}
            className="flex-1 h-9 rounded-md border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition disabled:opacity-60 disabled:cursor-not-allowed"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
