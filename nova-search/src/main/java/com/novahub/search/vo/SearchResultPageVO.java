package com.novahub.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "搜索响应分页结果")
public class SearchResultPageVO<T> {

    @Schema(description = "结果列表")
    private java.util.List<T> records;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "当前页")
    private long page;

    @Schema(description = "每页大小")
    private long pageSize;

    @Schema(description = "总页数")
    private long pages;

    @Schema(description = "是否还有下一页")
    private boolean hasNext;

    public static <T> SearchResultPageVO<T> of(java.util.List<T> records, long total, long page, long pageSize) {
        SearchResultPageVO<T> vo = SearchResultPageVO.<T>builder()
                .records(records)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .pages(pageSize > 0 ? (total + pageSize - 1) / pageSize : 0)
                .hasNext(page * pageSize < total)
                .build();
        return vo;
    }
}
