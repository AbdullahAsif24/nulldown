package com.project.nulldown.dto;

import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String email
) {}