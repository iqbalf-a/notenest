package com.notenest.noteservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class NoteRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Size(max = 20000, message = "Content must be at most 20000 characters")
    private String content;

    @Size(max = 10, message = "A note can have at most 10 tags")
    private Set<@NotBlank(message = "Tag must not be blank")
                @Size(max = 30, message = "Tag must be at most 30 characters") String> tags = new LinkedHashSet<>();
}
