"use client";

/**
 * PortalHealthReporting -- Community health concern reporting for the patient portal.
 *
 * Report categories: Disease/illness, Environmental concerns, Food safety,
 * Health service complaints.
 * Includes report form with category, description, location, severity,
 * status tracking, and recent reports list.
 */

import { useState } from "react";
import {
  AlertTriangle,
  Bug,
  Droplets,
  Megaphone,
  Send,
  CheckCircle,
  Clock,
  MapPin,
} from "lucide-react";

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

const MY_REPORTS = [
  {
    id: "CR-001",
    type: "Water Contamination",
    category: "environmental",
    location: "Glen Norah Borehole #3",
    status: "investigating" as const,
    submitted: "2026-04-03",
    lastUpdate: "2026-04-05",
  },
  {
    id: "CR-002",
    type: "Suspected Cholera Cases",
    category: "disease",
    location: "Budiriro 5",
    status: "responded" as const,
    submitted: "2026-03-28",
    lastUpdate: "2026-04-01",
  },
  {
    id: "CR-003",
    type: "Illegal Dumping",
    category: "environmental",
    location: "Near Mbare Musika",
    status: "resolved" as const,
    submitted: "2026-03-15",
    lastUpdate: "2026-03-22",
  },
];

interface ReportCategory {
  value: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description: string;
  color: string;
}

const REPORT_CATEGORIES: ReportCategory[] = [
  {
    value: "disease",
    label: "Disease / Unusual Symptoms",
    icon: Bug,
    description:
      "Report suspected outbreaks, unusual illness clusters, or disease symptoms in your community",
    color: "text-red-600",
  },
  {
    value: "environmental",
    label: "Environmental Concern",
    icon: Droplets,
    description:
      "Water contamination, sanitation issues, pest infestations, air quality, illegal dumping",
    color: "text-amber-600",
  },
  {
    value: "food_safety",
    label: "Food Safety",
    icon: AlertTriangle,
    description:
      "Unsafe food handling, expired products, unhygienic food premises",
    color: "text-orange-500",
  },
  {
    value: "service",
    label: "Health Service Complaint",
    icon: Megaphone,
    description:
      "Issues with health facilities, providers, or public health services",
    color: "text-impilo-500",
  },
];

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function PortalHealthReporting() {
  const [showForm, setShowForm] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = () => {
    setSubmitted(true);
    setTimeout(() => {
      setSubmitted(false);
      setShowForm(false);
      setSelectedCategory(null);
    }, 3000);
  };

  return (
    <div className="space-y-4">
      {/* Report a Concern Card */}
      <div className="border rounded-lg bg-white">
        <div className="px-5 py-4 border-b">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-amber-500" />
            Report a Health Concern
          </h3>
          <p className="text-sm text-gray-500 mt-1">
            Help keep your community safe. Reports are reviewed by local public
            health officers and you&apos;ll be notified of progress.
          </p>
        </div>
        <div className="p-5">
          {!showForm ? (
            /* Category picker */
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {REPORT_CATEGORIES.map((cat) => {
                const Icon = cat.icon;
                return (
                  <button
                    key={cat.value}
                    onClick={() => {
                      setSelectedCategory(cat.value);
                      setShowForm(true);
                    }}
                    className="p-4 border rounded-lg text-left hover:border-impilo-400 hover:bg-impilo-50/50 transition-all"
                  >
                    <div className="flex items-center gap-3 mb-2">
                      <div className="p-2 rounded-lg bg-gray-100">
                        <Icon className={`h-5 w-5 ${cat.color}`} />
                      </div>
                      <span className="font-semibold text-sm">{cat.label}</span>
                    </div>
                    <p className="text-xs text-gray-500">{cat.description}</p>
                  </button>
                );
              })}
            </div>
          ) : submitted ? (
            /* Success state */
            <div className="text-center py-8 space-y-3">
              <CheckCircle className="h-12 w-12 text-green-500 mx-auto" />
              <h3 className="text-lg font-bold">
                Report Submitted Successfully
              </h3>
              <p className="text-sm text-gray-500">
                Your report has been received and assigned to a public health
                officer. You&apos;ll receive updates on progress.
              </p>
            </div>
          ) : (
            /* Report form */
            <div className="space-y-4">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs border rounded px-2 py-0.5 capitalize text-gray-600 border-gray-200">
                  {selectedCategory?.replace(/_/g, " ")}
                </span>
                <button
                  className="text-xs text-impilo-500 hover:underline"
                  onClick={() => {
                    setShowForm(false);
                    setSelectedCategory(null);
                  }}
                >
                  Change category
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    What are you reporting?
                  </label>
                  <input
                    type="text"
                    placeholder="Brief title, e.g. 'Dirty water from borehole'"
                    className="w-full h-9 px-3 text-sm border rounded-md border-gray-300 focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Location
                  </label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder="Area, address, or landmark"
                      className="flex-1 h-9 px-3 text-sm border rounded-md border-gray-300 focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                    />
                    <button className="h-9 w-9 flex items-center justify-center border rounded-md border-gray-300 hover:bg-gray-50">
                      <MapPin className="h-4 w-4 text-gray-500" />
                    </button>
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Describe the situation
                </label>
                <textarea
                  placeholder="Provide as much detail as possible: What did you see? When did it start? How many people are affected?"
                  className="w-full min-h-[100px] px-3 py-2 text-sm border rounded-md border-gray-300 focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                />
              </div>

              {/* Disease-specific fields */}
              {selectedCategory === "disease" && (
                <div className="grid grid-cols-2 gap-3 p-3 border rounded-lg bg-red-50/50">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">
                      How many people affected?
                    </label>
                    <select className="w-full h-8 px-2 text-xs border rounded-md border-gray-300 bg-white">
                      <option value="">Select</option>
                      <option value="1">Just me / 1 person</option>
                      <option value="2-5">2-5 people</option>
                      <option value="6-20">6-20 people</option>
                      <option value="20+">More than 20</option>
                      <option value="unknown">Not sure</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">
                      Main symptoms observed
                    </label>
                    <select className="w-full h-8 px-2 text-xs border rounded-md border-gray-300 bg-white">
                      <option value="">Select</option>
                      <option value="diarrhoea">Diarrhoea / Vomiting</option>
                      <option value="fever">Fever / Body Aches</option>
                      <option value="rash">Skin Rash</option>
                      <option value="respiratory">
                        Cough / Breathing Difficulty
                      </option>
                      <option value="other">Other</option>
                    </select>
                  </div>
                </div>
              )}

              <div>
                <label className="block text-xs text-gray-500 mb-1">
                  You can also attach photos (optional)
                </label>
                <input
                  type="file"
                  accept="image/*"
                  multiple
                  className="text-xs"
                />
              </div>

              <div className="flex items-center gap-2 p-3 border rounded-lg bg-gray-50 text-xs text-gray-500">
                <CheckCircle className="h-4 w-4 text-green-500 shrink-0" />
                Your identity is protected. Only authorized public health
                officers can view your contact details.
              </div>

              <div className="flex gap-2">
                <button
                  className="flex items-center gap-1 px-4 py-2 text-sm font-medium text-white bg-impilo-500 rounded-md hover:bg-impilo-600 transition-colors"
                  onClick={handleSubmit}
                >
                  <Send className="h-4 w-4" /> Submit Report
                </button>
                <button
                  className="px-4 py-2 text-sm font-medium text-gray-700 border rounded-md hover:bg-gray-50 transition-colors"
                  onClick={() => {
                    setShowForm(false);
                    setSelectedCategory(null);
                  }}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* My Reports */}
      <div className="border rounded-lg bg-white">
        <div className="px-5 py-3 border-b">
          <h4 className="text-sm font-semibold">My Reports</h4>
          <p className="text-xs text-gray-500">
            Track the status of your submitted health concerns
          </p>
        </div>
        <div className="p-5 space-y-3">
          {MY_REPORTS.map((r) => (
            <div
              key={r.id}
              className="flex items-center justify-between p-3 border rounded-lg"
            >
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-medium text-sm">{r.type}</span>
                  <span className="text-xs border rounded px-1.5 py-0.5 capitalize text-gray-500 border-gray-200">
                    {r.category}
                  </span>
                </div>
                <div className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <MapPin className="h-3 w-3" /> {r.location}
                  <span>&bull;</span>
                  <span>Submitted {r.submitted}</span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className={`inline-flex items-center text-xs font-medium rounded px-2 py-0.5 ${
                    r.status === "resolved"
                      ? "bg-green-50 text-green-700"
                      : r.status === "responded"
                        ? "bg-impilo-50 text-impilo-600"
                        : "bg-amber-50 text-amber-700"
                  }`}
                >
                  {r.status === "investigating" && (
                    <Clock className="h-3 w-3 mr-1" />
                  )}
                  {r.status === "resolved" && (
                    <CheckCircle className="h-3 w-3 mr-1" />
                  )}
                  {r.status}
                </span>
                <button className="text-xs text-impilo-500 hover:underline">
                  Details
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
