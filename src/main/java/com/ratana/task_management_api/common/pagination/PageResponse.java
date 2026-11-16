package com.ratana.task_management_api.common.pagination;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Paginated result wrapper")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    @Schema(description = "The page content")
    private List<T> content;

    @Schema(description = "Current page number, zero-indexed", example = "0")
    private int page;

    @Schema(description = "Items per page", example = "20")
    private int size;

    @Schema(description = "Total number of elements across all pages", example = "147")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "8")
    private int totalPages;

    @Schema(description = "Whether this is the first page", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the last page", example = "false")
    private boolean last;

    @Schema(description = "Whether the page content is empty", example = "false")
    private boolean empty;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();

    }


}
