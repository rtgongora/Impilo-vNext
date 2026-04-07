"use client";

import { useState } from "react";
import {
  Save,
  FileText,
  CheckCircle2,
  AlertCircle,
  User,
  MessageSquare,
  History,
  Pill,
  Users,
  Home,
  ClipboardList,
  Stethoscope,
  Heart,
  Wind,
  Activity,
  Brain,
  Baby,
  Scissors,
  HeartPulse,
  Bone,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import {
  ClerkingTemplate,
  ClerkingSection,
  ClerkingField,
  CadreLevel,
  filterSectionsByRole,
} from "@/data/clerkingTemplates";

interface ClerkingFormEditorProps {
  template: ClerkingTemplate;
  cadreLevel: CadreLevel;
  encounterId?: string;
  onSave?: (data: Record<string, any>) => void;
  onSign?: (data: Record<string, any>) => void;
  initialData?: Record<string, any>;
}

const iconMap: Record<string, any> = {
  User,
  MessageSquare,
  FileText,
  History,
  Pill,
  Users,
  Home,
  ClipboardList,
  Stethoscope,
  Heart,
  Wind,
  Activity,
  Brain,
  Baby,
  Scissors,
  HeartPulse,
  Bone,
};

export function ClerkingFormEditor({
  template,
  cadreLevel,
  encounterId,
  onSave,
  onSign,
  initialData = {},
}: ClerkingFormEditorProps) {
  const [formData, setFormData] = useState<Record<string, any>>(initialData);
  const [isSaving, setIsSaving] = useState(false);
  const [lastSaved, setLastSaved] = useState<Date | null>(null);
  const [expandedSections, setExpandedSections] = useState<string[]>([
    template.sections[0]?.id,
  ]);
  const [feedback, setFeedback] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  // Filter sections based on cadre level
  const filteredSections = filterSectionsByRole(template.sections, cadreLevel);

  // Calculate completion percentage
  const calculateCompletion = () => {
    let totalRequired = 0;
    let completedRequired = 0;

    filteredSections.forEach((section) => {
      section.fields.forEach((field) => {
        if (field.required) {
          totalRequired++;
          const value = formData[field.id];
          if (value && (typeof value === "string" ? value.trim() : true)) {
            completedRequired++;
          }
        }
      });
    });

    return totalRequired > 0
      ? Math.round((completedRequired / totalRequired) * 100)
      : 0;
  };

  const completion = calculateCompletion();

  const handleFieldChange = (fieldId: string, value: any) => {
    setFormData((prev) => ({ ...prev, [fieldId]: value }));
  };

  const handleCheckboxGroupChange = (
    fieldId: string,
    option: string,
    checked: boolean
  ) => {
    setFormData((prev) => {
      const currentValues = prev[fieldId] || [];
      if (checked) {
        return { ...prev, [fieldId]: [...currentValues, option] };
      } else {
        return {
          ...prev,
          [fieldId]: currentValues.filter((v: string) => v !== option),
        };
      }
    });
  };

  const showFeedback = (type: "success" | "error", message: string) => {
    setFeedback({ type, message });
    setTimeout(() => setFeedback(null), 3000);
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      onSave?.(formData);
      setLastSaved(new Date());
      showFeedback("success", "Clerking saved successfully");
    } catch (error) {
      showFeedback("error", "Failed to save clerking");
    } finally {
      setIsSaving(false);
    }
  };

  const handleSign = async () => {
    if (completion < 100) {
      showFeedback("error", "Please complete all required fields before signing");
      return;
    }
    onSign?.(formData);
    showFeedback("success", "Clerking signed successfully");
  };

  const toggleSection = (sectionId: string) => {
    setExpandedSections((prev) =>
      prev.includes(sectionId)
        ? prev.filter((id) => id !== sectionId)
        : [...prev, sectionId]
    );
  };

  const formatTime = (date: Date) => {
    return date.toLocaleTimeString("en-GB", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  };

  const renderField = (field: ClerkingField) => {
    const value = formData[field.id];

    switch (field.type) {
      case "text":
        return (
          <input
            id={field.id}
            type="text"
            value={value || ""}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
            placeholder={field.placeholder}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        );

      case "textarea":
        return (
          <textarea
            id={field.id}
            value={value || ""}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
            placeholder={field.placeholder}
            className="w-full min-h-[100px] rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        );

      case "number":
        return (
          <input
            id={field.id}
            type="number"
            value={value || ""}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
            placeholder={field.placeholder}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        );

      case "date":
        return (
          <input
            id={field.id}
            type="date"
            value={value || ""}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        );

      case "checkbox":
        return (
          <div className="flex items-center space-x-2">
            <input
              id={field.id}
              type="checkbox"
              checked={value || false}
              onChange={(e) => handleFieldChange(field.id, e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            <label htmlFor={field.id} className="text-sm cursor-pointer">
              {field.label}
            </label>
          </div>
        );

      case "select":
        return (
          <select
            id={field.id}
            value={value || ""}
            onChange={(e) => handleFieldChange(field.id, e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="">Select {field.label.toLowerCase()}</option>
            {field.options?.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        );

      case "checkbox-group":
        return (
          <div className="grid grid-cols-2 gap-2">
            {field.options?.map((option) => (
              <div key={option} className="flex items-center space-x-2">
                <input
                  id={`${field.id}-${option}`}
                  type="checkbox"
                  checked={(value || []).includes(option)}
                  onChange={(e) =>
                    handleCheckboxGroupChange(
                      field.id,
                      option,
                      e.target.checked
                    )
                  }
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <label
                  htmlFor={`${field.id}-${option}`}
                  className="text-sm cursor-pointer"
                >
                  {option}
                </label>
              </div>
            ))}
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="h-full flex flex-col">
      {/* Feedback banner */}
      {feedback && (
        <div
          className={`px-4 py-2 text-sm font-medium ${
            feedback.type === "success"
              ? "bg-green-50 text-green-800 border-b border-green-200"
              : "bg-red-50 text-red-800 border-b border-red-200"
          }`}
        >
          {feedback.message}
        </div>
      )}

      {/* Header */}
      <div className="p-4 border-b bg-white flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">{template.name}</h2>
          <p className="text-sm text-gray-500">{template.description}</p>
        </div>
        <div className="flex items-center gap-4">
          <div className="text-right">
            <div className="flex items-center gap-2">
              <div className="w-32 h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-blue-600 rounded-full transition-all"
                  style={{ width: `${completion}%` }}
                />
              </div>
              <span className="text-sm font-medium">{completion}%</span>
            </div>
            {lastSaved && (
              <p className="text-xs text-gray-500">
                Last saved: {formatTime(lastSaved)}
              </p>
            )}
          </div>
          <button
            type="button"
            className="inline-flex items-center px-4 py-2 text-sm font-medium border border-gray-300 rounded-md text-gray-700 bg-white hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            onClick={handleSave}
            disabled={isSaving}
          >
            <Save className="w-4 h-4 mr-2" />
            Save Draft
          </button>
          <button
            type="button"
            className="inline-flex items-center px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            onClick={handleSign}
            disabled={completion < 100}
          >
            <CheckCircle2 className="w-4 h-4 mr-2" />
            Sign &amp; Complete
          </button>
        </div>
      </div>

      {/* Form Content */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="space-y-4">
          {filteredSections.map((section) => {
            const Icon = iconMap[section.icon] || FileText;
            const sectionFields = section.fields;
            const hasRequiredFields = sectionFields.some((f) => f.required);
            const allRequiredFilled = sectionFields
              .filter((f) => f.required)
              .every((f) => {
                const val = formData[f.id];
                return val && (typeof val === "string" ? val.trim() : true);
              });
            const isExpanded = expandedSections.includes(section.id);

            return (
              <div
                key={section.id}
                className="border border-gray-200 rounded-lg"
              >
                <button
                  type="button"
                  className="w-full flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors rounded-t-lg"
                  onClick={() => toggleSection(section.id)}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`p-2 rounded-lg ${
                        hasRequiredFields && !allRequiredFilled
                          ? "bg-amber-100 text-amber-600"
                          : "bg-blue-100 text-blue-600"
                      }`}
                    >
                      <Icon className="w-4 h-4" />
                    </div>
                    <span className="font-medium">{section.title}</span>
                    {hasRequiredFields && allRequiredFilled && (
                      <span className="inline-flex items-center rounded-md border border-green-300 bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">
                        <CheckCircle2 className="w-3 h-3 mr-1" />
                        Complete
                      </span>
                    )}
                    {hasRequiredFields && !allRequiredFilled && (
                      <span className="inline-flex items-center rounded-md border border-amber-300 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
                        <AlertCircle className="w-3 h-3 mr-1" />
                        Required
                      </span>
                    )}
                  </div>
                  {isExpanded ? (
                    <ChevronDown className="w-4 h-4 text-gray-500" />
                  ) : (
                    <ChevronRight className="w-4 h-4 text-gray-500" />
                  )}
                </button>

                {isExpanded && (
                  <div className="px-4 pb-4">
                    <div className="space-y-4 pt-2">
                      {sectionFields.map((field) => {
                        if (field.type === "checkbox") {
                          return (
                            <div key={field.id}>{renderField(field)}</div>
                          );
                        }

                        return (
                          <div key={field.id} className="space-y-2">
                            <label
                              htmlFor={field.id}
                              className="flex items-center gap-1 text-sm font-medium"
                            >
                              {field.label}
                              {field.required && (
                                <span className="text-red-500">*</span>
                              )}
                            </label>
                            {renderField(field)}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
