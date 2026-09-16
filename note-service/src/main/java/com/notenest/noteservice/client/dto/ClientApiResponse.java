package com.notenest.noteservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Bentuk ApiResponse yang dikirim user-service - untuk parsing balasan Feign
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}
