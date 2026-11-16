package com.ratana.task_management_api.auth.dto.response;

import java.util.List;

public class JwtResponse {

    private String token;
    private String type = "Bearer";
    private String refreshToken;
    private Long id;
    private String username;
    private String email;
    private List<String> roles;
}
