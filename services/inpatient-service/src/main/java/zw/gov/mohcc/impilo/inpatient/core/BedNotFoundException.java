package zw.gov.mohcc.impilo.inpatient.core;

public class BedNotFoundException extends RuntimeException {

    public BedNotFoundException(String message) {
        super(message);
    }
}
