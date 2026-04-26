"use client";

/**
 * DictationButton — A mic toggle button for voice dictation.
 * Appends transcribed speech to the target value via onChange callback.
 */
import { useCallback, useEffect, useRef } from "react";
import { Mic, MicOff } from "lucide-react";
import { useSpeechToText } from "@/hooks/useSpeechToText";

interface DictationButtonProps {
  /** Current text value of the target field */
  value: string;
  /** Called with the updated text (existing + dictated) */
  onValueChange: (newValue: string) => void;
  /** Language for recognition (BCP-47) */
  language?: string;
  /** Size variant */
  size?: "sm" | "default" | "icon";
  /** Additional className */
  className?: string;
  /** Disabled */
  disabled?: boolean;
}

export function DictationButton({
  value,
  onValueChange,
  language = "en-US",
  size = "icon",
  className,
  disabled = false,
}: DictationButtonProps) {
  const baseValueRef = useRef(value);

  const {
    isListening,
    isSupported,
    transcript,
    interimTranscript,
    toggleListening,
    stopListening,
    resetTranscript,
  } = useSpeechToText({
    language,
    continuous: true,
    interimResults: true,
  });

  // Capture the base value when dictation starts
  useEffect(() => {
    if (isListening && transcript === "" && interimTranscript === "") {
      baseValueRef.current = value;
    }
  }, [isListening]);

  // Append finalized transcript to value
  useEffect(() => {
    if (transcript) {
      const separator = baseValueRef.current && !baseValueRef.current.endsWith(" ") ? " " : "";
      onValueChange(baseValueRef.current + separator + transcript);
    }
  }, [transcript]);

  const handleToggle = useCallback(() => {
    if (isListening) {
      stopListening();
      resetTranscript();
    } else {
      baseValueRef.current = value;
      resetTranscript();
      toggleListening();
    }
  }, [isListening, value, stopListening, resetTranscript, toggleListening]);

  if (!isSupported) return null;

  const sizeClasses = size === "sm" ? "h-7 w-7" : size === "icon" ? "h-8 w-8" : "h-9 w-9";

  return (
    <div className="relative group" title={isListening ? "Stop dictation" : "Voice dictation"}>
      <button
        type="button"
        className={`relative inline-flex items-center justify-center rounded-md transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 ${sizeClasses} ${
          isListening
            ? "bg-red-600 text-white hover:bg-red-700"
            : "hover:bg-gray-100 text-gray-600"
        } ${className || ""}`}
        onClick={handleToggle}
        disabled={disabled}
      >
        {isListening ? (
          <>
            <MicOff className="h-4 w-4" />
            {/* Pulsing indicator */}
            <span className="absolute -top-0.5 -right-0.5 flex h-2.5 w-2.5">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-500 opacity-75" />
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500" />
            </span>
          </>
        ) : (
          <Mic className="h-4 w-4" />
        )}
      </button>
    </div>
  );
}
