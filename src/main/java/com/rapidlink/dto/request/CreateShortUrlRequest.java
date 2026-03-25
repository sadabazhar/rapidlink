package com.rapidlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateShortUrlRequest {

    @NotBlank(message = "URL must not be empty")
    @Pattern(
            regexp = "^(http|https)://.*$",
            message = "Only HTTP and HTTPS URLs are allowed"
    )
    @Size(max = 2048, message = "URL is too long")
    private String url;
}
