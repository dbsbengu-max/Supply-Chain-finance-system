package com.scf.common.dto;

import java.util.List;

public record PageResponse<T>(
        int pageNo,
        int pageSize,
        long total,
        List<T> records
) {
    public static <T> PageResponse<T> of(int pageNo, int pageSize, long total, List<T> records) {
        return new PageResponse<>(pageNo, pageSize, total, records);
    }
}
