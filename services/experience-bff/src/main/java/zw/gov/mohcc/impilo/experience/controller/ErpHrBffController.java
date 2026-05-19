package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.HrPayrollServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/erp/hr")
public class ErpHrBffController {

    private final HrPayrollServiceClient client;

    public ErpHrBffController(HrPayrollServiceClient client) {
        this.client = client;
    }

    @GetMapping("/employees")
    public ResponseEntity<?> employees(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                       @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getEmployees());
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch employees", requestId, correlationId);
        }
    }

    @PostMapping("/employees")
    public ResponseEntity<?> createEmployee(@RequestBody JsonNode body,
                                            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.postEmployee(body));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to create employee", requestId, correlationId);
        }
    }

    @GetMapping("/contracts")
    public ResponseEntity<?> contracts(@RequestParam("employee_id") String employeeId,
                                       @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                       @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getContracts(employeeId));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch contracts", requestId, correlationId);
        }
    }

    @GetMapping("/leave/types")
    public ResponseEntity<?> leaveTypes(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                        @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getLeaveTypes());
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch leave types", requestId, correlationId);
        }
    }

    @GetMapping("/leave/requests")
    public ResponseEntity<?> leaveRequests(@RequestParam("employee_id") String employeeId,
                                           @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                           @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getLeaveRequests(employeeId));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch leave requests", requestId, correlationId);
        }
    }

    @GetMapping("/attendance")
    public ResponseEntity<?> attendance(@RequestParam("employee_id") String employeeId,
                                        @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                        @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getAttendance(employeeId));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch attendance", requestId, correlationId);
        }
    }

    @GetMapping("/payroll/runs")
    public ResponseEntity<?> payrollRuns(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                         @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getPayrollRuns());
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch payroll runs", requestId, correlationId);
        }
    }

    @PostMapping("/payroll/runs")
    public ResponseEntity<?> createPayrollRun(@RequestBody JsonNode body,
                                              @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                              @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.postPayrollRun(body));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to create payroll run", requestId, correlationId);
        }
    }

    @PostMapping("/payroll/runs/{runId}/calculate")
    public ResponseEntity<?> calculatePayroll(@PathVariable String runId,
                                              @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                              @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.postPayrollCalculate(runId));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to calculate payroll", requestId, correlationId);
        }
    }

    @PostMapping("/payroll/runs/{runId}/pay")
    public ResponseEntity<?> payPayroll(@PathVariable String runId,
                                        @RequestBody(required = false) JsonNode body,
                                        @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                        @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.postPayrollPay(runId, body));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to pay payroll run", requestId, correlationId);
        }
    }

    @GetMapping("/payroll/payslips")
    public ResponseEntity<?> payslips(@RequestParam("run_id") String runId,
                                      @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                      @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getPayslips(runId));
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch payslips", requestId, correlationId);
        }
    }

    @GetMapping("/deductions")
    public ResponseEntity<?> deductions(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                        @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getDeductions());
        } catch (Exception e) {
            return failClose("HR_PAYROLL_UNAVAILABLE", "Unable to fetch deductions", requestId, correlationId);
        }
    }

    private static ResponseEntity<Map<String, Object>> failClose(String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
