package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.enums.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class PreferenceUpdateRequest {

    @NotNull
    private NotificationType type;
    private Boolean inAppEnabled;
    private Boolean pushEnabled;
    private Boolean emailEnabled;
    private Boolean telegramEnabled;
}
