-- ============================================================================
-- hr-payroll V002 — drop the workforce attendance/leave tables.
--
-- Workforce attendance + leave (incl. leave types) moved to Vashandi (the workforce SoR);
-- payroll derives worked-hours from Vashandi. These hr-payroll tables are now orphaned.
-- hr-payroll retains employees, contracts, deductions, payroll runs, payslips.
-- ============================================================================

DROP TABLE IF EXISTS hr.attendance_records;
DROP TABLE IF EXISTS hr.leave_requests;
DROP TABLE IF EXISTS hr.leave_balances;
DROP TABLE IF EXISTS hr.leave_types;
