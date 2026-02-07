package zw.gov.mohcc.impilo.tshepo.consent.exception;

/**
 * Thrown when a share link has expired, been revoked, or exceeded its max uses.
 */
public class ShareLinkExpiredException extends RuntimeException {

    public ShareLinkExpiredException(String message) {
        super(message);
    }
}
