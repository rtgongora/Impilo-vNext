"use client";

/**
 * Dictation — Voice dictation with recording controls, transcript editor, and note type selector.
 * Route: /clinical/dictation | pageTitle: "Voice Dictation"
 */

import { useState, useRef } from "react";
import { Mic, MicOff, Pause, Play, Square, Save, Loader2, FileText, Trash2, Clock } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type RecordingState = "idle" | "recording" | "paused" | "processing";
type NoteType = "SOAP" | "Progress" | "Procedure" | "Consultation" | "Discharge";

interface RecentDictation {
  id: string;
  date: string;
  noteType: NoteType;
  duration: string;
  wordCount: number;
  status: "Saved" | "Draft" | "Processing";
  preview: string;
}

const MOCK_RECENT: RecentDictation[] = [
  { id: "d-1", date: "2026-04-06 09:15", noteType: "SOAP", duration: "3:42", wordCount: 245, status: "Saved", preview: "Patient presents with persistent lower back pain..." },
  { id: "d-2", date: "2026-04-06 08:30", noteType: "Progress", duration: "2:18", wordCount: 156, status: "Saved", preview: "Day 3 post-operative. Wound healing well..." },
  { id: "d-3", date: "2026-04-05 16:45", noteType: "Consultation", duration: "5:12", wordCount: 380, status: "Draft", preview: "Referred by Dr. Ndlovu for cardiology opinion..." },
];

const WAVEFORM_BARS = 40;

export default function DictationPage() {
  const [recordingState, setRecordingState] = useState<RecordingState>("idle");
  const [noteType, setNoteType] = useState<NoteType>("SOAP");
  const [transcript, setTranscript] = useState("");
  const [elapsed, setElapsed] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const { data } = useQuery({
    queryKey: ["recent-dictations"],
    queryFn: async () => ({ data: MOCK_RECENT }),
  });

  const recentDictations = data?.data ?? [];

  function startRecording() {
    setRecordingState("recording");
    setElapsed(0);
    timerRef.current = setInterval(() => {
      setElapsed((prev) => prev + 1);
    }, 1000);
  }

  function pauseRecording() {
    setRecordingState("paused");
    if (timerRef.current) clearInterval(timerRef.current);
  }

  function resumeRecording() {
    setRecordingState("recording");
    timerRef.current = setInterval(() => {
      setElapsed((prev) => prev + 1);
    }, 1000);
  }

  function stopRecording() {
    if (timerRef.current) clearInterval(timerRef.current);
    setRecordingState("processing");
    // Simulate processing
    setTimeout(() => {
      setTranscript("Patient presents with a two-week history of persistent cough, productive of yellowish sputum. No haemoptysis. Associated with low-grade fever and night sweats. No significant weight loss noted. Past medical history includes well-controlled hypertension on amlodipine 5mg daily. No known allergies. On examination, patient is afebrile, respiratory rate 18, SpO2 97% on room air. Chest examination reveals bilateral basal crepitations. Assessment: Community-acquired pneumonia. Plan: Chest X-ray, sputum culture, start amoxicillin 500mg TDS for 7 days. Follow up in 5 days.");
      setRecordingState("idle");
    }, 2000);
  }

  function formatTime(s: number): string {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}:${sec.toString().padStart(2, "0")}`;
  }

  // Generate pseudo-random waveform heights for visualization
  function getWaveformHeights(): number[] {
    return Array.from({ length: WAVEFORM_BARS }, (_, i) => {
      if (recordingState === "recording") {
        return 15 + Math.sin(Date.now() / 200 + i * 0.5) * 15 + Math.random() * 20;
      }
      if (recordingState === "paused") {
        return 5;
      }
      return 3;
    });
  }

  const heights = getWaveformHeights();

  return (
    <AppLayout>
      <PageShell title="Voice Dictation" subtitle="Speech-to-text for clinical notes">
        <div className="space-y-6">
          {/* Header */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Mic className="w-5 h-5 text-pink-600" />
              <h2 className="text-lg font-semibold text-gray-900">Dictation Studio</h2>
            </div>
            <div>
              <label className="text-xs font-medium text-gray-600 mr-2">Note Type:</label>
              <select
                value={noteType}
                onChange={(e) => setNoteType(e.target.value as NoteType)}
                className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option>SOAP</option>
                <option>Progress</option>
                <option>Procedure</option>
                <option>Consultation</option>
                <option>Discharge</option>
              </select>
            </div>
          </div>

          {/* Recording Panel */}
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            {/* Waveform Visualization */}
            <div className="flex items-end justify-center gap-0.5 h-16 mb-6">
              {heights.map((h, i) => (
                <div
                  key={i}
                  className={`w-1.5 rounded-full transition-all duration-150 ${
                    recordingState === "recording" ? "bg-red-400" : recordingState === "paused" ? "bg-amber-300" : "bg-gray-200"
                  }`}
                  style={{ height: `${h}%` }}
                />
              ))}
            </div>

            {/* Timer */}
            <div className="text-center mb-6">
              <span className={`text-3xl font-mono font-bold ${recordingState === "recording" ? "text-red-600" : "text-gray-700"}`}>
                {formatTime(elapsed)}
              </span>
              {recordingState === "recording" && (
                <p className="text-xs text-red-500 mt-1 animate-pulse">Recording...</p>
              )}
              {recordingState === "paused" && (
                <p className="text-xs text-amber-500 mt-1">Paused</p>
              )}
              {recordingState === "processing" && (
                <p className="text-xs text-blue-500 mt-1 flex items-center justify-center gap-1">
                  <Loader2 className="w-3 h-3 animate-spin" /> Processing transcript...
                </p>
              )}
            </div>

            {/* Controls */}
            <div className="flex items-center justify-center gap-4">
              {recordingState === "idle" && (
                <button
                  onClick={startRecording}
                  className="w-16 h-16 rounded-full bg-red-600 text-white flex items-center justify-center hover:bg-red-700 transition-colors shadow-lg"
                >
                  <Mic className="w-7 h-7" />
                </button>
              )}
              {recordingState === "recording" && (
                <>
                  <button onClick={pauseRecording} className="w-12 h-12 rounded-full bg-amber-500 text-white flex items-center justify-center hover:bg-amber-600 transition-colors">
                    <Pause className="w-5 h-5" />
                  </button>
                  <button onClick={stopRecording} className="w-16 h-16 rounded-full bg-red-600 text-white flex items-center justify-center hover:bg-red-700 transition-colors shadow-lg">
                    <Square className="w-6 h-6" />
                  </button>
                </>
              )}
              {recordingState === "paused" && (
                <>
                  <button onClick={resumeRecording} className="w-12 h-12 rounded-full bg-green-500 text-white flex items-center justify-center hover:bg-green-600 transition-colors">
                    <Play className="w-5 h-5" />
                  </button>
                  <button onClick={stopRecording} className="w-16 h-16 rounded-full bg-red-600 text-white flex items-center justify-center hover:bg-red-700 transition-colors shadow-lg">
                    <Square className="w-6 h-6" />
                  </button>
                </>
              )}
              {recordingState === "processing" && (
                <div className="w-16 h-16 rounded-full bg-gray-200 flex items-center justify-center">
                  <Loader2 className="w-7 h-7 text-gray-400 animate-spin" />
                </div>
              )}
            </div>
          </div>

          {/* Transcript Editor */}
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-medium text-gray-900">Transcript</h3>
              <span className="text-xs text-gray-400">
                {transcript ? `${transcript.split(/\s+/).length} words` : "No transcript yet"}
              </span>
            </div>
            <textarea
              value={transcript}
              onChange={(e) => setTranscript(e.target.value)}
              rows={8}
              className="w-full rounded-lg border border-gray-300 px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 leading-relaxed"
              placeholder="Transcript will appear here after recording..."
            />
            <div className="flex items-center gap-3 mt-3">
              <button
                disabled={!transcript}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                <Save className="w-4 h-4" /> Save to Notes
              </button>
              <button
                disabled={!transcript}
                onClick={() => setTranscript("")}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                <Trash2 className="w-4 h-4" /> Clear
              </button>
            </div>
          </div>

          {/* Recent Dictations */}
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
              <Clock className="w-4 h-4 text-gray-500" />
              <h3 className="font-medium text-gray-900">Recent Dictations</h3>
            </div>
            <div className="divide-y divide-gray-100">
              {recentDictations.map((d) => (
                <div key={d.id} className="px-5 py-3 hover:bg-gray-50 transition-colors">
                  <div className="flex items-center justify-between mb-1">
                    <div className="flex items-center gap-2">
                      <FileText className="w-4 h-4 text-gray-400" />
                      <span className="text-sm font-medium text-gray-900">{d.noteType} Note</span>
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${d.status === "Saved" ? "bg-green-100 text-green-700" : d.status === "Draft" ? "bg-amber-100 text-amber-700" : "bg-blue-100 text-blue-700"}`}>
                        {d.status}
                      </span>
                    </div>
                    <span className="text-xs text-gray-400">{d.date}</span>
                  </div>
                  <p className="text-xs text-gray-500 truncate">{d.preview}</p>
                  <div className="flex items-center gap-3 mt-1 text-[10px] text-gray-400">
                    <span>Duration: {d.duration}</span>
                    <span>{d.wordCount} words</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
