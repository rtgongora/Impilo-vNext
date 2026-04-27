/**
 * useSpeechToText — Unified voice dictation hook
 * Uses Web Speech API (browser-native) with optional server STT fallback (gated).
 * Types align with `shared-ui` dictation contracts.
 */
import { useState, useRef, useCallback, useEffect } from "react";
import type { DictationLanguage, TranscriptionResult } from "shared-ui";

export type SpeechEngine = "browser" | "elevenlabs" | "auto";

interface UseSpeechToTextOptions {
  engine?: SpeechEngine;
  language?: DictationLanguage;
  continuous?: boolean;
  interimResults?: boolean;
  onTranscript?: (text: string, isFinal: boolean) => void;
  /** Structured transcript events (platform dictation contract). */
  onTranscriptionResult?: (result: TranscriptionResult) => void;
  onError?: (error: string) => void;
  /**
   * When false (default), never starts MediaRecorder / server transcription — browser STT only.
   * Set true only where product + Mvumo consent allow raw audio to leave the device.
   */
  allowCloudStt?: boolean;
}

interface UseSpeechToTextReturn {
  isListening: boolean;
  isSupported: boolean;
  transcript: string;
  interimTranscript: string;
  startListening: () => void;
  stopListening: () => void;
  toggleListening: () => void;
  resetTranscript: () => void;
  activeEngine: "browser" | "elevenlabs" | "none";
}

function getSpeechRecognitionClass(): SpeechRecognitionConstructor | null {
  if (typeof window === "undefined") return null;
  return window.SpeechRecognition ?? window.webkitSpeechRecognition ?? null;
}

const isBrowserSpeechSupported = () => !!getSpeechRecognitionClass();

// BCP-47 to ISO 639-3 mapping for ElevenLabs
const bcp47ToIso639: Record<string, string> = {
  "en": "eng", "en-US": "eng", "en-GB": "eng",
  "es": "spa", "es-ES": "spa", "fr": "fra", "fr-FR": "fra",
  "de": "deu", "de-DE": "deu", "pt": "por", "pt-BR": "por",
  "zu": "zul", "xh": "xho", "af": "afr", "st": "sot",
};

export function useSpeechToText({
  engine = "auto",
  language = "en-US",
  continuous = true,
  interimResults = true,
  onTranscript,
  onTranscriptionResult,
  onError,
  allowCloudStt = false,
}: UseSpeechToTextOptions = {}): UseSpeechToTextReturn {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [interimTranscript, setInterimTranscript] = useState("");
  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const recordingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const activeEngineRef = useRef<"browser" | "elevenlabs" | "none">("none");

  const browserSupported = isBrowserSpeechSupported();
  const isSupported =
    browserSupported || (allowCloudStt && (engine === "auto" || engine === "elevenlabs"));

  const emitResult = useCallback(
    (text: string, isFinal: boolean) => {
      const trimmed = text.trim();
      if (!trimmed) return;
      onTranscriptionResult?.({
        transcript: trimmed,
        isFinal,
        language,
      });
    },
    [language, onTranscriptionResult],
  );

  const resolvedEngine: "browser" | "elevenlabs" | "none" =
    engine === "browser"
      ? browserSupported
        ? "browser"
        : "none"
      : engine === "elevenlabs"
        ? allowCloudStt
          ? "elevenlabs"
          : "none"
        : engine === "auto"
          ? browserSupported
            ? "browser"
            : allowCloudStt
              ? "elevenlabs"
              : "none"
          : "none";

  useEffect(() => {
    return () => {
      if (recognitionRef.current) {
        try { recognitionRef.current.abort?.(); } catch { /* noop */ }
      }
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
        try { mediaRecorderRef.current.stop(); } catch { /* noop */ }
      }
      if (recordingIntervalRef.current) clearInterval(recordingIntervalRef.current);
    };
  }, []);

  // --- ElevenLabs Scribe engine ---
  const sendChunkToScribe = useCallback(async (audioBlob: Blob) => {
    try {
      const buffer = await audioBlob.arrayBuffer();
      const base64 = btoa(String.fromCharCode(...new Uint8Array(buffer)));
      const isoLang = bcp47ToIso639[language] || "eng";

      const resp = await fetch("/api/v1/speech/transcribe", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ audio: base64, language: isoLang, mimeType: audioBlob.type }),
      });

      if (!resp.ok) {
        const err = await resp.text();
        console.error("Scribe API error:", err);
        return;
      }

      const data: unknown = await resp.json();
      const text =
        data && typeof data === "object" && "text" in data && typeof (data as { text: unknown }).text === "string"
          ? (data as { text: string }).text.trim()
          : "";
      if (text) {
        emitResult(text, true);
        setTranscript((prev) => {
          const newText = prev ? `${prev} ${text}` : text;
          onTranscript?.(newText, true);
          return newText;
        });
      }
    } catch (e) {
      console.error("Scribe chunk error:", e);
    }
  }, [language, onTranscript, emitResult]);

  const startElevenLabsRecognition = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm";
      const recorder = new MediaRecorder(stream, { mimeType });

      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };

      recorder.start();
      mediaRecorderRef.current = recorder;
      activeEngineRef.current = "elevenlabs";
      setIsListening(true);

      // Send chunks every 5 seconds for near-real-time transcription
      recordingIntervalRef.current = setInterval(() => {
        if (recorder.state === "recording" && chunksRef.current.length > 0) {
          const blob = new Blob(chunksRef.current, { type: mimeType });
          chunksRef.current = [];
          recorder.stop();
          sendChunkToScribe(blob);
          // Restart recording
          const newRecorder = new MediaRecorder(stream, { mimeType });
          newRecorder.ondataavailable = (e) => {
            if (e.data.size > 0) chunksRef.current.push(e.data);
          };
          newRecorder.start();
          mediaRecorderRef.current = newRecorder;
        }
      }, 5000);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Failed to start ElevenLabs recording";
      onError?.(msg);
    }
  }, [sendChunkToScribe, onError]);

  // --- Browser engine ---
  const startBrowserRecognition = useCallback(() => {
    const SpeechRecognitionClass = getSpeechRecognitionClass();
    if (!SpeechRecognitionClass) {
      onError?.("Speech recognition not supported in this browser");
      return;
    }

    const recognition = new SpeechRecognitionClass();
    recognition.lang = language;
    recognition.continuous = continuous;
    recognition.interimResults = interimResults;
    recognition.maxAlternatives = 1;

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let interim = "";
      let final = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          final += result[0]?.transcript ?? "";
        } else {
          interim += result[0]?.transcript ?? "";
        }
      }
      if (final) {
        const segment = final.trim();
        if (segment) {
          emitResult(segment, true);
          setTranscript((prev) => {
            const newText = prev ? prev + " " + segment : segment;
            onTranscript?.(newText, true);
            return newText;
          });
        }
        setInterimTranscript("");
      }
      if (interim) {
        const interimTrim = interim.trim();
        if (interimTrim) emitResult(interimTrim, false);
        setInterimTranscript(interim);
        onTranscript?.(interim, false);
      }
    };

    recognition.onerror = (event: Event & { error?: string }) => {
      if (event.error === "aborted" || event.error === "no-speech") return;
      onError?.(event.error || "Speech recognition error");
      setIsListening(false);
    };

    recognition.onend = () => {
      if (isListening && continuous) {
        try { recognition.start(); } catch { setIsListening(false); }
      } else {
        setIsListening(false);
      }
    };

    try {
      recognition.start();
      recognitionRef.current = recognition;
      activeEngineRef.current = "browser";
      setIsListening(true);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Failed to start speech recognition";
      onError?.(msg);
    }
  }, [language, continuous, interimResults, onTranscript, onError, isListening, emitResult]);

  const startListening = useCallback(() => {
    if (isListening) return;

    if (resolvedEngine === "elevenlabs") {
      void startElevenLabsRecognition();
      return;
    }

    if (resolvedEngine === "browser") {
      void navigator.mediaDevices
        .getUserMedia({ audio: true })
        .then(() => {
          startBrowserRecognition();
        })
        .catch(() => {
          onError?.("Microphone access is required for voice dictation");
        });
      return;
    }

    onError?.("Voice dictation is not available in this browser");
  }, [isListening, resolvedEngine, startBrowserRecognition, startElevenLabsRecognition, onError]);

  const stopListening = useCallback(() => {
    // Stop browser recognition
    if (recognitionRef.current) {
      try { recognitionRef.current.stop(); } catch { /* noop */ }
      recognitionRef.current = null;
    }
    // Stop ElevenLabs recording
    if (recordingIntervalRef.current) {
      clearInterval(recordingIntervalRef.current);
      recordingIntervalRef.current = null;
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      // Send final chunk
      const recorder = mediaRecorderRef.current;
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          sendChunkToScribe(e.data);
        }
      };
      try { recorder.stop(); } catch { /* noop */ }
      // Stop all tracks
      recorder.stream.getTracks().forEach((t) => t.stop());
      mediaRecorderRef.current = null;
    }
    activeEngineRef.current = "none";
    setIsListening(false);
    setInterimTranscript("");
  }, [sendChunkToScribe]);

  const toggleListening = useCallback(() => {
    if (isListening) {
      stopListening();
    } else {
      startListening();
    }
  }, [isListening, startListening, stopListening]);

  const resetTranscript = useCallback(() => {
    setTranscript("");
    setInterimTranscript("");
  }, []);

  return {
    isListening,
    isSupported,
    transcript,
    interimTranscript,
    startListening,
    stopListening,
    toggleListening,
    resetTranscript,
    activeEngine: activeEngineRef.current,
  };
}
