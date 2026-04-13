package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.HrPayrollServiceClient;

@RestController
@RequestMapping("/internal/v1/erp/hr")
public class ErpHrBffController {

    private final HrPayrollServiceClient client;

    public ErpHrBffController(HrPayrollServiceClient client) {
        this.client = client;
    }

    @GetMapping("/employees")
    public ResponseEntity<JsonNode> employees() {
        return ResponseEntity.ok(client.getEmployees());
    }

    @PostMapping("/employees")
    public ResponseEntity<JsonNode> createEmployee(@RequestBody JsonNode body) {
        return ResponseEntity.ok(client.postEmployee(body));
    }

    @GetMapping("/contracts")
    public ResponseEntity<JsonNode> contracts(@RequestParam("employee_id") String employeeId) {
        return ResponseEntity.ok(client.getContracts(employeeId));
    }

    @GetMapping("/leave/types")
    public ResponseEntity<JsonNode> leaveTypes() {
        return ResponseEntity.ok(client.getLeaveTypes());
    }

    @GetMapping("/leave/requests")
    public ResponseEntity<JsonNode> leaveRequests(@RequestParam("employee_id") String employeeId) {
        return ResponseEntity.ok(client.getLeaveRequests(employeeId));
    }

    @GetMapping("/attendance")
    public ResponseEntity<JsonNode> attendance(@RequestParam("employee_id") String employeeId) {
        return ResponseEntity.ok(client.getAttendance(employeeId));
    }

    @GetMapping("/payroll/runs")
    public ResponseEntity<JsonNode> payrollRuns() {
        return ResponseEntity.ok(client.getPayrollRuns());
    }

    @PostMapping("/payroll/runs")
    public ResponseEntity<JsonNode> createPayrollRun(@RequestBody JsonNode body) {
        return ResponseEntity.ok(client.postPayrollRun(body));
    }

    @PostMapping("/payroll/runs/{runId}/calculate")
    public ResponseEntity<JsonNode> calculatePayroll(@PathVariable String runId) {
        return ResponseEntity.ok(client.postPayrollCalculate(runId));
    }

    @PostMapping("/payroll/runs/{runId}/pay")
    public ResponseEntity<JsonNode> payPayroll(@PathVariable String runId, @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.ok(client.postPayrollPay(runId, body));
    }

    @GetMapping("/payroll/payslips")
    public ResponseEntity<JsonNode> payslips(@RequestParam("run_id") String runId) {
        return ResponseEntity.ok(client.getPayslips(runId));
    }

    @GetMapping("/deductions")
    public ResponseEntity<JsonNode> deductions() {
        return ResponseEntity.ok(client.getDeductions());
    }
}
