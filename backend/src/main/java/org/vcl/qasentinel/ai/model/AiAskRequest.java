package org.vcl.qasentinel.ai.model;

import jakarta.validation.constraints.NotBlank;

public record AiAskRequest(@NotBlank String question) {}
