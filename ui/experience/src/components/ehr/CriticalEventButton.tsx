"use client";

/**
 * CriticalEventButton — Rapid critical event activation.
 * Ported from Lovable (94L). Shows dialog to select event type
 * (Code Blue, Rapid Response, Resuscitation, Emergency).
 */

import { useState } from "react";
import { AlertTriangle, Zap, X } from "lucide-react";

type CriticalEventType = "code-blue" | "rapid-response" | "resuscitation" | "emergency";

const CRITICAL_EVENT_TYPES: { id: CriticalEventType; label: string; description: string }[] = [
  { id: "code-blue", label: "Code Blue", description: "Cardiac / Respiratory Arrest" },
  { id: "rapid-response", label: "Rapid Response", description: "Clinical Deterioration" },
  { id: "resuscitation", label: "Resuscitation", description: "Emergency Resuscitation" },
  { id: "emergency", label: "Emergency", description: "General Emergency" },
];

interface CriticalEventButtonProps {
  isCriticalEventActive?: boolean;
  onActivate?: (type: CriticalEventType, reason: string) => void;
}

export function CriticalEventButton({ isCriticalEventActive = false, onActivate }: CriticalEventButtonProps) {
  const [open, setOpen] = useState(false);

  const handleActivate = (type: CriticalEventType) => {
    onActivate?.(type, "Manual activation");
    setOpen(false);
  };

  if (isCriticalEventActive) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-red-600 text-white animate-pulse">
        <Zap className="w-4 h-4" />
        <span className="text-sm font-semibold">CRITICAL EVENT ACTIVE</span>
      </div>
    );
  }

  return (
    <>
      <button onClick={() => setOpen(true)}
        className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-red-50 border border-red-300 text-red-700 hover:bg-red-600 hover:text-white hover:border-red-600 transition-colors">
        <AlertTriangle className="w-4 h-4" />
        <span className="text-sm font-medium">Critical Event</span>
      </button>

      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setOpen(false)} />
          <div className="relative bg-white rounded-xl shadow-2xl w-[440px]">
            <div className="px-6 py-4 border-b flex items-center justify-between">
              <h2 className="text-base font-semibold text-red-700 flex items-center gap-2"><AlertTriangle className="w-5 h-5" /> Activate Critical Event</h2>
              <button onClick={() => setOpen(false)} className="p-1 rounded hover:bg-gray-100"><X className="w-4 h-4" /></button>
            </div>
            <p className="px-6 pt-3 text-sm text-gray-500">Select the type of critical event to activate emergency protocols.</p>
            <div className="p-6 space-y-3">
              {CRITICAL_EVENT_TYPES.map(event => (
                <button key={event.id} onClick={() => handleActivate(event.id)}
                  className="w-full flex items-start gap-3 p-4 border-2 rounded-lg text-left hover:border-red-400 hover:bg-red-50 transition-colors">
                  <Zap className="w-5 h-5 text-red-500 mt-0.5 shrink-0" />
                  <div><p className="font-semibold text-gray-900">{event.label}</p><p className="text-sm text-gray-500">{event.description}</p></div>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
