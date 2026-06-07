package zw.gov.mohcc.impilo.booking.core;

import java.util.UUID;

final class BookingNumberGenerator {

    private BookingNumberGenerator() {}

    static String nextBookingNumber() {
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "BK-" + shortId;
    }

    static String nextAppointmentNumber() {
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "AP-" + shortId;
    }
}
