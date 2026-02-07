package zw.gov.mohcc.impilo.tshepo.consent.exception;

/**
 * Thrown when a share link cannot be found by its token or ID.
 */
public class ShareLinkNotFoundException extends RuntimeException {

    public ShareLinkNotFoundException(String message) {
        super(message);
    }
}
