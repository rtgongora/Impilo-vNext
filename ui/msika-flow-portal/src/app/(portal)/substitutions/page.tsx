"use client";

import { useState } from "react";

export default function SubstitutionsPage() {
  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">Substitution Approvals</h1>
      <p className="text-sm text-neutral-500 mb-6">
        Review and approve medication substitutions proposed by your pharmacy.
      </p>

      <div className="card p-12 text-center">
        <p className="text-neutral-500">No pending substitution requests.</p>
      </div>
    </div>
  );
}
