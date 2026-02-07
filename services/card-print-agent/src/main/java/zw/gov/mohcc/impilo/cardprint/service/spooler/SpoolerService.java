package zw.gov.mohcc.impilo.cardprint.service.spooler;

import java.io.IOException;
import java.util.UUID;

/**
 * Interface for print output spooling. Implementations determine
 * where rendered PDFs are written (file system, network printer, etc.).
 */
public interface SpoolerService {

    /**
     * Spools a rendered PDF to the target output destination.
     *
     * @param jobId    the print job identifier
     * @param fileName the output file name
     * @param pdfData  the rendered PDF bytes
     * @throws IOException if the spool operation fails
     */
    void spool(UUID jobId, String fileName, byte[] pdfData) throws IOException;

    /**
     * Retrieves a previously spooled PDF by job ID.
     *
     * @param jobId    the print job identifier
     * @param fileName the file name
     * @return the PDF bytes, or null if not found
     * @throws IOException if the read operation fails
     */
    byte[] retrieve(UUID jobId, String fileName) throws IOException;
}
