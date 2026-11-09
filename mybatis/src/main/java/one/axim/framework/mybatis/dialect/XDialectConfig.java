package one.axim.framework.mybatis.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 기동 시 방언 설정을 읽는다 (v1.5.0).
 *
 * <pre>
 * axim:
 *   mybatis:
 *     dialect: postgresql   # mysql | postgresql — 없으면 자동 감지, 실패 시 mysql
 * </pre>
 *
 * <p>설정이 없으면 기동 시점에는 DB 에 붙지 않는다. 첫 쿼리에서 {@code XResultInterceptor} 가
 * 그 쿼리의 커넥션(= DataSource 커넥션)의 {@code DatabaseMetaData.getDatabaseProductName()} 으로
 * 감지한다. 1.4.x MySQL 앱의 기동 동작(기동 중 DB 접속 없음)을 바꾸지 않기 위해서다 — 기동 시
 * 감지를 하면 DB 장애 중 재기동이 커넥션 타임아웃(Hikari 기본 30초)만큼 늦어진다.
 *
 * <p>{@code @ComponentScan("one.axim.framework.mybatis")} 로 등록된다 (기존 필수 설정 그대로).
 */
@Component
public class XDialectConfig {

    public static final String PROPERTY = "axim.mybatis.dialect";

    private static final Logger log = LoggerFactory.getLogger(XDialectConfig.class);

    public XDialectConfig(Environment environment) {
        String configured = environment.getProperty(PROPERTY);
        if (StringUtils.hasText(configured)) {
            XDialect dialect = XDialect.fromName(configured);
            XDialectHolder.set(dialect);
            log.info("Axim mybatis dialect: {} ({}={})", dialect, PROPERTY, configured);
        } else {
            // 이 앱 기준 미결정 — 첫 쿼리에서 감지. 그 전까지 current() 는 MySQL.
            XDialectHolder.reset();
            log.debug("Axim mybatis dialect: {} not set; will auto-detect on first query (fallback mysql)", PROPERTY);
        }
    }
}
