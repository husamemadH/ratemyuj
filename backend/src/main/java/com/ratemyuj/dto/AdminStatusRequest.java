package com.ratemyuj.dto;

import com.ratemyuj.domain.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record AdminStatusRequest(@NotNull ReviewStatus status) {}
