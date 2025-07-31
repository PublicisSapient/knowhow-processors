package com.publicissapient.knowhow.processor.scm.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Utility class for handling pagination operations.
 * 
 * This class provides helper methods for creating and managing pagination
 * parameters, handling page boundaries, and creating common pagination configurations.
 */
public final class PaginationUtil {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 1000;
    public static final int MIN_PAGE_SIZE = 1;

    private PaginationUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a Pageable object with default settings.
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @return Pageable object
     */
    public static Pageable createPageable(int page, int size) {
        return createPageable(page, size, (Sort) null);
    }

    /**
     * Creates a Pageable object with sorting.
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @param sort the sort specification
     * @return Pageable object
     */
    public static Pageable createPageable(int page, int size, Sort sort) {
        int validatedPage = Math.max(0, page);
        int validatedSize = validatePageSize(size);
        
        if (sort != null) {
            return PageRequest.of(validatedPage, validatedSize, sort);
        } else {
            return PageRequest.of(validatedPage, validatedSize);
        }
    }

    /**
     * Creates a Pageable object with sorting by field and direction.
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @param sortField the field to sort by
     * @param sortDirection the sort direction (ASC or DESC)
     * @return Pageable object
     */
    public static Pageable createPageable(int page, int size, String sortField, Sort.Direction sortDirection) {
        Sort sort = Sort.by(sortDirection, sortField);
        return createPageable(page, size, sort);
    }

    /**
     * Creates a Pageable object with multiple sort fields.
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @param sortFields array of sort field specifications
     * @return Pageable object
     */
    public static Pageable createPageable(int page, int size, Sort.Order... sortFields) {
        Sort sort = Sort.by(sortFields);
        return createPageable(page, size, sort);
    }

    /**
     * Creates a default Pageable object for recent items (sorted by creation date descending).
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @return Pageable object sorted by createdAt descending
     */
    public static Pageable createRecentItemsPageable(int page, int size) {
        return createPageable(page, size, "createdAt", Sort.Direction.DESC);
    }

    /**
     * Creates a default Pageable object for updated items (sorted by update date descending).
     * 
     * @param page the page number (0-based)
     * @param size the page size
     * @return Pageable object sorted by updatedAt descending
     */
    public static Pageable createUpdatedItemsPageable(int page, int size) {
        return createPageable(page, size, "updatedAt", Sort.Direction.DESC);
    }

    /**
     * Validates and adjusts page size to be within acceptable bounds.
     * 
     * @param size the requested page size
     * @return validated page size
     */
    public static int validatePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Calculates the total number of pages for a given total count and page size.
     * 
     * @param totalCount the total number of items
     * @param pageSize the page size
     * @return total number of pages
     */
    public static int calculateTotalPages(long totalCount, int pageSize) {
        if (totalCount <= 0 || pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    /**
     * Calculates the offset for a given page and page size.
     * 
     * @param page the page number (0-based)
     * @param pageSize the page size
     * @return the offset
     */
    public static long calculateOffset(int page, int pageSize) {
        return (long) Math.max(0, page) * Math.max(1, pageSize);
    }

    /**
     * Checks if there is a next page available.
     * 
     * @param currentPage the current page number (0-based)
     * @param totalPages the total number of pages
     * @return true if there is a next page, false otherwise
     */
    public static boolean hasNextPage(int currentPage, int totalPages) {
        return currentPage + 1 < totalPages;
    }

    /**
     * Checks if there is a previous page available.
     * 
     * @param currentPage the current page number (0-based)
     * @return true if there is a previous page, false otherwise
     */
    public static boolean hasPreviousPage(int currentPage) {
        return currentPage > 0;
    }

    /**
     * Creates pagination metadata for API responses.
     * 
     * @param currentPage the current page number (0-based)
     * @param pageSize the page size
     * @param totalElements the total number of elements
     * @return PaginationMetadata object
     */
    public static PaginationMetadata createPaginationMetadata(int currentPage, int pageSize, long totalElements) {
        int totalPages = calculateTotalPages(totalElements, pageSize);
        boolean hasNext = hasNextPage(currentPage, totalPages);
        boolean hasPrevious = hasPreviousPage(currentPage);
        
        return PaginationMetadata.builder()
            .currentPage(currentPage)
            .pageSize(pageSize)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .hasNext(hasNext)
            .hasPrevious(hasPrevious)
            .isFirst(currentPage == 0)
            .isLast(currentPage == totalPages - 1 || totalPages == 0)
            .build();
    }

    /**
     * Data class for pagination metadata.
     */
    public static class PaginationMetadata {
        private final int currentPage;
        private final int pageSize;
        private final long totalElements;
        private final int totalPages;
        private final boolean hasNext;
        private final boolean hasPrevious;
        private final boolean isFirst;
        private final boolean isLast;

        private PaginationMetadata(Builder builder) {
            this.currentPage = builder.currentPage;
            this.pageSize = builder.pageSize;
            this.totalElements = builder.totalElements;
            this.totalPages = builder.totalPages;
            this.hasNext = builder.hasNext;
            this.hasPrevious = builder.hasPrevious;
            this.isFirst = builder.isFirst;
            this.isLast = builder.isLast;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getCurrentPage() { return currentPage; }
        public int getPageSize() { return pageSize; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public boolean isHasNext() { return hasNext; }
        public boolean isHasPrevious() { return hasPrevious; }
        public boolean isFirst() { return isFirst; }
        public boolean isLast() { return isLast; }

        public static class Builder {
            private int currentPage;
            private int pageSize;
            private long totalElements;
            private int totalPages;
            private boolean hasNext;
            private boolean hasPrevious;
            private boolean isFirst;
            private boolean isLast;

            public Builder currentPage(int currentPage) { this.currentPage = currentPage; return this; }
            public Builder pageSize(int pageSize) { this.pageSize = pageSize; return this; }
            public Builder totalElements(long totalElements) { this.totalElements = totalElements; return this; }
            public Builder totalPages(int totalPages) { this.totalPages = totalPages; return this; }
            public Builder hasNext(boolean hasNext) { this.hasNext = hasNext; return this; }
            public Builder hasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; return this; }
            public Builder isFirst(boolean isFirst) { this.isFirst = isFirst; return this; }
            public Builder isLast(boolean isLast) { this.isLast = isLast; return this; }

            public PaginationMetadata build() {
                return new PaginationMetadata(this);
            }
        }
    }
}