package one.axim.framework.mybatis.type;

import one.axim.framework.core.utils.NamingConvert;

import java.util.HashMap;

/**
 * Created by dudgh on 2017. 5. 30..
 */
public class XDataMap extends HashMap<String, Object> {

    @Override
    public Object put(String key, Object value) {
        return super.put(NamingConvert.toCamelCase(key), value);
    }

    @Override
    public void putAll(java.util.Map<? extends String, ?> m) {
        if (m != null) {
            m.forEach(this::put);
        }
    }
}