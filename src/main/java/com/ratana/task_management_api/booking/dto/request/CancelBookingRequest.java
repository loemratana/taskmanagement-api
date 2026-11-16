package com.ratana.task_management_api.booking.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelBookingRequest {

    @Size(max = 500)
    private String reason;
}
