package zw.gov.mohcc.impilo.mushex.service;

/**
 * Raised when a tenant-scoped MusheX platform record cannot be found.
 */
public class PlatformResourceNotFoundException extends IllegalArgumentException {

    public static final String CODE = "PLATFORM_RECORD_NOT_FOUND";

    public PlatformResourceNotFoundException(String message) {
        super(message);
    }
}
