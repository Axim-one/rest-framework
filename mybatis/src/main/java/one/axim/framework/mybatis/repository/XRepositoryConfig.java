package one.axim.framework.mybatis.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Created by dudgh on 2017. 6. 4..
 */
@Component
public class XRepositoryConfig {

    private static boolean isDebug;

    public XRepositoryConfig(@Autowired Environment environment) {

        XRepositoryConfig.isDebug = readBooleanValue(environment, "spring.debug");
    }

    public static boolean isDebug() {

        return XRepositoryConfig.isDebug;
    }

    private boolean readBooleanValue(Environment env, String name) {

        String value = env.getProperty(name);
        return (value != null && !value.equals("false"));
    }
}
