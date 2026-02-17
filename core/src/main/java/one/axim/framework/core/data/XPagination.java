package one.axim.framework.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by dudgh on 2017. 6. 10..
 */
public class XPagination {

    private int offset;

    private int size;

    private int page;

    private final ArrayList<XOrder> orders;

    public XPagination() {

        orders = new ArrayList<>();
    }

    public int getOffset() {

        if (page > 0 && size > 0) {
            return (page - 1) * size;
        }
        return offset;
    }

    public void setOffset(int offset) {

        this.offset = offset;
    }

    public int getSize() {

        return size;
    }

    public void setSize(int size) {

        this.size = size;
    }

    public void addOrder(XOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Order must not be null");
        }
        this.orders.add(order);
    }

    public boolean hasOrder() {

        return !orders.isEmpty();
    }

    public List<XOrder> getOrders() {

        return Collections.unmodifiableList(this.orders);
    }

    public boolean hasLimit() {
        return !(size == 0 && offset == 0);
    }

    public int getPage() {

        return page;
    }

    public void setPage(int page) {

        this.page = page;
    }
}