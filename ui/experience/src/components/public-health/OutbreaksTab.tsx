"use client";

import { useState } from "react";
import { Siren, Plus, MapPin, BarChart3, UserCheck } from "lucide-react";

const OUTBREAKS = [
  { id: "OB-2026-001", disease: "Cholera", location: "Budiriro, Harare", status: "active", cases: 47, deaths: 2, cfr: "4.3%", started: "2026-02-15", lastUpdate: "2 hrs ago", severity: "high", responseTeams: 3, contactsTraced: 156 },
  { id: "OB-2026-002", disease: "Typhoid", location: "Chitungwiza", status: "active", cases: 23, deaths: 0, cfr: "0%", started: "2026-02-28", lastUpdate: "4 hrs ago", severity: "medium", responseTeams: 1, contactsTraced: 45 },
  { id: "OB-2026-003", disease: "Measles", location: "Masvingo Province", status: "contained", cases: 156, deaths: 3, cfr: "1.9%", started: "2025-12-10", lastUpdate: "1 day ago", severity: "medium", responseTeams: 2, contactsTraced: 890 },
];

const EPI_CURVE_DATA = [
  { week: "W6", cases: 2 }, { week: "W7", cases: 5 }, { week: "W8", cases: 8 }, { week: "W9", cases: 12 },
  { week: "W10", cases: 7 }, { week: "W11", cases: 5 }, { week: "W12", cases: 4 }, { week: "W13", cases: 3 }, { week: "W14", cases: 1 },
];

const CONTACT_QUEUE = [
  { id: "CT-001", contact: "M. Banda", index: "CPID-***421", relation: "Household", status: "under_monitoring", day: "Day 4/5", symptoms: false },
  { id: "CT-002", contact: "S. Moyo", index: "CPID-***421", relation: "Neighbour", status: "under_monitoring", day: "Day 4/5", symptoms: false },
  { id: "CT-003", contact: "T. Ncube", index: "CPID-***422", relation: "Household", status: "symptomatic", day: "Day 3/5", symptoms: true },
  { id: "CT-004", contact: "R. Zhou", index: "CPID-***422", relation: "School", status: "completed", day: "Day 5/5", symptoms: false },
  { id: "CT-005", contact: "L. Dube", index: "CPID-***423", relation: "Household", status: "lost_to_followup", day: "Day 2/5", symptoms: false },
];

export function OutbreaksTab() {
  const [selectedOutbreak, setSelectedOutbreak] = useState<string | null>(null);
  const [showDeclareForm, setShowDeclareForm] = useState(false);
  const [showNewContact, setShowNewContact] = useState(false);
  const [selectedContact, setSelectedContact] = useState<string | null>(null);
  const [detailSubTab, setDetailSubTab] = useState<"epi" | "contacts" | "response">("epi");

  return (
    <div className="space-y-4">
      {/* Outbreak List */}
      <div className="bg-white rounded-lg border border-gray-200">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <div>
            <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Siren className="h-5 w-5" /> Outbreak & Incident Operations
            </h4>
            <p className="text-xs text-gray-500">Full lifecycle management - detection, response, containment, closure</p>
          </div>
          <button onClick={() => setShowDeclareForm(!showDeclareForm)}
            className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
            <Plus className="h-4 w-4" /> Declare Outbreak
          </button>
        </div>

        <div className="p-4 space-y-4">
          {/* Declare Outbreak Form */}
          {showDeclareForm && (
            <div className="p-4 border border-red-200 bg-red-50 rounded-lg">
              <h4 className="font-semibold text-sm mb-3 text-red-700">Declare New Outbreak / Health Incident</h4>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600">Disease / Condition</label>
                  <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                    <option value="">Select</option>
                    <option value="cholera">Cholera</option>
                    <option value="typhoid">Typhoid</option>
                    <option value="measles">Measles</option>
                    <option value="anthrax">Anthrax</option>
                    <option value="food_poisoning">Food Poisoning</option>
                    <option value="chemical">Chemical Incident</option>
                    <option value="other">Other</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Location / Area</label>
                  <input placeholder="e.g. Budiriro, Harare" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Severity</label>
                  <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                    <option value="">Assess</option>
                    <option value="low">Low</option>
                    <option value="medium">Medium</option>
                    <option value="high">High</option>
                    <option value="critical">Critical</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Initial Case Count</label>
                  <input type="number" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Initial Deaths</label>
                  <input type="number" defaultValue={0} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Date of First Case</label>
                  <input type="date" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Reporting Officer</label>
                  <input placeholder="Name and designation" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">EOC Activation</label>
                  <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                    <option value="none">No EOC Activation</option>
                    <option value="level1">Level 1 - Watch</option>
                    <option value="level2">Level 2 - Enhanced Response</option>
                    <option value="level3">Level 3 - Full Activation</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600">Response Teams to Deploy</label>
                  <input type="number" defaultValue={1} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                </div>
              </div>
              <div className="mt-3">
                <label className="text-xs font-medium text-gray-600">Situation Summary</label>
                <textarea placeholder="Describe the outbreak situation, affected population, initial response..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[80px]" />
              </div>
              <div className="flex gap-2 mt-3">
                <button className="px-3 py-1.5 bg-red-600 text-white text-xs font-medium rounded-lg hover:bg-red-700">Declare Outbreak</button>
                <button onClick={() => setShowDeclareForm(false)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
              </div>
            </div>
          )}

          {/* Outbreaks Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">ID</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Disease</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Location</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Cases</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Deaths</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">CFR</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Teams</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Contacts</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Started</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                </tr>
              </thead>
              <tbody>
                {OUTBREAKS.map(ob => (
                  <tr key={ob.id} className={`border-b hover:bg-gray-50 ${selectedOutbreak === ob.id ? "bg-blue-50" : ""}`}>
                    <td className="px-3 py-2 font-mono">{ob.id}</td>
                    <td className="px-3 py-2 font-medium text-gray-900">{ob.disease}</td>
                    <td className="px-3 py-2 flex items-center gap-1"><MapPin className="h-3 w-3" />{ob.location}</td>
                    <td className="px-3 py-2">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        ob.status === "active" ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"
                      }`}>{ob.status}</span>
                    </td>
                    <td className="px-3 py-2 font-bold">{ob.cases}</td>
                    <td className="px-3 py-2">{ob.deaths}</td>
                    <td className={`px-3 py-2 ${parseFloat(ob.cfr) > 2 ? "text-red-700 font-bold" : ""}`}>{ob.cfr}</td>
                    <td className="px-3 py-2">{ob.responseTeams}</td>
                    <td className="px-3 py-2">{ob.contactsTraced}</td>
                    <td className="px-3 py-2 text-gray-500">{ob.started}</td>
                    <td className="px-3 py-2">
                      <button onClick={() => setSelectedOutbreak(ob.id === selectedOutbreak ? null : ob.id)}
                        className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">
                        {ob.id === selectedOutbreak ? "Close" : "Detail"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Outbreak Detail Section */}
      {selectedOutbreak && (
        <>
          {/* Outbreak Update Form */}
          <div className="p-4 border border-amber-200 bg-amber-50 rounded-lg">
            <h4 className="font-semibold text-sm mb-3">Update Outbreak - {OUTBREAKS.find(o => o.id === selectedOutbreak)?.disease}</h4>
            <div className="grid grid-cols-4 gap-3">
              <div>
                <label className="text-xs font-medium text-gray-600">New Cases (today)</label>
                <input type="number" defaultValue={0} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600">New Deaths (today)</label>
                <input type="number" defaultValue={0} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600">New Contacts Traced</label>
                <input type="number" defaultValue={0} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600">Status Update</label>
                <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                  <option value="active">Active - Ongoing</option>
                  <option value="declining">Active - Declining</option>
                  <option value="contained">Contained</option>
                  <option value="closed">Closed</option>
                </select>
              </div>
            </div>
            <div className="mt-2">
              <label className="text-xs font-medium text-gray-600">Situation Update Notes</label>
              <textarea placeholder="Today's situation update..." className="w-full text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[40px]" />
            </div>
            <div className="flex gap-2 mt-2">
              <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Submit Daily Update</button>
              <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Generate SitRep</button>
            </div>
          </div>

          {/* Detail Sub-tabs */}
          <div className="flex gap-1 border-b border-gray-200">
            {[
              { key: "epi" as const, label: "Epi Curve" },
              { key: "contacts" as const, label: "Contact Tracing" },
              { key: "response" as const, label: "Response Teams" },
            ].map((tab) => (
              <button key={tab.key} onClick={() => setDetailSubTab(tab.key)}
                className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
                  detailSubTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                {tab.label}
              </button>
            ))}
          </div>

          {/* Epi Curve */}
          {detailSubTab === "epi" && (
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h4 className="text-sm font-semibold flex items-center gap-2 mb-1">
                <BarChart3 className="h-4 w-4" /> Epidemiological Curve
              </h4>
              <p className="text-xs text-gray-500 mb-4">Weekly case distribution - {OUTBREAKS.find(o => o.id === selectedOutbreak)?.disease}</p>
              <div className="flex items-end gap-2 h-32">
                {EPI_CURVE_DATA.map((d, i) => (
                  <div key={i} className="flex-1 flex flex-col items-center gap-1">
                    <div className="w-full bg-red-400 rounded-t" style={{ height: `${(d.cases / 12) * 100}%`, minHeight: d.cases > 0 ? 4 : 0 }} />
                    <span className="text-[9px] text-gray-500">{d.week}</span>
                  </div>
                ))}
              </div>
              <p className="text-xs text-gray-500 mt-2 text-center">Cases by epidemiological week</p>
            </div>
          )}

          {/* Contact Tracing */}
          {detailSubTab === "contacts" && (
            <div className="bg-white rounded-lg border border-gray-200">
              <div className="px-4 py-3 border-b flex items-center justify-between">
                <h4 className="text-sm font-semibold flex items-center gap-2">
                  <UserCheck className="h-4 w-4" /> Contact Tracing Queue
                </h4>
                <button onClick={() => setShowNewContact(!showNewContact)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
                  <Plus className="h-3.5 w-3.5" /> Log Contact
                </button>
              </div>
              <div className="p-4 space-y-4">
                {showNewContact && (
                  <div className="p-4 border border-blue-200 bg-blue-50 rounded-lg">
                    <h4 className="font-semibold text-sm mb-3">Log New Contact</h4>
                    <div className="grid grid-cols-3 gap-3">
                      <div>
                        <label className="text-xs font-medium text-gray-600">Contact Name</label>
                        <input placeholder="Full name" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Index Case (CPID)</label>
                        <input placeholder="CPID of source case" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Relationship</label>
                        <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                          <option value="">Select</option>
                          <option value="household">Household</option>
                          <option value="neighbour">Neighbour</option>
                          <option value="workplace">Workplace</option>
                          <option value="school">School</option>
                          <option value="healthcare">Healthcare Worker</option>
                          <option value="other">Other</option>
                        </select>
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Phone Number</label>
                        <input placeholder="+263..." className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Address</label>
                        <input placeholder="Location" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                      <div>
                        <label className="text-xs font-medium text-gray-600">Monitoring Duration (days)</label>
                        <input type="number" defaultValue={5} className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                      </div>
                    </div>
                    <div className="flex gap-2 mt-3">
                      <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Register Contact</button>
                      <button onClick={() => setShowNewContact(false)} className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Cancel</button>
                    </div>
                  </div>
                )}

                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b bg-gray-50">
                        <th className="text-left px-3 py-2 font-medium text-gray-600">ID</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Contact Name</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Index Case</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Relation</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Monitoring</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600">Symptomatic</th>
                        <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {CONTACT_QUEUE.map(c => (
                        <tr key={c.id} className={`border-b hover:bg-gray-50 ${selectedContact === c.id ? "bg-blue-50" : ""}`}>
                          <td className="px-3 py-2 font-mono">{c.id}</td>
                          <td className="px-3 py-2 font-medium text-gray-900">{c.contact}</td>
                          <td className="px-3 py-2 font-mono">{c.index}</td>
                          <td className="px-3 py-2">{c.relation}</td>
                          <td className="px-3 py-2">
                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                              c.status === "symptomatic" ? "bg-red-100 text-red-700" :
                              c.status === "lost_to_followup" ? "bg-amber-100 text-amber-700" :
                              c.status === "completed" ? "bg-green-100 text-green-700" : "bg-blue-100 text-blue-700"
                            }`}>{c.status.replace(/_/g, " ")}</span>
                          </td>
                          <td className="px-3 py-2">{c.day}</td>
                          <td className="px-3 py-2">
                            {c.symptoms ? <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded-full text-[10px]">Yes</span> : <span className="text-gray-500">No</span>}
                          </td>
                          <td className="px-3 py-2">
                            <button onClick={() => setSelectedContact(selectedContact === c.id ? null : c.id)}
                              className="px-2 py-1 text-gray-500 text-[10px] font-medium hover:text-gray-700">
                              {selectedContact === c.id ? "Close" : "Update"}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Expanded Contact Follow-up */}
                {selectedContact && (() => {
                  const c = CONTACT_QUEUE.find(ct => ct.id === selectedContact);
                  if (!c) return null;
                  return (
                    <div className="p-3 bg-gray-50 rounded-lg border border-gray-200 space-y-2">
                      <h4 className="text-sm font-semibold">Daily Follow-up - {c.contact}</h4>
                      <div className="grid grid-cols-3 gap-3">
                        <div>
                          <label className="text-xs font-medium text-gray-600">Today&apos;s Check</label>
                          <select className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1">
                            <option value="">Result</option>
                            <option value="well">Well - No Symptoms</option>
                            <option value="symptomatic">Symptomatic - Refer for Testing</option>
                            <option value="unreachable">Could Not Reach</option>
                            <option value="refused">Refused Follow-up</option>
                          </select>
                        </div>
                        <div>
                          <label className="text-xs font-medium text-gray-600">Temperature (C)</label>
                          <input placeholder="e.g. 36.8" className="w-full h-8 px-2 text-xs border border-gray-300 rounded-lg mt-1" />
                        </div>
                        <div>
                          <label className="text-xs font-medium text-gray-600">Notes</label>
                          <textarea placeholder="Observations..." className="w-full h-8 text-xs border border-gray-300 rounded-lg mt-1 p-2 min-h-[32px]" />
                        </div>
                      </div>
                      <div className="flex gap-2">
                        <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Save Check-in</button>
                        <button className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-lg">Convert to Case</button>
                      </div>
                    </div>
                  );
                })()}
              </div>
            </div>
          )}

          {/* Response Teams */}
          {detailSubTab === "response" && (
            <div className="grid grid-cols-3 gap-4">
              {[
                { name: "Harare South Response Team", lead: "J. Moyo", members: 6, tasks: "Contact tracing, water sampling", since: "Apr 2" },
                { name: "WASH Emergency Team", lead: "T. Matamba", members: 4, tasks: "Water purification, hygiene promotion", since: "Apr 3" },
                { name: "CTC Clinical Team", lead: "Dr. S. Hwende", members: 8, tasks: "Patient management, case management", since: "Apr 3" },
              ].map((t, i) => (
                <div key={i} className="bg-white rounded-lg border border-gray-200 p-4">
                  <h4 className="font-semibold text-sm mb-2">{t.name}</h4>
                  <div className="space-y-1 text-xs text-gray-500">
                    <p>Lead: <span className="text-gray-900">{t.lead}</span></p>
                    <p>Members: <span className="text-gray-900">{t.members}</span></p>
                    <p>Tasks: <span className="text-gray-900">{t.tasks}</span></p>
                    <p>Deployed: <span className="text-gray-900">{t.since}</span></p>
                  </div>
                  <button className="mt-2 w-full px-3 py-1.5 border border-gray-300 rounded-lg text-xs font-medium hover:bg-gray-50">Update Team Status</button>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
