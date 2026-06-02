"use client";

import { useMemo, useState } from "react";
import { useFundoLanguageOptions, type FundoLanguageOption } from "@/hooks/queries/useFundoLms";

const COURSE_CATEGORIES = [
  { value: "GENERAL", label: "General" },
  { value: "ORIENTATION", label: "Orientation" },
  { value: "CLINICAL", label: "Clinical" },
  { value: "COMPLIANCE", label: "Compliance" },
  { value: "DIGITAL_HEALTH", label: "Digital health" },
  { value: "OPERATIONS", label: "Operations" },
];

const COURSE_LEVELS = [
  { value: "INTRODUCTORY", label: "Introductory" },
  { value: "INTERMEDIATE", label: "Intermediate" },
  { value: "ADVANCED", label: "Advanced" },
  { value: "REFRESHER", label: "Refresher" },
];

const COURSE_STATUSES = ["DRAFT", "PUBLISHED", "ARCHIVED"];

const FALLBACK_COURSE_LANGUAGES: FundoLanguageOption[] = [
  { code: "en", label: "English", nativeLabel: "English" },
  { code: "sn", label: "Shona", nativeLabel: "ChiShona" },
  { code: "nd", label: "Ndebele", nativeLabel: "isiNdebele" },
  { code: "ny", label: "Chewa / Nyanja", nativeLabel: "Chichewa / Chinyanja" },
  { code: "st", label: "Sotho", nativeLabel: "Sesotho" },
  { code: "pt", label: "Portuguese", nativeLabel: "Português" },
];

export interface FundoCourseFormValues {
  code: string;
  title: string;
  description?: string;
  category: string;
  level: string;
  language: string;
  estimatedDurationMinutes?: number;
  mandatory: boolean;
  cpdEligible: boolean;
  cpdPoints?: number;
  status: string;
}

interface FundoCourseFormProps {
  submitLabel: string;
  isSubmitting?: boolean;
  initialValues?: Partial<FundoCourseFormValues>;
  onSubmit: (values: FundoCourseFormValues) => void;
}

export function FundoCourseForm({
  submitLabel,
  isSubmitting = false,
  initialValues,
  onSubmit,
}: FundoCourseFormProps) {
  const [code, setCode] = useState(initialValues?.code ?? "");
  const [title, setTitle] = useState(initialValues?.title ?? "");
  const [description, setDescription] = useState(initialValues?.description ?? "");
  const [category, setCategory] = useState(initialValues?.category ?? "GENERAL");
  const [level, setLevel] = useState(initialValues?.level ?? "INTRODUCTORY");
  const [language, setLanguage] = useState(initialValues?.language ?? "en");
  const [languageSearch, setLanguageSearch] = useState("");
  const [languageOpen, setLanguageOpen] = useState(false);
  const [estimatedDurationMinutes, setEstimatedDurationMinutes] = useState(
    initialValues?.estimatedDurationMinutes ? String(initialValues.estimatedDurationMinutes) : "",
  );
  const [mandatory, setMandatory] = useState(Boolean(initialValues?.mandatory));
  const [cpdEligible, setCpdEligible] = useState(Boolean(initialValues?.cpdEligible));
  const [cpdPoints, setCpdPoints] = useState(initialValues?.cpdPoints ? String(initialValues.cpdPoints) : "");
  const [status, setStatus] = useState(initialValues?.status ?? "DRAFT");
  const [error, setError] = useState("");
  const { data: languageData } = useFundoLanguageOptions();
  const languages =
    languageData?.data?.items && languageData.data.items.length > 0
      ? languageData.data.items
      : FALLBACK_COURSE_LANGUAGES;
  const selectedLanguage = languages.find((option) => option.code === language) ?? languages[0];
  const languageOptions = useMemo(() => {
    const q = languageSearch.trim().toLowerCase();
    if (!q) return languages;
    return languages.filter((option) =>
      `${option.code} ${option.label} ${option.nativeLabel ?? ""}`.toLowerCase().includes(q),
    );
  }, [languageSearch, languages]);

  function submit() {
    setError("");
    if (!code.trim() || !title.trim()) {
      setError("Course code and title are required.");
      return;
    }

    onSubmit({
      code: code.trim(),
      title: title.trim(),
      description: description.trim() || undefined,
      category,
      level,
      language: language.trim() || "en",
      estimatedDurationMinutes: estimatedDurationMinutes ? Number(estimatedDurationMinutes) : undefined,
      mandatory,
      cpdEligible,
      cpdPoints: cpdEligible && cpdPoints ? Number(cpdPoints) : undefined,
      status,
    });
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="space-y-1 text-sm">
          <span className="font-medium text-gray-800">Course code</span>
          <input className="w-full rounded border border-gray-300 px-2 py-2 text-sm" value={code} onChange={(e) => setCode(e.target.value)} />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium text-gray-800">Course title</span>
          <input className="w-full rounded border border-gray-300 px-2 py-2 text-sm" value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
      </div>
      <label className="block space-y-1 text-sm">
        <span className="font-medium text-gray-800">Description</span>
        <textarea className="min-h-24 w-full rounded border border-gray-300 px-2 py-2 text-sm" value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label className="space-y-1 text-sm">
          <span className="font-medium text-gray-800">Category</span>
          <select className="w-full rounded border border-gray-300 px-2 py-2 text-sm" value={category} onChange={(e) => setCategory(e.target.value)}>
            {COURSE_CATEGORIES.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium text-gray-800">Level</span>
          <select className="w-full rounded border border-gray-300 px-2 py-2 text-sm" value={level} onChange={(e) => setLevel(e.target.value)}>
            {COURSE_LEVELS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium text-gray-800">Language</span>
          <div className="relative">
            <button
              type="button"
              onClick={() => setLanguageOpen((open) => !open)}
              className="flex w-full items-center justify-between gap-2 rounded border border-gray-300 px-2 py-2 text-left text-sm"
              aria-expanded={languageOpen}
            >
              <span className="truncate">{selectedLanguage.label} ({selectedLanguage.code})</span>
              <span className="text-xs text-gray-500">{languageOpen ? "Close" : "Change"}</span>
            </button>
            {languageOpen ? (
              <div className="absolute z-20 mt-1 w-full overflow-hidden rounded border border-gray-200 bg-white shadow-lg">
                <div className="border-b border-gray-100 p-2">
                  <input
                    className="w-full rounded border border-gray-300 px-2 py-1.5 text-sm"
                    value={languageSearch}
                    onChange={(e) => setLanguageSearch(e.target.value)}
                    placeholder="Search language or code"
                    aria-label="Search language"
                  />
                </div>
                <div className="py-1">
                  {languageOptions.map((option) => (
                    <button
                      key={option.code}
                      type="button"
                      onClick={() => {
                        setLanguage(option.code);
                        setLanguageOpen(false);
                        setLanguageSearch("");
                      }}
                      className={`block w-full px-3 py-2 text-left text-sm hover:bg-gray-50 ${
                        option.code === language ? "bg-impilo-50 font-medium text-impilo-700" : "text-gray-700"
                      }`}
                    >
                      <span className="block">{option.label} ({option.code})</span>
                      <span className="text-xs text-gray-500">{option.nativeLabel}</span>
                    </button>
                  ))}
                  {languageOptions.length === 0 ? (
                    <p className="px-3 py-2 text-sm text-gray-500">No matching language option.</p>
                  ) : null}
                </div>
              </div>
            ) : null}
          </div>
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium text-gray-800">Status</span>
          <select className="w-full rounded border border-gray-300 px-2 py-2 text-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
            {COURSE_STATUSES.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="space-y-1 text-sm">
          <span className="font-medium text-gray-800">Duration minutes</span>
          <input className="w-full rounded border border-gray-300 px-2 py-2 text-sm" type="number" min="0" value={estimatedDurationMinutes} onChange={(e) => setEstimatedDurationMinutes(e.target.value)} />
        </label>
        <label className="flex items-center gap-2 rounded border border-gray-200 px-3 py-2 text-sm text-gray-700">
          <input type="checkbox" checked={mandatory} onChange={(e) => setMandatory(e.target.checked)} />
          Mandatory
        </label>
        <label className="flex items-center gap-2 rounded border border-gray-200 px-3 py-2 text-sm text-gray-700">
          <input type="checkbox" checked={cpdEligible} onChange={(e) => setCpdEligible(e.target.checked)} />
          CPD eligible
        </label>
      </div>
      {cpdEligible ? (
        <label className="block max-w-xs space-y-1 text-sm">
          <span className="font-medium text-gray-800">CPD points</span>
          <input className="w-full rounded border border-gray-300 px-2 py-2 text-sm" type="number" min="0" value={cpdPoints} onChange={(e) => setCpdPoints(e.target.value)} />
        </label>
      ) : null}
      {error ? <p className="text-xs text-rose-700">{error}</p> : null}
      <button type="button" onClick={submit} disabled={isSubmitting} className="rounded bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50">
        {submitLabel}
      </button>
    </div>
  );
}
