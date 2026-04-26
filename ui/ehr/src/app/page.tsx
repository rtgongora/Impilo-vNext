"use client";

import { useState } from "react";
import { useEhrStore, type Patient } from "@/stores/ehrStore";
import { PatientBanner } from "./components/PatientBanner";
import { PatientSearch } from "./components/PatientSearch";
import { ClinicalDashboard } from "./components/ClinicalDashboard";
import { EncounterPanel } from "./components/EncounterPanel";
import { VitoClientRegistrationPanel } from "./components/VitoClientRegistrationPanel";

export default function EhrPage() {
  const { activePatient, activeEncounter, setActivePatient, addRecentPatient } = useEhrStore();
  const [showVitoRegister, setShowVitoRegister] = useState(false);

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-impilo-primary text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h1 className="text-lg font-semibold">Impilo EHR</h1>
          <nav className="flex gap-3 text-sm">
            <span className="opacity-80">Clinical Workspace</span>
          </nav>
        </div>
        <div className="text-sm opacity-80">
          {new Date().toLocaleDateString("en-ZW", {
            weekday: "long",
            year: "numeric",
            month: "long",
            day: "numeric",
          })}
        </div>
      </header>

      <main className="flex-1 flex">
        <aside className="w-80 border-r bg-white p-4 overflow-y-auto">
          <PatientSearch />
          <button
            type="button"
            onClick={() => setShowVitoRegister((v) => !v)}
            className="mt-3 w-full text-xs font-medium text-impilo-primary border border-impilo-primary/40 rounded-md py-1.5 hover:bg-impilo-primary/5"
          >
            {showVitoRegister ? "Hide" : "Show"} Vito new-client registration
          </button>
          {showVitoRegister ? (
            <VitoClientRegistrationPanel
              onRegistered={(p: Patient) => {
                setActivePatient(p);
                addRecentPatient(p);
                setShowVitoRegister(false);
              }}
            />
          ) : null}
        </aside>

        <section className="flex-1 flex flex-col">
          {activePatient ? (
            <>
              <PatientBanner patient={activePatient} />
              <div className="flex-1 flex">
                <div className="flex-1 overflow-y-auto p-4">
                  <ClinicalDashboard patient={activePatient} />
                </div>
                {activeEncounter && (
                  <div className="w-96 border-l bg-white overflow-y-auto">
                    <EncounterPanel encounter={activeEncounter} />
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-gray-400">
              <div className="text-center">
                <p className="text-2xl mb-2">Select a patient</p>
                <p>Search by name, NID, or CPID to begin</p>
              </div>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
