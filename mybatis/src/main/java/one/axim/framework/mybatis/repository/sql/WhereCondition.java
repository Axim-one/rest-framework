package one.axim.framework.mybatis.repository.sql;

import one.axim.framework.core.utils.NamingConvert;

/**
 * Created by dudgh on 2017. 5. 29..
 */
public class WhereCondition {

    private final static String QUERY_FORMAT = " %s %s %s #{where.%s} ";

    private final static String QUERY_FORMAT_NOT_VALUE = " %s %s %s ";

    private String andOrType;

    private String name;

    private ConditionType conditionType;

    private boolean isFirstCondition;

    private String value;

    private boolean isBracket = false;

    public WhereCondition(String value) {

        this.value = value;
    }

    public WhereCondition(String value, boolean bracket) {

        this.value = value;
        this.isBracket = bracket;
    }

    public WhereCondition(String l, ConditionType type, String name) {

        this.andOrType = l;
        this.name = name;
        this.conditionType = type;
    }

    @Override
    public String toString() {

        String query = "";

        if (this.value != null) {
            return value;
        } else {
            if (conditionType.isHasValue()) {
                query = String.format(QUERY_FORMAT, this.andOrType, NamingConvert.toUnderScoreName(name),
                        this.conditionType, this.name);
            } else {
                query = String.format(QUERY_FORMAT_NOT_VALUE, this.andOrType, NamingConvert.toUnderScoreName(name),
                        this.conditionType);
            }
        }

        return query;
    }

    public boolean isFirstCondition() {

        return isFirstCondition;
    }

    public void setFirstCondition(boolean firstCondition) {

        if (firstCondition) {
            this.andOrType = "";
        }

        isFirstCondition = firstCondition;
    }

    public boolean isBracket() {

        return isBracket;
    }
}
