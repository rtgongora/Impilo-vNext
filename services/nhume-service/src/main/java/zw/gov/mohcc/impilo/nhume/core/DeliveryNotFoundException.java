package zw.gov.mohcc.impilo.nhume.core;

import java.util.UUID;

public class DeliveryNotFoundException extends RuntimeException {
    public DeliveryNotFoundException(UUID deliveryId) {
        super("Nhume delivery not found: " + deliveryId);
    }
}
