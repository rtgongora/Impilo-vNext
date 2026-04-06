"use client";

/**
 * EncounterDocumentsSheet -- Side panel for viewing, uploading, and scanning
 * patient documents during an encounter.
 *
 * Document categories: clinical notes, lab reports, imaging, consent forms,
 * referrals. Includes search, category filter, upload, preview/download,
 * and scan actions.
 *
 * Uses mock data; production would integrate with Landela document service.
 */

import { useState, useMemo } from "react";
import {
  FolderOpen,
  Upload,
  FileText,
  ScanLine,
  Eye,
  Download,
  Clock,
  CheckCircle,
  AlertTriangle,
  Search,
  X,
  File,
  Image as ImageIcon,
  History,
} from "lucide-react";

// ---------------------------------------------------------------------------
// Types & Mock Data
// ---------------------------------------------------------------------------

interface PatientDocument {
  id: string;
  title: string;
  typeCode: string;
  typeName: string;
  capturedAt: string;
  author: string;
  size: string;
  status: "verified" | "pending_review" | "processing" | "draft";
}

const MOCK_DOCUMENTS: PatientDocument[] = [
  {
    id: "doc-1",
    title: "Admission Clinical Note",
    typeCode: "clinical",
    typeName: "Clinical Note",
    capturedAt: "2026-04-02",
    author: "Dr. T. Moyo",
    size: "24 KB",
    status: "verified",
  },
  {
    id: "doc-2",
    title: "Full Blood Count Results",
    typeCode: "lab",
    typeName: "Lab Report",
    capturedAt: "2026-04-03",
    author: "Lab System",
    size: "12 KB",
    status: "verified",
  },
  {
    id: "doc-3",
    title: "Chest X-Ray PA View",
    typeCode: "imaging",
    typeName: "Imaging Report",
    capturedAt: "2026-04-03",
    author: "Dr. K. Ndlovu",
    size: "1.2 MB",
    status: "verified",
  },
  {
    id: "doc-4",
    title: "Informed Consent - Procedure",
    typeCode: "consent",
    typeName: "Consent Form",
    capturedAt: "2026-04-02",
    author: "Sr. J. Phiri",
    size: "8 KB",
    status: "verified",
  },
  {
    id: "doc-5",
    title: "Referral Letter - Cardiology",
    typeCode: "referral",
    typeName: "Referral Letter",
    capturedAt: "2026-04-04",
    author: "Dr. T. Moyo",
    size: "18 KB",
    status: "pending_review",
  },
  {
    id: "doc-6",
    title: "Discharge Summary Draft",
    typeCode: "clinical",
    typeName: "Clinical Note",
    capturedAt: "2026-04-05",
    author: "Dr. T. Moyo",
    size: "32 KB",
    status: "draft",
  },
];

const DOCUMENT_CATEGORIES = [
  { id: "all", label: "All Documents" },
  { id: "clinical", label: "Clinical" },
  { id: "lab", label: "Lab Reports" },
  { id: "imaging", label: "Imaging" },
  { id: "consent", label: "Consent Forms" },
  { id: "referral", label: "Referrals" },
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function getStatusIcon(status: string) {
  switch (status) {
    case "verified":
      return <CheckCircle className="h-3.5 w-3.5 text-green-600" />;
    case "processing":
      return <Clock className="h-3.5 w-3.5 text-blue-600 animate-spin" />;
    case "pending_review":
      return <AlertTriangle className="h-3.5 w-3.5 text-amber-600" />;
    default:
      return <Clock className="h-3.5 w-3.5 text-gray-400" />;
  }
}

function getDocIcon(typeCode: string) {
  if (typeCode === "imaging")
    return <ImageIcon className="h-4 w-4 text-gray-500" />;
  if (typeCode === "lab")
    return <FileText className="h-4 w-4 text-gray-500" />;
  return <File className="h-4 w-4 text-gray-500" />;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function EncounterDocumentsSheet() {
  const [open, setOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<"browse" | "recent" | "history">(
    "browse",
  );
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("all");

  const filteredDocuments = useMemo(() => {
    return MOCK_DOCUMENTS.filter((doc) => {
      const matchesSearch =
        !searchQuery ||
        doc.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        doc.typeCode.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesCategory =
        categoryFilter === "all" || doc.typeCode === categoryFilter;
      return matchesSearch && matchesCategory;
    });
  }, [searchQuery, categoryFilter]);

  return (
    <>
      {/* Trigger button */}
      <button
        onClick={() => setOpen(true)}
        className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-gray-600 hover:bg-gray-100 rounded-md transition-colors"
      >
        <FolderOpen className="w-3.5 h-3.5" />
        Documents
        <span className="h-4 px-1 text-[10px] font-medium bg-gray-100 text-gray-600 rounded">
          {MOCK_DOCUMENTS.length}
        </span>
      </button>

      {/* Overlay + Sheet */}
      {open && (
        <>
          <div
            className="fixed inset-0 bg-black/30 z-40"
            onClick={() => setOpen(false)}
          />
          <div className="fixed right-0 top-0 bottom-0 w-[520px] sm:w-[600px] bg-white border-l border-gray-200 shadow-xl z-50 flex flex-col">
            {/* Header */}
            <div className="px-5 pt-5 pb-3 border-b flex items-center justify-between">
              <h2 className="text-base font-semibold flex items-center gap-2">
                <FolderOpen className="h-5 w-5 text-blue-600" />
                Patient Documents
              </h2>
              <div className="flex items-center gap-1.5">
                <button className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium border rounded-md border-gray-300 hover:bg-gray-50 transition-colors">
                  <ScanLine className="h-3.5 w-3.5" />
                  Scan
                </button>
                <button className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 transition-colors">
                  <Upload className="h-3.5 w-3.5" />
                  Upload
                </button>
                <button
                  onClick={() => setOpen(false)}
                  className="ml-2 p-1 text-gray-400 hover:text-gray-600 rounded"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </div>

            {/* Tabs */}
            <div className="px-5 pt-3">
              <div className="grid grid-cols-3 gap-1 bg-gray-100 rounded-lg p-1">
                {(
                  [
                    { key: "browse", icon: FolderOpen, label: "All Files" },
                    { key: "recent", icon: Clock, label: "Recent" },
                    { key: "history", icon: History, label: "Legacy" },
                  ] as const
                ).map((tab) => {
                  const Icon = tab.icon;
                  return (
                    <button
                      key={tab.key}
                      onClick={() => setActiveTab(tab.key)}
                      className={`flex items-center justify-center gap-1.5 py-1.5 text-xs font-medium rounded-md transition-colors ${
                        activeTab === tab.key
                          ? "bg-white text-gray-900 shadow-sm"
                          : "text-gray-500 hover:text-gray-700"
                      }`}
                    >
                      <Icon className="h-3.5 w-3.5" />
                      {tab.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Browse Tab */}
            {activeTab === "browse" && (
              <div className="flex-1 flex flex-col overflow-hidden">
                {/* Search & Filters */}
                <div className="px-5 py-3 space-y-2 border-b">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400" />
                    <input
                      placeholder="Search documents..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="w-full h-8 pl-8 pr-3 text-xs border rounded-md border-gray-300 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                    />
                  </div>
                  <div className="flex gap-1 flex-wrap">
                    {DOCUMENT_CATEGORIES.map((cat) => (
                      <button
                        key={cat.id}
                        onClick={() => setCategoryFilter(cat.id)}
                        className={`text-[10px] px-2 py-0.5 rounded-full border transition-colors ${
                          categoryFilter === cat.id
                            ? "bg-blue-600 text-white border-blue-600"
                            : "bg-white text-gray-600 border-gray-200 hover:border-blue-300"
                        }`}
                      >
                        {cat.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Document list */}
                <div className="flex-1 overflow-y-auto px-5 py-3">
                  {filteredDocuments.length === 0 ? (
                    <div className="text-center py-12">
                      <FileText className="h-10 w-10 text-gray-300 mx-auto mb-3" />
                      <p className="text-sm font-medium mb-1">
                        No documents found
                      </p>
                      <p className="text-xs text-gray-500 mb-4">
                        {searchQuery
                          ? "Try a different search term"
                          : "Scan or upload documents to get started"}
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      {filteredDocuments.map((doc) => (
                        <div
                          key={doc.id}
                          className="flex items-center gap-3 p-3 rounded-lg border border-gray-200 hover:border-blue-300 cursor-pointer transition-colors group"
                        >
                          <div className="h-9 w-9 rounded bg-gray-100 flex items-center justify-center shrink-0">
                            {getDocIcon(doc.typeCode)}
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium truncate">
                              {doc.title}
                            </p>
                            <div className="flex items-center gap-1.5 text-[11px] text-gray-500">
                              <span>{doc.typeName}</span>
                              <span>&middot;</span>
                              <span>{doc.capturedAt}</span>
                              <span>&middot;</span>
                              <span>{doc.author}</span>
                              <span>&middot;</span>
                              <span>{doc.size}</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-1.5">
                            {getStatusIcon(doc.status)}
                            <button
                              className="p-1 text-gray-400 opacity-0 group-hover:opacity-100 hover:text-blue-600 transition-all"
                              title="Preview"
                            >
                              <Eye className="h-3.5 w-3.5" />
                            </button>
                            <button
                              className="p-1 text-gray-400 opacity-0 group-hover:opacity-100 hover:text-blue-600 transition-all"
                              title="Download"
                            >
                              <Download className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Recent Tab */}
            {activeTab === "recent" && (
              <div className="flex-1 overflow-y-auto px-5 py-3">
                <div className="text-center py-12">
                  <ScanLine className="h-10 w-10 text-gray-300 mx-auto mb-3" />
                  <p className="text-sm font-medium mb-1">No recent scans</p>
                  <p className="text-xs text-gray-500 mb-4">
                    Documents scanned or uploaded during this encounter appear
                    here
                  </p>
                  <button className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium border rounded-md border-gray-300 hover:bg-gray-50 mx-auto transition-colors">
                    <ScanLine className="h-3.5 w-3.5" />
                    Scan Now
                  </button>
                </div>
              </div>
            )}

            {/* History Tab */}
            {activeTab === "history" && (
              <div className="flex-1 overflow-y-auto px-5 py-3">
                <div className="text-center py-6 mb-4">
                  <History className="h-8 w-8 text-gray-300 mx-auto mb-2" />
                  <p className="text-sm font-medium mb-1">
                    Legacy &amp; Scanned Records
                  </p>
                  <p className="text-xs text-gray-500 mb-4">
                    Upload old paper records, historical documents, and previous
                    provider notes
                  </p>
                  <div className="flex justify-center gap-2">
                    <button className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium border rounded-md border-gray-300 hover:bg-gray-50 transition-colors">
                      <ScanLine className="h-3.5 w-3.5" />
                      Scan Old Records
                    </button>
                    <button className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium border rounded-md border-gray-300 hover:bg-gray-50 transition-colors">
                      <Upload className="h-3.5 w-3.5" />
                      Upload Files
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </>
      )}
    </>
  );
}
