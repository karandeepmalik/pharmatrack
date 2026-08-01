package com.pharma.medicinestock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private int number;
    private int size;
    /** Optional aggregate over the full matching set, not just this page — null unless the
     *  caller supplies one (e.g. View Past Medicine Dispatches' total quantity). */
    private BigDecimal totalQuantity;

    public static <T> PagedResponse<T> of(Page<T> page) {
        return of(page, null);
    }

    public static <T> PagedResponse<T> of(Page<T> page, BigDecimal totalQuantity) {
        return new PagedResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast(),
            page.getNumber(),
            page.getSize(),
            totalQuantity
        );
    }
}
