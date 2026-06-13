"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
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

  const empCols = [
    { key: "id", header: "Employee", fields: ["employeeId", "id", "employee_id"] },
    { key: "name", header: "Name", fields: ["displayName", "name", "fullName"] },
    { key: "dept", header: "Department", fields: ["department", "departmentId", "department_id"] },
    { key: "status", header: "Status", fields: ["status", "employmentStatus"] },
  ];

  return (
    <AppLayout>
      <PageShell title="HR & payroll" subtitle="Employees, leave, attendance, and payroll via BFF">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link href="/erp" className="text-sm text-primary hover:underline">
            ← ERP hub
          </Link>
          <label className="flex items-center gap-2 text-sm text-foreground">
            Employee
            <select
              className="rounded border border-border px-2 py-1 text-sm"
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
          <label className="flex items-center gap-2 text-sm text-foreground">
            Payroll run
            <select
              className="rounded border border-border px-2 py-1 text-sm"
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
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Employees</h2>
            <JsonApiDataTable data={employees.data} columns={empCols} isLoading={employees.isPending} error={employees.error as Error | null} emptyHint="No employees returned from hr-payroll-service." />
          </section>
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Payroll runs</h2>
            <JsonApiDataTable
              data={runs.data}
              columns={[
                { key: "run", header: "Run", fields: ["runId", "id", "run_id"] },
                { key: "period", header: "Period", fields: ["period", "payPeriod", "periodLabel"] },
                { key: "status", header: "Status", fields: ["status", "runStatus"] },
              ]}
              isLoading={runs.isPending}
              error={runs.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Contracts</h2>
            <JsonApiDataTable
              data={contracts.data}
              columns={[
                { key: "contract", header: "Contract", fields: ["contractId", "id"] },
                { key: "type", header: "Type", fields: ["contractType", "type"] },
                { key: "start", header: "Start", fields: ["startDate", "start_date"] },
                { key: "status", header: "Status", fields: ["status"] },
              ]}
              isLoading={contracts.isPending}
              error={contracts.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Payslips</h2>
            <JsonApiDataTable
              data={payslips.data}
              columns={[
                { key: "slip", header: "Payslip", fields: ["payslipId", "id"] },
                { key: "employee", header: "Employee", fields: ["employeeId", "employee_id"] },
                { key: "net", header: "Net pay", fields: ["netPay", "net_pay", "amount"] },
                { key: "status", header: "Status", fields: ["status"] },
              ]}
              isLoading={payslips.isPending}
              error={payslips.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Leave types</h2>
            <JsonApiDataTable
              data={leaveTypes.data}
              columns={[
                { key: "code", header: "Code", fields: ["code", "leaveTypeCode"] },
                { key: "name", header: "Name", fields: ["name", "label"] },
                { key: "days", header: "Days", fields: ["defaultDays", "days"] },
              ]}
              isLoading={leaveTypes.isPending}
              error={leaveTypes.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Deduction types</h2>
            <JsonApiDataTable
              data={deductions.data}
              columns={[
                { key: "code", header: "Code", fields: ["code", "deductionCode"] },
                { key: "name", header: "Name", fields: ["name", "label"] },
                { key: "rate", header: "Rate", fields: ["rate", "percentage"] },
              ]}
              isLoading={deductions.isPending}
              error={deductions.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm md:col-span-2">
            <h2 className="text-sm font-semibold text-foreground">Leave requests</h2>
            <JsonApiDataTable
              data={leaveReq.data}
              columns={[
                { key: "request", header: "Request", fields: ["requestId", "id"] },
                { key: "type", header: "Type", fields: ["leaveType", "type"] },
                { key: "from", header: "From", fields: ["startDate", "from"] },
                { key: "to", header: "To", fields: ["endDate", "to"] },
                { key: "status", header: "Status", fields: ["status"] },
              ]}
              isLoading={leaveReq.isPending}
              error={leaveReq.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-border bg-card p-4 shadow-sm md:col-span-2">
            <h2 className="text-sm font-semibold text-foreground">Attendance</h2>
            <JsonApiDataTable
              data={attendance.data}
              columns={[
                { key: "date", header: "Date", fields: ["date", "attendanceDate"] },
                { key: "shift", header: "Shift", fields: ["shift", "shiftId"] },
                { key: "hours", header: "Hours", fields: ["hoursWorked", "hours"] },
                { key: "status", header: "Status", fields: ["status"] },
              ]}
              isLoading={attendance.isPending}
              error={attendance.error as Error | null}
            />
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
