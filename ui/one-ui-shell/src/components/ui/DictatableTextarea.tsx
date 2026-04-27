"use client";

/**
 * DictatableTextarea — Drop-in replacement for <textarea> with built-in voice dictation.
 * Renders a standard textarea with a mic button in the corner.
 */
import * as React from "react";
import type { DictationLanguage, TranscriptionResult } from "shared-ui";
import { DictationButton } from "@/components/ui/DictationButton";

interface DictatableTextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  /** Show dictation button (default true) */
  dictation?: boolean;
  /** Language for speech recognition */
  dictationLanguage?: DictationLanguage;
  /** Forwarded to dictation hook — server STT only when true and product consent allows. */
  dictationAllowCloudStt?: boolean;
  /** Structured transcript events for audit / Mvumo / platform hooks. */
  onDictationTranscriptionResult?: (result: TranscriptionResult) => void;
  /** Controlled value — required for dictation to work */
  value?: string;
  /** onChange handler */
  onChange?: React.ChangeEventHandler<HTMLTextAreaElement>;
  /** Direct value change handler (alternative to onChange) */
  onValueChange?: (value: string) => void;
}

const DictatableTextarea = React.forwardRef<
  HTMLTextAreaElement,
  DictatableTextareaProps
>(
  (
    {
      dictation = true,
      dictationLanguage = "en-US",
      dictationAllowCloudStt = false,
      onDictationTranscriptionResult,
      className,
      value,
      onChange,
      onValueChange,
      disabled,
      ...props
    },
    ref
  ) => {
    const handleDictationChange = React.useCallback(
      (newValue: string) => {
        if (onValueChange) {
          onValueChange(newValue);
        } else if (onChange) {
          const nativeEvent = new Event("input", { bubbles: true });
          const syntheticTarget = {
            value: newValue,
          } as HTMLTextAreaElement;
          const syntheticEvent = {
            ...nativeEvent,
            target: syntheticTarget,
            currentTarget: syntheticTarget,
          } as unknown as React.ChangeEvent<HTMLTextAreaElement>;
          onChange(syntheticEvent);
        }
      },
      [onChange, onValueChange]
    );

    return (
      <div className="relative">
        <textarea
          ref={ref}
          className={`w-full min-h-[80px] px-3 py-2 text-sm rounded-md border border-gray-300 bg-white focus:outline-none focus:ring-2 focus:ring-violet-500 focus:border-transparent resize-y disabled:opacity-50 disabled:cursor-not-allowed ${dictation ? "pr-10" : ""} ${className || ""}`}
          value={value}
          onChange={onChange}
          disabled={disabled}
          {...props}
        />
        {dictation && (
          <div className="absolute top-1.5 right-1.5">
            <DictationButton
              value={typeof value === "string" ? value : ""}
              onValueChange={handleDictationChange}
              language={dictationLanguage}
              allowCloudStt={dictationAllowCloudStt}
              onTranscriptionResult={onDictationTranscriptionResult}
              size="icon"
              className="h-7 w-7"
              disabled={disabled}
            />
          </div>
        )}
      </div>
    );
  }
);
DictatableTextarea.displayName = "DictatableTextarea";

export { DictatableTextarea };
