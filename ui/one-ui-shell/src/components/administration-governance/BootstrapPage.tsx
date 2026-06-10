"use client";

import { AdministrationGovernanceShell } from "./AdministrationGovernanceShell";
import { FirstNationalAdministratorWizard } from "./FirstNationalAdministratorWizard";

export function BootstrapPage() {
  return (
    <AdministrationGovernanceShell title="Platform Bootstrap" subtitle="Controlled first national administrator creation" requireGovernanceEntry={false}>
      <FirstNationalAdministratorWizard />
    </AdministrationGovernanceShell>
  );
}
