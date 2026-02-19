package one.axim.framework.core.data;

import one.axim.framework.core.utils.NamingConvert;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a sort order for a single column, used with {@link XPagination}.
 *
 * <p>The column name is specified in camelCase (matching the entity field name) and
 * is automatically converted to snake_case in the generated SQL.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Sort by createdAt descending
 * XOrder order = new XOrder("createdAt", XDirection.DESC);
 *
 * // Add to pagination
 * XPagination pagination = new XPagination();
 * pagination.setPage(1);
 * pagination.setSize(10);
 * pagination.addOrder(order);
 * pagination.addOrder(new XOrder("name", XDirection.ASC));
 * }</pre>
 *
 * @see XPagination
 * @see XDirection
 */
public class XOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private XDirection direction;

    private String column;

    public XOrder(String column, XDirection direction) {

        this.direction = direction;
        this.column = column;
    }

    public XOrder() {

    }

    public XDirection getDirection() {

        return direction;
    }

    public void setDirection(XDirection direction) {

        this.direction = direction;
    }

    public String getColumn() {

        return column;
    }

    public void setColumn(String column) {

        this.column = column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XOrder xOrder)) return false;
        return direction == xOrder.direction && Objects.equals(column, xOrder.column);
    }

    @Override
    public int hashCode() {
        return Objects.hash(direction, column);
    }

    @Override
    public String toString() {

        return NamingConvert.toUnderScoreName(this.column) + " " + this.direction;
    }
}