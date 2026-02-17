package one.axim.framework.core.data;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by dudgh on 2017. 6. 5..
 */
public class XPage<T> {

    private Integer totalCount;

    private Integer size;

    private Integer offset;

    private List<XOrder> orders;

    private Integer page;

    private List<T> pageRows;

    public Integer getTotalCount() {

        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {

        this.totalCount = totalCount;
    }

    public Integer getSize() {

        return size;
    }

    public void setSize(Integer size) {

        this.size = size;
    }

    public Integer getOffset() {

        return offset;
    }

    public void setOffset(Integer offset) {

        this.offset = offset;
    }

    public List<XOrder> getOrders() {

        return orders;
    }

    public void setOrders(List<XOrder> orders) {

        this.orders = orders;
    }

    public Integer getPage() {

        return page;
    }

    public void setPage(Integer page) {

        this.page = page;
    }

    public List<T> getPageRows() {

        return pageRows;
    }

    public void setPageRows(List<T> pageRows) {

        this.pageRows = pageRows;
    }

    public void addPageRows(List<T> pageRows) {

        if (pageRows == null || pageRows.isEmpty()) {
            return;
        }

        if (this.pageRows != null)
            this.pageRows = Stream.concat(this.pageRows.stream(), pageRows.stream()).collect(Collectors.toList());
        else
            this.pageRows = pageRows;
    }

    @SuppressWarnings("unchecked")
    public void setPageRowsByObject(Object object) {
        if (object == null) {
            this.pageRows = null;
        } else if (object instanceof List<?>) {
            this.pageRows = (List<T>) object;
        } else {
            throw new IllegalArgumentException("Expected List but got " + object.getClass().getName());
        }
    }

    @Override
    public String toString() {
        return "XPage{totalCount=" + totalCount
                + ", page=" + page
                + ", size=" + size
                + ", rows=" + (pageRows != null ? pageRows.size() : 0) + "}";
    }

    public boolean getHasNext() {

        if(this.page == null) return false;
        if(this.totalCount == null) return false;
        if(this.size == null || this.size == 0) return false;

        int totalPages = (this.totalCount + this.size - 1) / this.size;
        return this.page < totalPages;
    }
}