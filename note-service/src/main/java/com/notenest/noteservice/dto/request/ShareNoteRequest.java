package com.notenest.noteservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShareNoteRequest {

    // Client mengirim email, bukan userId: orang tahu email temannya, bukan UUID-nya.
    // Penerjemahan email -> userId dilakukan note-service lewat Feign ke user-service.
    @Email(message = "Invalid email format")
    @NotBlank(message = "Target email is required")
    private String targetEmail;
}
