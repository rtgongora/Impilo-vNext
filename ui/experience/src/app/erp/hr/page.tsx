"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useHrAttendance,
  useHrContracts,
  useHrDeductionTypes,
  useHrEmployees,
  useHrLeaveRequests,
  useHrLeaveTypes,
  useHrPayrollRuns,
  useHrPayslips,
} from "@/hooks/queries/useHrPayroll";

type Emp = { employeeId?: string };

export default function ErpHrPage() {
  const employees = useHrEmployees();
  const list = employees.data as Emp[] | undefined;
  const defaultEmp = useMemo(() => {
    if (!Array.isArray(list) || list.length === 0) return "";
    return list[0].employeeId ?? "";
  }, [list]);
  const [employeeId, setEmployeeId] = useState("");
  const effEmp = employeeId || defaultEmp;

  const runs = useHrPayrollRuns();
  const runList = runs.data as { runId?: string }[] | undefined;
  const defaultRun = useMemo(() => {
    if (!Array.isArray(runList) || runList.length === 0) return "";
    return runList[0].runId ?? "";
  }, [runList]);
  const [runId, setRunId] = useState("");
  const effRun = runId || defaultRun;

  const contracts = useHrContracts(effEmp || null);
  const leaveTypes = useHrLeaveTypes();
  const leaveReq = useHrLeaveRequests(effEmp || null);
  const attendance = useHrAttendance(effEmp || null);
  const payslips = useHrPayslips(effRun || null);
  const deductions = useHrDeductionTypes();

  return (
    <AppLayout>
      <PageShell title="HR & payroll" subtitle="Employees, leave, attendance, and payroll via BFF">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link href="/erp" className="text-sm text-impilo-500 hover:underline">
            ← ERP hub
          </Link>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            Employee
            <select
              className="rounded border border-slate-300 px-2 py-1 text-sm"
              value={effEmp}
              onChange={(e) => setEmployeeId(e.target.value)}
            >
              {Array.isArray(list) &&
                list.map((e) => (
                  <option key={e.employeeId} value={e.employeeId ?? ""}>
                    {e.employeeId}
                  </option>
                ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            Payroll run
            <select
              className="rounded border border-slate-300 px-2 py-1 text-sm"
              value={effRun}
              onChange={(e) => setRunId(e.target.value)}
            >
              {Array.isArray(runList) &&
                runList.map((r) => (
                  <option key={r.runId} value={r.runId ?? ""}>
                    {r.runId}
                  </option>
                ))}
            </select>
          </label>
        </div>

        <div className="space-y-6">
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Employees</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(employees.data ?? employees.error ?? employees.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Contracts</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(contracts.data ?? contracts.error ?? contracts.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Leave types</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(leaveTypes.data ?? leaveTypes.error ?? leaveTypes.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Leave requests</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(leaveReq.data ?? leaveReq.error ?? leaveReq.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Attendance</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(attendance.data ?? attendance.error ?? attendance.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Payroll runs</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(runs.data ?? runs.error ?? runs.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Payslips</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(payslips.data ?? payslips.error ?? payslips.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Deduction types</h3>
            <pre className="mt-2 max-h-40 overflow-auto text-xs">
              {JSON.stringify(deductions.data ?? deductions.error ?? deductions.isLoading, null, 2)}
            </pre>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
