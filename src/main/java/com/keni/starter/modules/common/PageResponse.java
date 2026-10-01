package com.keni.starter.modules.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A page of results, as a fixed shape.
 *
 * <p>Deliberately not Spring's {@code Page} itself. Page's JSON layout is an
 * implementation detail that has changed between releases, so serialising it directly
 * pins the API to a library version. This record is the contract instead.
 *
 * @param content the rows on this page, possibly empty
 * @param page zero based page index
 * @param size requested page size, not the number of rows actually returned
 * @param totalElements rows matching the query across every page
 * @param totalPages total pages available, which is 0 when there are no rows at all
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    boolean empty) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(),
        page.isEmpty());
  }

  /** The number of pages available, which is 0 rather than 1 for an empty result. */
  public boolean hasPages() {
    return totalPages > 0;
  }
}