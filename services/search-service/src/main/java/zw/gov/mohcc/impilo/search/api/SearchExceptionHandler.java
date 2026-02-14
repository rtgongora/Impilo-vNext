package zw.gov.mohcc.impilo.search.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.search.service.DocumentNotFoundException;
import zw.gov.mohcc.impilo.search.service.IndexNotFoundException;

import java.util.UUID;

@RestControllerAdvice
public class SearchExceptionHandler {

    @ExceptionHandler(IndexNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleIndexNotFound(IndexNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleDocumentNotFound(DocumentNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    private ResponseEntity<ErrorEnvelope> notFound(String message) {
        RequestContext ctx = RequestContextHolder.get();
        String reqId = ctx != null ? ctx.requestId() : UUID.randomUUID().toString();
        String corrId = ctx != null ? ctx.correlationId() : UUID.randomUUID().toString();
        ErrorEnvelope envelope = ErrorEnvelope.of("NOT_FOUND", message, reqId, corrId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope);
    }
}
