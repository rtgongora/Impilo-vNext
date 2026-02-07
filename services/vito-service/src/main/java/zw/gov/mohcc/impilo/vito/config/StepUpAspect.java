package zw.gov.mohcc.impilo.vito.config;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

import java.util.Map;

/**
 * AOP aspect that enforces step-up authentication on annotated methods.
 * If X-Step-Up-Token header is missing or empty, returns 401 STEP_UP_REQUIRED.
 */
@Aspect
@Component
public class StepUpAspect {

    @Around("@annotation(stepUpRequired)")
    public Object enforceStepUp(ProceedingJoinPoint joinPoint, StepUpRequired stepUpRequired) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String stepUpToken = request.getHeader("X-Step-Up-Token");

            if (stepUpToken == null || stepUpToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "decision", "STEP_UP_REQUIRED",
                                "reason", stepUpRequired.reason(),
                                "stepUpMethods", new String[]{"OTP", "BIOMETRIC"}
                        ));
            }
        }
        return joinPoint.proceed();
    }
}
