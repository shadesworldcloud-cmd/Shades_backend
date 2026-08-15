package com.sunglassstore.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {

    @JsonIgnore
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private String name;

    public AuthResponse(String accessToken, String refreshToken, Long userId, String email, String name) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.userId = userId;
        this.email = email;
        this.name = name;
    }
}
