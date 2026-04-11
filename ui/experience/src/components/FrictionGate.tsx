"use client";

/**
 * FrictionGate — Health OS §8 (Graduated Trust and Friction)
 *
 * Wraps actions that require elevated or maximum friction. Shows a
 * confirmation dialog proportionate to the friction level before
 * allowing the action to proceed.
 *
 * Usage:
 *   <FrictionGate level="ELEVATED" onConfirm={handleSubmit}>
 *     <button>Submit Clinical Note</button>
 *   </FrictionGate>
 */

import { type ReactNode, useState } from "react";
import { ShieldAlert, ShieldCheck, AlertTriangle } from "lucide-react";
import type { FrictionLevel } from "@/hooks/useFrictionLevel";
import { frictionUiHints } from "@/hooks/useFrictionLevel";

interface FrictionGateProps {
  /** Required friction level for this action. */
  level: FrictionLevel;
  /** Called when user confirms through the friction gate. */
  onConfirm: () => void;
  /** Action description shown in the confirmation dialog. */
  actionDescription?: string;
  children: ReactNode;
}

export function FrictionGate({ level, onConfirm, actionDescription, children }: FrictionGateProps) {
  const [showConfirm, setShowConfirm] = useState(false);
  const [pin, setPin] = useState("");
  const hints = frictionUiHints(level);

  // MINIMAL and LOW — no gate, pass through directly
  if (level === "MINIMAL" || level === "LOW" || level === "STANDARD") {
    return <div onClick={onConfirm}>{children}</div>;
  }

  function handleClick() {
    setShowConfirm(true);
  }

  function handleConfirm() {
    setShowConfirm(false);
    setPin("");
    onConfirm();
  }

  return (
    <>
      <div onClick={handleClick} className="cursor-pointer">{children}</div>

      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-6 shadow-xl">
            <div className="flex items-center gap-3 mb-4">
              {level === "MAXIMUM" ? (
                <ShieldAlert className="h-6 w-6 text-red-500" />
              ) : (
                <AlertTriangle className="h-6 w-6 text-amber-500" />
              )}
              <div>
                <h3 className="font-semibold text-gray-900">
                  {level === "MAXIMUM" ? "Maximum Assurance Required" : "Confirm Action"}
                </h3>
                <p className="text-xs text-gray-500">{hints.label}</p>
              </div>
            </div>

            <p className="text-sm text-gray-700 mb-4">
              {actionDescription ?? "This action requires elevated trust verification. Please confirm to proceed."}
            </p>

            {hints.requirePin && (
              <div className="mb-4">
                <label className="block text-xs font-medium text-gray-700 mb-1">
                  Enter PIN or password to confirm
                </label>
                <input
                  type="password"
                  value={pin}
                  onChange={(e) => setPin(e.target.value)}
                  placeholder="PIN"
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-400 focus:ring-1 focus:ring-blue-200 focus:outline-none"
                  autoFocus
                />
              </div>
            )}

            <div className="flex gap-3 justify-end">
              <button
                onClick={() => { setShowConfirm(false); setPin(""); }}
                className="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirm}
                disabled={hints.requirePin && pin.length < 4}
                className={`px-4 py-2 text-sm font-medium text-white rounded-lg disabled:opacity-50 ${
                  level === "MAXIMUM" ? "bg-red-600 hover:bg-red-700" : "bg-amber-600 hover:bg-amber-700"
                }`}
              >
                <ShieldCheck className="h-4 w-4 inline mr-1" />
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
