package com.notenest.noteservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShareResponse {
    private UUID id;
    private UUID noteId;
    private UUID sharedWithUserId;
    private String sharedWithEmail;
    private String permission;
    private LocalDateTime createdAt;
}
