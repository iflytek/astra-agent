package com.iflytek.astra.console.toolkit.config.jooq;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    @Bean
    public DSLContext dslContext() {
        Settings settings = new Settings()
                // Do not render schema
                .withRenderSchema(false)
                // Identifiers do not automatically include quotes (we handle whitelist/escaping ourselves)
                .withRenderQuotedNames(RenderQuotedNames.NEVER)
                // Do not execute SQL
                .withExecuteLogging(false)
                .withStatementType(org.jooq.conf.StatementType.STATIC_STATEMENT);
        // STATIC_STATEMENT: Only construct SQL template/parameters, do not attempt actual execution

        return DSL.using(SQLDialect.POSTGRES, settings);
    }
}
