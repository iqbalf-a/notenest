package com.notenest.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Display name is required")
    @Size(max = 60, message = "Display name must be at most 60 characters")
    private String displayName;

    @Size(max = 500, message = "Bio must be at most 500 characters")
    private String bio;

    @Size(max = 255, message = "Avatar URL must be at most 255 characters")
    private String avatarUrl;
}
