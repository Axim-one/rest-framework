package one.axim.framework.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pagination request model specifying page number, page size, and sort orders.
 *
 * <p>Pass an {@code XPagination} instance to repository query methods
 * (e.g., {@code findAll(XPagination)}, {@code findWhere(XPagination, Map)}) to
 * receive an {@link XPage} result with automatic COUNT query and LIMIT/OFFSET.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * XPagination pagination = new XPagination();
 * pagination.setPage(1);
 * pagination.setSize(20);
 * pagination.addOrder(new XOrder("createdAt", XDirection.DESC));
 *
 * XPage<User> result = userRepository.findAll(pagination);
 * }</pre>
 *
 * @see XPage
 * @see XOrder
 * @see XDirection
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