package zw.gov.mohcc.impilo.learning.config;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.core.env.Environment;

public final class LearningDatabaseBootstrap {

    private static final String POSTGRES_PREFIX = "jdbc:postgresql:";

    private LearningDatabaseBootstrap() {
    }

    public static void createDatabaseIfMissing(Environment environment) {
        if (!environment.getProperty("learning.database.auto-create-enabled", Boolean.class, true)) {
            return;
        }

        String datasourceUrl = environment.getProperty("spring.datasource.url", "");
        if (!datasourceUrl.startsWith(POSTGRES_PREFIX)) {
            return;
        }

        DatabaseTarget target = DatabaseTarget.from(datasourceUrl);
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        String maintenanceDatabase = environment.getProperty("learning.database.maintenance-database", "postgres");

        String maintenanceUrl = target.maintenanceUrl(maintenanceDatabase);
        try (Connection connection = DriverManager.getConnection(maintenanceUrl, username, password)) {
            if (!databaseExists(connection, target.databaseName())) {
                createDatabase(connection, target.databaseName());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to ensure learning database exists: " + target.databaseName(), ex);
        }
    }

    private static boolean databaseExists(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("select 1 from pg_database where datname = ?")) {
            statement.setString(1, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void createDatabase(Connection connection, String databaseName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("create database " + quoteIdentifier(databaseName));
        }
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid database name");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record DatabaseTarget(String server, String databaseName, String query) {

        static DatabaseTarget from(String datasourceUrl) {
            String raw = datasourceUrl.substring(POSTGRES_PREFIX.length());
            URI uri = URI.create(raw.startsWith("//") ? "postgresql:" + raw : "postgresql://" + raw);
            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                throw new IllegalArgumentException("PostgreSQL datasource URL must include a database name");
            }
            String server = raw.substring(0, raw.indexOf(path));
            return new DatabaseTarget(server, path.substring(1), uri.getRawQuery());
        }

        String maintenanceUrl(String maintenanceDatabase) {
            String url = POSTGRES_PREFIX + server + "/" + maintenanceDatabase;
            return query == null || query.isBlank() ? url : url + "?" + query;
        }
    }
}
