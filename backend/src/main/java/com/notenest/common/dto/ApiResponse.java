package com.notenest.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Amplop {success, message, data} - bentuknya identik dengan ApiResponse milik
// auth/user/note, jadi JSON yang diterima frontend tidak berubah sedikit pun.
// Punya sendiri supaya GlobalExceptionHandler gabungan tidak harus memihak
// salah satu dari ketiga DTO service.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
