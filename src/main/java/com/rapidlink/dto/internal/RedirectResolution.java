package com.rapidlink.dto.internal;

import lombok.Builder;

import java.net.URI;
import java.util.UUID;

@Builder
public record RedirectResolution(URI uri, UUID shortUrlId) {}