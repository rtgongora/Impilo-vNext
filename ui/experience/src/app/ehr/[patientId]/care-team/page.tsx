"use client";

/**
 * Care Team — Assigned providers with roles, specialties, and contact info.
 * Route: /ehr/[patientId]/care-team | pageTitle: "Care Team"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Users, Loader2, Plus, X, Star, Phone, Mail, Trash2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface CareTeamMember {
  id: string;
  name: string;
  role: string;
  specialty: string;
  isPrimary: boolean;
  phone: string;
  email: string;
  facility: string;
  assignedDate: string;
  status: "Active" | "Inactive";
  avatar: string;
}

const MOCK_TEAM: CareTeamMember[] = [
  { id: "ct-1", name: "Dr. M. Ndlovu", role: "Primary Physician", specialty: "Internal Medicine", isPrimary: true, phone: "+263 77 123 4567", email: "m.ndlovu@impilo.zw", facility: "Harare Central Hospital", assignedDate: "2025-01-15", status: "Active", avatar: "MN" },
  { id: "ct-2", name: "Sr. N. Phiri", role: "Primary Nurse", specialty: "General Nursing", isPrimary: true, phone: "+263 77 234 5678", email: "n.phiri@impilo.zw", facility: "Harare Central Hospital", assignedDate: "2025-01-15", status: "Active", avatar: "NP" },
  { id: "ct-3", name: "Dr. T. Moyo", role: "Specialist", specialty: "Cardiology", isPrimary: false, phone: "+263 77 345 6789", email: "t.moyo@impilo.zw", facility: "Parirenyatwa Hospital", assignedDate: "2025-06-20", status: "Active", avatar: "TM" },
  { id: "ct-4", name: "Dr. S. Chirwa", role: "Consultant", specialty: "Psychiatry", isPrimary: false, phone: "+263 77 456 7890", email: "s.chirwa@impilo.zw", facility: "Harare Central Hospital", assignedDate: "2025-11-10", status: "Active", avatar: "SC" },
  { id: "ct-5", name: "Ms. L. Banda", role: "Dietitian", specialty: "Clinical Nutrition", isPrimary: false, phone: "+263 77 567 8901", email: "l.banda@impilo.zw", facility: "Harare Central Hospital", assignedDate: "2026-01-05", status: "Active", avatar: "LB" },
  { id: "ct-6", name: "Mr. K. Sibanda", role: "Physiotherapist", specialty: "Orthopaedic Physiotherapy", isPrimary: false, phone: "+263 77 678 9012", email: "k.sibanda@impilo.zw", facility: "Parirenyatwa Hospital", assignedDate: "2025-08-12", status: "Inactive", avatar: "KS" },
];

export default function CareTeamPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["care-team", patientId],
    queryFn: async () => ({ data: MOCK_TEAM }),
  });

  const members = data?.data ?? [];
  const [showForm, setShowForm] = useState(false);
  const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
  const [newName, setNewName] = useState("");
  const [newRole, setNewRole] = useState("");
  const [newSpecialty, setNewSpecialty] = useState("");

  const activeMembers = members.filter((m) => m.status === "Active");
  const inactiveMembers = members.filter((m) => m.status === "Inactive");

  return (
    <EHRLayout>
      <PageShell title="Care Team" subtitle="Assigned providers and care team management">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading care team...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Users className="w-5 h-5 text-blue-600" />
                <h2 className="text-lg font-semibold text-gray-900">Care Team</h2>
                <span className="text-xs text-gray-400">{activeMembers.length} active members</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex border border-gray-300 rounded-lg overflow-hidden">
                  <button onClick={() => setViewMode("grid")} className={`px-3 py-1.5 text-xs ${viewMode === "grid" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>Grid</button>
                  <button onClick={() => setViewMode("list")} className={`px-3 py-1.5 text-xs ${viewMode === "list" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>List</button>
                </div>
                <button type="button" onClick={() => setShowForm(true)} className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                  <Plus className="w-4 h-4" />
                  Add Member
                </button>
              </div>
            </div>

            {/* Add Member Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-medium text-gray-900">Add Team Member</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Provider Name</label>
                    <input type="text" value={newName} onChange={(e) => setNewName(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="Search providers..." />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Role</label>
                    <select value={newRole} onChange={(e) => setNewRole(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                      <option value="">Select role...</option>
                      <option>Primary Physician</option>
                      <option>Specialist</option>
                      <option>Consultant</option>
                      <option>Primary Nurse</option>
                      <option>Physiotherapist</option>
                      <option>Dietitian</option>
                      <option>Social Worker</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Specialty</label>
                    <input type="text" value={newSpecialty} onChange={(e) => setNewSpecialty(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="e.g., Cardiology" />
                  </div>
                </div>
                <div className="flex items-center gap-3 pt-4">
                  <button className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">Add to Team</button>
                  <button onClick={() => setShowForm(false)} className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors">Cancel</button>
                </div>
              </div>
            )}

            {/* Members Display */}
            {members.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No care team members assigned</p>
              </div>
            ) : viewMode === "grid" ? (
              <>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {activeMembers.map((member) => (
                    <div key={member.id} className="bg-white rounded-lg border border-gray-200 p-5 hover:border-blue-200 transition-colors">
                      <div className="flex items-start justify-between mb-3">
                        <div className="flex items-center gap-3">
                          <div className="w-11 h-11 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-sm font-semibold">
                            {member.avatar}
                          </div>
                          <div>
                            <h3 className="font-medium text-gray-900 text-sm flex items-center gap-1">
                              {member.name}
                              {member.isPrimary && <Star className="w-3.5 h-3.5 text-amber-500 fill-amber-500" />}
                            </h3>
                            <p className="text-xs text-blue-600">{member.role}</p>
                          </div>
                        </div>
                        <button className="p-1 text-gray-400 hover:text-red-500 transition-colors">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                      <p className="text-xs text-gray-500 mb-2">{member.specialty}</p>
                      <p className="text-xs text-gray-400">{member.facility}</p>
                      <div className="flex items-center gap-3 mt-3 pt-3 border-t border-gray-100">
                        <a href={`tel:${member.phone}`} className="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-blue-600">
                          <Phone className="w-3 h-3" /> {member.phone}
                        </a>
                      </div>
                      <div className="mt-1">
                        <a href={`mailto:${member.email}`} className="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-blue-600">
                          <Mail className="w-3 h-3" /> {member.email}
                        </a>
                      </div>
                    </div>
                  ))}
                </div>
                {inactiveMembers.length > 0 && (
                  <div>
                    <h3 className="text-sm font-medium text-gray-500 mb-3">Inactive Members</h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 opacity-60">
                      {inactiveMembers.map((member) => (
                        <div key={member.id} className="bg-white rounded-lg border border-gray-200 p-5">
                          <div className="flex items-center gap-3 mb-2">
                            <div className="w-11 h-11 rounded-full bg-gray-100 text-gray-500 flex items-center justify-center text-sm font-semibold">{member.avatar}</div>
                            <div>
                              <h3 className="font-medium text-gray-700 text-sm">{member.name}</h3>
                              <p className="text-xs text-gray-500">{member.role} &middot; {member.specialty}</p>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Role</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Specialty</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Facility</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Contact</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.map((m) => (
                      <tr key={m.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 text-gray-900 flex items-center gap-1">
                          {m.name}
                          {m.isPrimary && <Star className="w-3 h-3 text-amber-500 fill-amber-500" />}
                        </td>
                        <td className="px-4 py-3 text-gray-700">{m.role}</td>
                        <td className="px-4 py-3 text-gray-700">{m.specialty}</td>
                        <td className="px-4 py-3 text-gray-500">{m.facility}</td>
                        <td className="px-4 py-3 text-gray-500 text-xs">{m.phone}</td>
                        <td className="px-4 py-3">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${m.status === "Active" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"}`}>{m.status}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
