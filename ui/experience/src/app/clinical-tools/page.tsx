"use client";

/**
 * Clinical Tools — Voice dictation, offline sync, document management,
 * CDS alerts, and clinical productivity tools.
 * Route: /clinical-tools
 * Lovable: Clinical Tools with voice dictation + offline sync.
 * Runtime extends with: CDS alerts config, vitals trends, document management.
 */

import Link from "next/link";
import { useState, useRef, useCallback } from "react";
import {
  Mic, MicOff, Wifi, FileText, Activity,
  Shield, RefreshCw, CheckCircle2, AlertTriangle, Download,
  Loader2, Settings, Heart, ClipboardList,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type ActiveTab = "dictation" | "offline" | "documents" | "cds" | "productivity";

const TABS: { key: ActiveTab; label: string; icon: typeof Mic }[] = [
  { key: "dictation", label: "Voice Dictation", icon: Mic },
  { key: "offline", label: "Offline Sync", icon: Wifi },
  { key: "documents", label: "Documents", icon: FileText },
  { key: "cds", label: "CDS Alerts", icon: Shield },
  { key: "productivity", label: "Productivity", icon: Activity },
];

export default function ClinicalToolsPage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("dictation");

  return (
    <AppLayout>
      <PageShell title="Clinical Tools" subtitle="Voice dictation, offline sync, documents, and clinical decision support">
        <div className="mb-4 flex flex-wrap gap-3 text-sm">
          <Link href="/clinical-tools/rules" className="text-pink-700 hover:underline font-medium">
            Rules engine
          </Link>
          <span className="text-gray-300">·</span>
          <Link href="/clinical-tools/forms" className="text-cyan-700 hover:underline font-medium">
            Form schema builder
          </Link>
        </div>
        <div className="flex gap-1 mb-6 border-b border-gray-200 overflow-x-auto">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                  activeTab === tab.key ? "border-pink-600 text-pink-600" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                <Icon className="w-4 h-4" /> {tab.label}
              </button>
            );
          })}
        </div>

        {activeTab === "dictation" && <DictationTab />}
        {activeTab === "offline" && <OfflineTab />}
        {activeTab === "documents" && <DocumentsTab />}
        {activeTab === "cds" && <CdsTab />}
        {activeTab === "productivity" && <ProductivityTab />}
      </PageShell>
    </AppLayout>
  );
}

// ── VOICE DICTATION TAB ──────────────────────────────────────────
function DictationTab() {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [supported, setSupported] = useState(true);
  const recognitionRef = useRef<SpeechRecognition | null>(null);

  const startListening = useCallback(() => {
    if (typeof window === "undefined") return;
    const SpeechRecognition = (window as unknown as { SpeechRecognition?: typeof window.SpeechRecognition; webkitSpeechRecognition?: typeof window.SpeechRecognition }).SpeechRecognition
      ?? (window as unknown as { webkitSpeechRecognition?: typeof window.SpeechRecognition }).webkitSpeechRecognition;
    if (!SpeechRecognition) { setSupported(false); return; }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = "en-US";

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let text = "";
      for (let i = 0; i < event.results.length; i++) {
        text += event.results[i][0].transcript;
      }
      setTranscript(text);
    };
    recognition.onerror = () => setIsListening(false);
    recognition.onend = () => setIsListening(false);

    recognition.start();
    recognitionRef.current = recognition;
    setIsListening(true);
  }, []);

  const stopListening = useCallback(() => {
    recognitionRef.current?.stop();
    setIsListening(false);
  }, []);

  return (
    <div className="space-y-6">
      <div className="bg-pink-50 rounded-lg border border-pink-200 p-4 text-sm text-pink-800">
        <strong>Voice Dictation:</strong> Use your browser&apos;s built-in speech recognition to dictate clinical notes hands-free. Works in Chrome, Edge, and Safari.
      </div>

      {!supported ? (
        <div className="bg-amber-50 rounded-lg border border-amber-200 p-5 text-center">
          <MicOff className="w-10 h-10 text-amber-400 mx-auto mb-3" />
          <p className="text-sm text-amber-700">Speech recognition is not supported in this browser.</p>
          <p className="text-xs text-amber-600 mt-1">Try Chrome, Edge, or Safari for voice dictation.</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-semibold text-gray-900">Dictation</h3>
            <button onClick={isListening ? stopListening : startListening}
              className={`inline-flex items-center gap-2 px-5 py-3 text-sm font-medium rounded-full transition-all ${
                isListening ? "bg-red-600 text-white animate-pulse" : "bg-pink-600 text-white hover:bg-pink-700"
              }`}>
              {isListening ? <><MicOff className="w-5 h-5" /> Stop</> : <><Mic className="w-5 h-5" /> Start Dictating</>}
            </button>
          </div>

          <div className={`min-h-[200px] rounded-lg border-2 p-4 text-sm ${isListening ? "border-pink-300 bg-pink-50" : "border-gray-200 bg-gray-50"}`}>
            {transcript ? (
              <p className="text-gray-900 whitespace-pre-wrap">{transcript}</p>
            ) : (
              <p className="text-gray-400 italic">{isListening ? "Listening... speak now" : "Press \"Start Dictating\" and speak to capture text"}</p>
            )}
          </div>

          {transcript && (
            <div className="flex gap-2 mt-3">
              <button onClick={() => navigator.clipboard.writeText(transcript)}
                className="px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200">
                Copy to Clipboard
              </button>
              <button onClick={() => setTranscript("")}
                className="px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200">
                Clear
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── OFFLINE SYNC TAB ─────────────────────────────────────────────
function OfflineTab() {
  const { data } = useQuery<{ data: Record<string, unknown> }>({
    queryKey: ["sync-status"],
    queryFn: () => apiClient.get("/internal/v1/clinical-tools/sync/status"),
  });
  const syncInfo = data?.data ?? {};

  return (
    <div className="space-y-6">
      <div className="bg-blue-50 rounded-lg border border-blue-200 p-4 text-sm text-blue-800">
        <strong>Offline Sync Engine:</strong> The mobile app syncs data in the background every 30 seconds. Conflicts are presented for user resolution. All operations are queued and replayed when connectivity resumes.
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <Wifi className={`w-8 h-8 mx-auto mb-2 ${syncInfo.offlineCapable ? "text-green-500" : "text-gray-400"}`} />
          <p className="text-sm font-semibold text-gray-900">Sync Engine</p>
          <p className="text-xs text-green-600">{String(syncInfo.syncEngine ?? "Available")}</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <RefreshCw className="w-8 h-8 text-blue-500 mx-auto mb-2" />
          <p className="text-sm font-semibold text-gray-900">Auto-Sync Interval</p>
          <p className="text-xs text-blue-600">{Number(syncInfo.autoSyncInterval ?? 30000) / 1000}s</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <AlertTriangle className="w-8 h-8 text-amber-500 mx-auto mb-2" />
          <p className="text-sm font-semibold text-gray-900">Conflict Resolution</p>
          <p className="text-xs text-amber-600">{String(syncInfo.conflictResolution ?? "User Prompted")}</p>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-900 mb-3">Sync Architecture</h3>
        <div className="space-y-2 text-xs text-gray-600">
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Operations queued locally when offline</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Background sync every 30 seconds when online</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Conflict detection with user resolution prompts</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Retry with exponential backoff on failures</span></div>
          <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4 text-green-500" /><span>Idempotent replay prevents duplicates</span></div>
        </div>
      </div>
    </div>
  );
}

// ── DOCUMENTS TAB ────────────────────────────────────────────────
function DocumentsTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["clinical-documents"],
    queryFn: () => apiClient.get("/internal/v1/clinical-tools/documents"),
  });
  const docs = data?.data ?? [];

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Document Management (Landela DMS)</h3>
      <p className="text-sm text-gray-500">Upload, manage, and retrieve clinical documents. Backed by MinIO object storage with SHA-256 content verification.</p>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <FileText className="w-6 h-6 text-blue-500 mx-auto mb-1" /><p className="text-lg font-bold text-gray-900">{docs.length}</p><p className="text-xs text-gray-500">Documents</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <Shield className="w-6 h-6 text-green-500 mx-auto mb-1" /><p className="text-lg font-bold text-gray-900">SHA-256</p><p className="text-xs text-gray-500">Content Verify</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <Download className="w-6 h-6 text-purple-500 mx-auto mb-1" /><p className="text-lg font-bold text-gray-900">Pre-signed</p><p className="text-xs text-gray-500">Secure URLs</p>
        </div>
      </div>

      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : docs.length === 0 ? (
        <div className="bg-white rounded-lg border p-12 text-center"><FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No documents uploaded yet</p></div>
      ) : (
        <div className="space-y-2">{docs.map((doc, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <FileText className="w-5 h-5 text-blue-500" />
              <div><p className="text-sm font-medium text-gray-900">{String(doc.filename ?? doc.name ?? "Document")}</p>
                <p className="text-xs text-gray-500">{String(doc.content_type ?? doc.mime_type ?? "—")} · {String(doc.created_at ?? "—")}</p></div>
            </div>
            <span className="text-xs text-gray-400 font-mono">{String(doc.object_id ?? doc.id ?? "").slice(0, 8)}</span>
          </div>
        ))}</div>
      )}
    </div>
  );
}

// ── CDS ALERTS CONFIG TAB ────────────────────────────────────────
function CdsTab() {
  return (
    <div className="space-y-6">
      <h3 className="text-base font-semibold text-gray-900">Clinical Decision Support</h3>
      <p className="text-sm text-gray-500">Rule-based alerts evaluated during encounters. No ML required — purely clinical guideline rules.</p>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">Rule</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Source</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Condition</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Severity</th>
          </tr></thead>
          <tbody>
            {([
              ["Hypertensive Crisis","Vitals","Systolic BP ≥ 180 mmHg","critical"],
              ["Hypoxemia","Vitals","SpO₂ < 92%","critical"],
              ["Drug-Allergy Interaction","Allergies","Active allergy + matching medication","critical"],
              ["Tachycardia","Vitals","Heart rate > 120 bpm","warning"],
              ["Bradycardia","Vitals","Heart rate < 50 bpm","warning"],
              ["Fever","Vitals","Temperature ≥ 38.5°C","warning"],
              ["Severe Allergy Flag","Allergies","Severity = SEVERE or LIFE_THREATENING","warning"],
              ["Diabetes Monitoring","Conditions","Active diabetes (ICD E11)","info"],
              ["Hypertension Monitoring","Conditions","Active hypertension (ICD I10)","info"],
              ["Asthma Alert","Conditions","Active asthma — avoid beta-blockers","info"],
            ] as const).map(([rule,source,condition,severity]) => (
              <tr key={rule} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-medium text-gray-900">{rule}</td>
                <td className="px-3 py-2"><span className="px-1.5 py-0.5 rounded bg-blue-100 text-blue-700 text-[10px]">{source}</span></td>
                <td className="px-3 py-2 text-gray-600">{condition}</td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded text-[10px] ${severity === "critical" ? "bg-red-100 text-red-700" : severity === "warning" ? "bg-amber-100 text-amber-700" : "bg-blue-100 text-blue-700"}`}>{severity}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-green-50 rounded-lg border border-green-200 p-4 text-sm text-green-800">
        <strong>Active in Encounters:</strong> CDS alerts are automatically evaluated when viewing any encounter. Alerts appear as dismissable banners above the encounter content.
      </div>
    </div>
  );
}

// ── PRODUCTIVITY TAB ─────────────────────────────────────────────
function ProductivityTab() {
  return (
    <div className="space-y-6">
      <h3 className="text-base font-semibold text-gray-900">Clinical Productivity Tools</h3>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Heart className="w-6 h-6 text-red-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Vitals Trend Charts</h4>
          <p className="text-xs text-gray-500 mt-1">SVG sparkline charts for BP, HR, SpO₂, and temperature trends. Automatically rendered when 2+ readings exist.</p>
          <p className="text-xs text-green-600 mt-2">✓ Active in Vitals page</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <ClipboardList className="w-6 h-6 text-blue-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Referral Package Builder</h4>
          <p className="text-xs text-gray-500 mt-1">4-step wizard that auto-generates clinical summaries from patient conditions, allergies, and medications.</p>
          <p className="text-xs text-green-600 mt-2">✓ Active in Consults page</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Shield className="w-6 h-6 text-indigo-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Specialty Workspaces</h4>
          <p className="text-xs text-gray-500 mt-1">6 specialty-specific workspaces (Cardiology, Surgery, Obstetrics, Paediatrics, Emergency, Orthopaedics) with tailored tools and order sets.</p>
          <p className="text-xs text-green-600 mt-2">✓ Active at /ehr/[patientId]/workspace/[specialty]</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <Settings className="w-6 h-6 text-gray-500 mb-2" />
          <h4 className="text-sm font-semibold text-gray-900">Encounter Menu Toggle</h4>
          <p className="text-xs text-gray-500 mt-1">Left/right position toggle for the EHR encounter menu. Persists preference in session storage.</p>
          <p className="text-xs text-green-600 mt-2">✓ Active in EHR Layout</p>
        </div>
      </div>
    </div>
  );
}
