package zw.gov.mohcc.impilo.rules.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.rules.engine.RuleExpressionInvalidException;

import java.util.UUID;

@RestControllerAdvice
public class RulesExceptionHandler {

    @ExceptionHandler(RuleExpressionInvalidException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidExpression(RuleExpressionInvalidException ex) {
        RequestContext ctx = RequestContextHolder.get();
        String reqId = ctx != null ? ctx.requestId() : UUID.randomUUID().toString();
        String corrId = ctx != null ? ctx.correlationId() : UUID.randomUUID().toString();
        ErrorEnvelope envelope = ErrorEnvelope.of(
                "RULE_EXPRESSION_INVALID", ex.getMessage(), reqId, corrId);
        return ResponseEntity.badRequest().body(envelope);
    }
}
