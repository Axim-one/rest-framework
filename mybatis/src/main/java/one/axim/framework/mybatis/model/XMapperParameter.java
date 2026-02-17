package one.axim.framework.mybatis.model;

import one.axim.framework.core.data.XPagination;
import one.axim.framework.mybatis.utils.XPaginationSql;

/**
 * Created by dudgh on 2017. 5. 29..
 */
public class XMapperParameter {

    private Object model;

    private Long lastInsertedId;

    private String rawQuery;

    private Object where;

    private XPagination pagination;

    private Class<?> resultClass;

    private String customQuery;

    public XMapperParameter() {

    }

    public XMapperParameter(Object model) {

        this.model = model;
    }

    public Object getModel() {

        return model;
    }

    public void setModel(Object model) {

        this.model = model;
    }

    public Long getLastInsertedId() {
        return lastInsertedId;
    }

    public void setLastInsertedId(Long lastInsertedId) {
        this.lastInsertedId = lastInsertedId;
    }

    public String getRawQuery() {

        String returnQuery = rawQuery;

        if(returnQuery !=null) {

            returnQuery = returnQuery.replaceAll("(AND \\(\\))|(OR \\(\\))", "");

            if (pagination != null) {
                returnQuery += XPaginationSql.orderBy(pagination);
                returnQuery += XPaginationSql.limit(pagination);
            }
        }

        return returnQuery;
    }

    public void setRawQuery(String rawQuery) {

        this.rawQuery = rawQuery;
    }

    public Object getWhere() {

        return where;
    }

    public void setWhere(Object where) {

        this.where = where;
    }

    public void setPagination(XPagination pagination) {

        this.pagination = pagination;
    }

    public XPagination getPagination() {
        return pagination;
    }

    public Class<?> getResultClass() {

        return resultClass;
    }

    public void setResultClass(Class<?> resultClass) {

        this.resultClass = resultClass;
    }

    public String getCustomQuery() {
        return customQuery;
    }

    public void setCustomQuery(String customQuery) {
        this.customQuery = customQuery;
    }
}
