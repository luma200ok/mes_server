package com.mes.domain.workorder.dto;

import java.util.List;

public record WorkOrderUploadResult(
        int totalRows,
        int successCount,
        int failCount,
        List<RowError> errors
) {
    public record RowError(int row, String reason) {}
}
