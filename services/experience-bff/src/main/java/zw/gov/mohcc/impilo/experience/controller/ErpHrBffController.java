package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.HrPayrollServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/erp/hr")
public class ErpHrBffController {

    private final HrPayrollServiceClient client;
    private final VashandiServiceClient vashandiClient;

    public ErpHrBffController(HrPayrollServiceClient client, VashandiServiceClient vashandiClient) {
        this.client = client;
        this.vashandiClient = vashandiClient;
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

    // Leave + attendance are owned by Vashandi (workforce SoR). These views read Vashandi
    // by provider-worker-id (the Employee.providerId bridge the UI already holds).

    @GetMapping("/leave/requests")
    public ResponseEntity<?> leaveRequests(@RequestParam("provider_worker_id") String providerWorkerId,
                                           @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                           @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(vashandiClient.getLeaveByProvider(providerWorkerId));
        } catch (Exception e) {
            return failClose("VASHANDI_UNAVAILABLE", "Unable to fetch leave from Vashandi", requestId, correlationId);
        }
    }

    @GetMapping("/attendance")
    public ResponseEntity<?> attendance(@RequestParam("provider_worker_id") String providerWorkerId,
                                        @RequestParam(value = "year", required = false) Integer year,
                                        @RequestParam(value = "month", required = false) Integer month,
                                        @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                        @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        try {
            return ResponseEntity.ok(vashandiClient.getAttendanceEventsByProvider(providerWorkerId, y, m));
        } catch (Exception e) {
            return failClose("VASHANDI_UNAVAILABLE", "Unable to fetch attendance from Vashandi", requestId, correlationId);
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
