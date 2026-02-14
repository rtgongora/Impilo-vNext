package zw.gov.mohcc.impilo.search.service;

public class IndexNotFoundException extends RuntimeException {
    public IndexNotFoundException(String id) {
        super("Index definition not found: " + id);
    }
}
