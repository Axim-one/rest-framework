package one.axim.framework.mybatis.repository.sql;

/**
 * Created by dudgh on 2017. 5. 29..
 */
public enum ConditionType {
    EQUAL("="),
    NOT_EQUAL("<>"),
    LESS("<"),
    LESS_EQUAL("<="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    LIKE("LIKE"),
    IS_NOT_NULL("IS NOT NULL", false),
    IS_NULL("IS NULL", false);

    private String value;

    private boolean isHasValue;

    ConditionType(String s) {

        this.value = s;
        isHasValue = true;
    }

    ConditionType(String s, boolean hasValue) {

        this.value = s;
        isHasValue = hasValue;
    }

    public String getValue() {

        return value;
    }

    @Override
    public String toString() {

        return value;
    }

    public boolean isHasValue() {

        return isHasValue;
    }
}
