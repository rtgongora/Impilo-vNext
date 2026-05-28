"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
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

        <div className="grid gap-4 md:grid-cols-2">
          <QueryResultPanel title="Employees" {...employees} data={employees.data} />
          <QueryResultPanel title="Payroll runs" {...runs} data={runs.data} />
          <QueryResultPanel title="Contracts" {...contracts} data={contracts.data} />
          <QueryResultPanel title="Payslips" {...payslips} data={payslips.data} />
          <QueryResultPanel title="Leave types" {...leaveTypes} data={leaveTypes.data} />
          <QueryResultPanel title="Deduction types" {...deductions} data={deductions.data} />
          <QueryResultPanel title="Leave requests" {...leaveReq} data={leaveReq.data} />
          <QueryResultPanel title="Attendance" {...attendance} data={attendance.data} />
        </div>
      </PageShell>
    </AppLayout>
  );
}
