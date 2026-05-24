package com.padelpro.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterCommand(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String email,
        String password
) {}
