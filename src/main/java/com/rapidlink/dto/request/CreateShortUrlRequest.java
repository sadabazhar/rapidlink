package com.rapidlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
public class CreateShortUrlRequest {

    @NotBlank(message = "URL must not be empty")
    @URL(message = "Invalid URL format")
    private String url;
}
