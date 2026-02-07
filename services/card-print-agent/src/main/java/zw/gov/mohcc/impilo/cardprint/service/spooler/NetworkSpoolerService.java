package zw.gov.mohcc.impilo.cardprint.service.spooler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

/**
 * Network printer spooler stub. Activated when {@code card-print.spooler.type=NETWORK}.
 *
 * This is a placeholder implementation for future network printer integration
 * (e.g., IPP, LPR, or vendor-specific APIs for CR-80 card printers like
 * Fargo, Magicard, or Evolis).
 */
@Service
@ConditionalOnProperty(name = "card-print.spooler.type", havingValue = "NETWORK")
public class NetworkSpoolerService implements SpoolerService {

    private static final Logger log = LoggerFactory.getLogger(NetworkSpoolerService.class);

    @Override
    public void spool(UUID jobId, String fileName, byte[] pdfData) throws IOException {
        // TODO: Implement network printer integration
        // This would typically involve:
        // 1. Discovering available printers on the network
        // 2. Sending the PDF to the printer via IPP/LPR protocol
        // 3. Monitoring print job status via SNMP or printer API
        log.warn("Network spooler is a stub implementation. Job {} not actually sent to printer.", jobId);
        throw new UnsupportedOperationException(
                "Network printer integration is not yet implemented. Configure card-print.spooler.type=FILE for file output.");
    }

    @Override
    public byte[] retrieve(UUID jobId, String fileName) throws IOException {
        log.warn("Network spooler does not support PDF retrieval. Job: {}", jobId);
        return null;
    }
}
