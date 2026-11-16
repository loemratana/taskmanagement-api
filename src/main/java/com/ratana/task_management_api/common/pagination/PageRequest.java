package com.ratana.task_management_api.common.pagination;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Schema(description = "Standard pagination and sorting parameters")
@Data
public class PageRequest {

    @Schema(description = "Page number, zero-indexed", example = "0", defaultValue = "0")
    @Min(0)
    private Integer page;

    @Schema(description = "Number of items per page", example = "20", defaultValue = "20")
    @Min(1) @Max(100)
    private Integer size;

    @Schema(description = "Field to sort by", example = "name", defaultValue = "createdAt")
    private String sortBy;

    @Schema(description = "Sort direction", example = "ASC", allowableValues = {"ASC", "DESC"}, defaultValue = "DESC")
    private String direction;


    // Compact constructor applies sane defaults if the client omits any field
    public PageRequest(Integer page, Integer size, String sortBy, String direction) {
        this.page = (page == null || page < 0) ? 0 : page;
        this.size = (size == null || size < 1) ? 20 : Math.min(size, 100);
        this.sortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        this.direction = (direction == null || direction.isBlank()) ? "DESC" : direction;
    }

    public static PageRequest of(Integer page, Integer size, String sortBy, String direction) {
        return new PageRequest(page, size, sortBy, direction);
    }


}
