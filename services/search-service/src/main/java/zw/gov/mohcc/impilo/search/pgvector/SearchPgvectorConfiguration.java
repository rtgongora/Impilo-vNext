package zw.gov.mohcc.impilo.search.pgvector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(SearchPgvectorProperties.class)
@ConditionalOnClass(JdbcTemplate.class)
public class SearchPgvectorConfiguration {

    @Bean
    PgvectorAnnSupport pgvectorAnnSupport(DataSource dataSource, SearchPgvectorProperties properties) {
        return new PgvectorAnnSupport(new JdbcTemplate(dataSource), properties);
    }
}
