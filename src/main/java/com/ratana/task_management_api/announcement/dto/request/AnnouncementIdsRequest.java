package com.ratana.task_management_api.announcement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Announcement ids for bulk publish / delete")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementIdsRequest {

    @NotEmpty
    @Size(max = 100)
    private Set<UUID> ids;
}
