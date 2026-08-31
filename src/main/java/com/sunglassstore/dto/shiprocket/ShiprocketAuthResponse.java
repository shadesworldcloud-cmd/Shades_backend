package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShiprocketAuthResponse(String token) {}
