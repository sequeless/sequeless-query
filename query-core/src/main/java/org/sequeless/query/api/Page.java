package org.sequeless.query.api;

/**
 * A page request for {@link org.sequeless.query.spi.BrowsePort#browse}: a 0-based page number and
 * a page size. Offset paging (spec 14, section 4.3).
 *
 * <p>A page whose number is past the last page is not an error: the browse returns no items and
 * the correct total.
 *
 * @param number the 0-based page number; must not be negative
 * @param size the page size; must be between 1 and 500 inclusive
 */
public record Page(int number, int size) {

    /**
     * Validates the page number and size.
     *
     * @throws IllegalArgumentException if {@code number} is negative, or {@code size} is not
     *     between 1 and 500 inclusive
     */
    public Page {
        if (number < 0) {
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (size < 1 || size > 500) {
            throw new IllegalArgumentException("Page size must be between 1 and 500");
        }
    }
}
