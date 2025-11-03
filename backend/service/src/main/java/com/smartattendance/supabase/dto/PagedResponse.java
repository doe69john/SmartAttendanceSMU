package com.smartattendance.supabase.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PagedResponse", description = "Generic paginated response wrapper")
public class PagedResponse<T> {

    @JsonProperty("items")
    @ArraySchema(arraySchema = @Schema(description = "Requested page of data"))
    private List<T> items;

    @JsonProperty("page")
    @Schema(description = "Zero-based page index")
    private int page;

    @JsonProperty("size")
    @Schema(description = "Size of the page requested")
    private int size;

    @JsonProperty("total_items")
    @Schema(description = "Total number of items available")
    private long totalItems;

    @JsonProperty("total_pages")
    @Schema(description = "Total number of pages available")
    private int totalPages;

    public PagedResponse() {
    }

    public PagedResponse(List<T> items, int page, int size, long totalItems, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
