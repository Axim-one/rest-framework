package one.axim.framework.mybatis.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 기동 시 방언을 정한다 (v1.5.0).
 *
 * <pre>
 * axim:
 *   mybatis:
 *     dialect: postgresql   # mysql | postgresql — 없으면 DataSource 로 자동 감지, 실패 시 mysql
 * </pre>
 *
 * <p>{@code @ComponentScan("one.axim.framework.mybatis")} 로 등록된다 (기존 필수 설정 그대로).
 */
@Component
public class XDialectConfig {

    public static final String PROPERTY = "axim.mybatis.dialect";

    private static final Logger log = LoggerFactory.getLogger(XDialectConfig.class);

    public XDialectConfig(Environment environment, ObjectProvider<DataSource> dataSources) {
        String configured = environment.getProperty(PROPERTY);
        if (StringUtils.hasText(configured)) {
            XDialect dialect = XDialect.fromName(configured);
            XDialectHolder.set(dialect);
            log.info("Axim mybatis dialect: {} ({}={})", dialect, PROPERTY, configured);
            return;
        }

        DataSource dataSource = dataSources.getIfUnique();
        XDialect detected = dataSource != null ? XDialect.detect(dataSource) : null;
        if (detected != null) {
            XDialectHolder.set(detected);
            log.info("Axim mybatis dialect: {} (auto-detected from DataSource)", detected);
        } else {
            // 미결정으로 둔다 — 첫 쿼리에서 XResultInterceptor 가 실제 커넥션으로 다시 감지하고,
            // 그래도 모르면 MySQL. 그 전까지 current() 는 MySQL.
            log.info("Axim mybatis dialect: not configured and DataSource detection unavailable; "
                    + "will detect on first query (fallback mysql)");
        }
    }
}
