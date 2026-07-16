package com.eldercare.dto;

import java.util.List;

/** 通用分页结果，页码从 1 开始。 */
public class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;
    private final int totalPages;
    private final int maxTotal;

    public PageResult(List<T> items, int page, int size, long total, int maxTotal) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = Math.max(1, (int) Math.ceil((double) total / size));
        this.maxTotal = maxTotal;
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotal() { return total; }
    public int getTotalPages() { return totalPages; }
    public int getMaxTotal() { return maxTotal; }
}
