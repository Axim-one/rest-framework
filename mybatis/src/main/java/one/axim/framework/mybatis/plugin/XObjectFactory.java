package one.axim.framework.mybatis.plugin;

import one.axim.framework.mybatis.type.XDataMap;

import org.apache.ibatis.reflection.factory.DefaultObjectFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Created by dudgh on 2017. 5. 30..
 */
public class XObjectFactory extends DefaultObjectFactory {

    private static final long serialVersionUID = 4576592418878031661L;

    @Override
    public <T> T create(Class<T> type) {
        if (type == HashMap.class) {
            return (T) new XDataMap();
        }
        return super.create(type);
    }

    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        if (type == HashMap.class) {
            return (T) new XDataMap();
        }
        return super.create(type, constructorArgTypes, constructorArgs);
    }

    @Override
    public void setProperties(Properties properties) {
        super.setProperties(properties);
    }
}
