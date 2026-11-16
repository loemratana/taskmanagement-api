package com.ratana.task_management_api.auth.dto.response;


import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeviceMetadata {

    String  deviceName;
    String deviceType;
    String ipAddress;
    String userAgent;


    public static DeviceMetadata unknown()
    {
        return DeviceMetadata.builder().deviceName("unknown").deviceType("unknown").userAgent("unknown").build();
    }



}
