"use client";

import { useState } from "react";
import {
  Stethoscope,
  Scissors,
  Baby,
  Brain,
  AlertTriangle,
  Bone,
  Heart,
  ChevronRight,
  GraduationCap,
} from "lucide-react";
import {
  CLERKING_TEMPLATES,
  ClerkingTemplate,
  CadreLevel,
  Specialty,
} from "@/data/clerkingTemplates";

interface ClerkingTemplateSelectorProps {
  onSelect: (template: ClerkingTemplate, cadreLevel: CadreLevel) => void;
  onCancel?: () => void;
}

const specialtyIcons: Record<Specialty, React.ComponentType<{ className?: string }>> = {
  'general-medicine': Stethoscope,
  'surgery': Scissors,
  'obstetrics-gynecology': Baby,
  'pediatrics': Baby,
  'psychiatry': Brain,
  'emergency': AlertTriangle,
  'orthopedics': Bone,
  'cardiology': Heart,
  'neurology': Brain,
};

const specialtyColors: Record<Specialty, string> = {
  'general-medicine': 'bg-primary/10 text-primary border-impilo-400/30',
  'surgery': 'bg-red-500/10 text-red-600 border-red-500/30',
  'obstetrics-gynecology': 'bg-pink-500/10 text-pink-600 border-pink-500/30',
  'pediatrics': 'bg-green-500/10 text-green-600 border-green-500/30',
  'psychiatry': 'bg-purple-500/10 text-purple-600 border-purple-500/30',
  'emergency': 'bg-orange-500/10 text-orange-600 border-orange-500/30',
  'orthopedics': 'bg-amber-500/10 text-amber-600 border-amber-500/30',
  'cardiology': 'bg-rose-500/10 text-rose-600 border-rose-500/30',
  'neurology': 'bg-indigo-500/10 text-indigo-600 border-indigo-500/30',
};

const CADRE_LEVELS: { value: CadreLevel; label: string; description: string }[] = [
  { value: 'student', label: 'Medical Student', description: 'Full clerking with guided prompts' },
  { value: 'intern', label: 'Intern', description: 'Complete assessment with supervision notes' },
  { value: 'registrar', label: 'Registrar', description: 'Full access including sensitive fields' },
  { value: 'consultant', label: 'Consultant', description: 'Complete access with all fields' },
];

export function ClerkingTemplateSelector({ onSelect, onCancel }: ClerkingTemplateSelectorProps) {
  const [selectedTemplate, setSelectedTemplate] = useState<ClerkingTemplate | null>(null);
  const [selectedCadre, setSelectedCadre] = useState<CadreLevel>('intern');
  const [step, setStep] = useState<'template' | 'cadre'>('template');

  const handleTemplateSelect = (template: ClerkingTemplate) => {
    setSelectedTemplate(template);
    setStep('cadre');
  };

  const handleConfirm = () => {
    if (selectedTemplate) {
      onSelect(selectedTemplate, selectedCadre);
    }
  };

  if (step === 'cadre' && selectedTemplate) {
    return (
      <div className="space-y-6 p-6">
        <div className="flex items-center gap-4">
          <button
            type="button"
            className="px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground hover:bg-neutral-100 rounded-md transition-colors"
            onClick={() => setStep('template')}
          >
            &larr; Back
          </button>
          <div>
            <h2 className="text-lg font-semibold">Select Your Role</h2>
            <p className="text-sm text-muted-foreground">
              Choose your clinical role for {selectedTemplate.name}
            </p>
          </div>
        </div>

        <div className="space-y-3">
          {CADRE_LEVELS.map((cadre) => (
            <div
              key={cadre.value}
              className={`flex items-center space-x-3 p-4 rounded-lg border-2 cursor-pointer transition-all ${
                selectedCadre === cadre.value
                  ? 'border-impilo-500 bg-primary-soft'
                  : 'border-border hover:border-primary/25'
              }`}
              onClick={() => setSelectedCadre(cadre.value)}
            >
              <input
                type="radio"
                name="cadreLevel"
                value={cadre.value}
                checked={selectedCadre === cadre.value}
                onChange={() => setSelectedCadre(cadre.value)}
                className="h-4 w-4 text-primary focus:ring-primary/40"
              />
              <div className="flex-1">
                <label
                  htmlFor={cadre.value}
                  className="text-base font-medium cursor-pointer"
                >
                  {cadre.label}
                </label>
                <p className="text-sm text-muted-foreground">{cadre.description}</p>
              </div>
              <GraduationCap className="w-5 h-5 text-muted-foreground" />
            </div>
          ))}
        </div>

        <div className="flex justify-end gap-3">
          {onCancel && (
            <button
              type="button"
              className="px-4 py-2 text-sm font-medium border border-border rounded-md text-foreground hover:bg-background transition-colors"
              onClick={onCancel}
            >
              Cancel
            </button>
          )}
          <button
            type="button"
            className="inline-flex items-center px-4 py-2 text-sm font-medium text-white bg-primary rounded-md hover:bg-primary-hover transition-colors"
            onClick={handleConfirm}
          >
            Start Clerking
            <ChevronRight className="w-4 h-4 ml-2" />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div>
        <h2 className="text-lg font-semibold">Select Clerking Template</h2>
        <p className="text-sm text-muted-foreground">
          Choose the appropriate template for this patient encounter
        </p>
      </div>

      <div className="max-h-[500px] overflow-y-auto">
        <div className="grid grid-cols-2 gap-4 pr-4">
          {CLERKING_TEMPLATES.map((template) => {
            const Icon = specialtyIcons[template.specialty];
            const colorClass = specialtyColors[template.specialty];

            return (
              <div
                key={template.id}
                className={`cursor-pointer rounded-lg border bg-card p-0 transition-all hover:border-impilo-500 hover:shadow-md ${
                  selectedTemplate?.id === template.id
                    ? 'border-impilo-500 ring-2 ring-impilo-500/20'
                    : 'border-border'
                }`}
                onClick={() => handleTemplateSelect(template)}
              >
                <div className="p-4 pb-3">
                  <div className="flex items-center gap-3">
                    <div className={`p-3 rounded-lg border ${colorClass}`}>
                      <Icon className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="text-base font-semibold">{template.name}</h3>
                      <p className="text-xs text-muted-foreground">
                        {template.sections.length} sections
                      </p>
                    </div>
                  </div>
                </div>
                <div className="px-4 pb-4">
                  <p className="text-sm text-muted-foreground mb-3">
                    {template.description}
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {template.sections.slice(0, 4).map((section) => (
                      <span
                        key={section.id}
                        className="inline-flex items-center rounded-md bg-neutral-100 px-2 py-0.5 text-xs font-medium text-foreground"
                      >
                        {section.title}
                      </span>
                    ))}
                    {template.sections.length > 4 && (
                      <span className="inline-flex items-center rounded-md border border-border px-2 py-0.5 text-xs font-medium text-muted-foreground">
                        +{template.sections.length - 4} more
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="flex justify-end gap-3">
        {onCancel && (
          <button
            type="button"
            className="px-4 py-2 text-sm font-medium border border-border rounded-md text-foreground hover:bg-background transition-colors"
            onClick={onCancel}
          >
            Cancel
          </button>
        )}
      </div>
    </div>
  );
}
