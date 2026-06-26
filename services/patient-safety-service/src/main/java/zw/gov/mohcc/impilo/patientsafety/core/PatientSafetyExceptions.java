package zw.gov.mohcc.impilo.patientsafety.core;

/** Domain exceptions and their HTTP mapping (see {@code ApiExceptionHandler}). */
public final class PatientSafetyExceptions {

    private PatientSafetyExceptions() {}

    /** Maps to 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    /** Maps to 409 — an operation invalid for the entity's current state. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
