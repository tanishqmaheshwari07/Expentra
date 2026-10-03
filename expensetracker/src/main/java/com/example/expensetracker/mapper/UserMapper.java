package com.example.expensetracker.mapper;

import com.example.expensetracker.dto.UserResponse;
import com.example.expensetracker.entity.User;

public class UserMapper {

    private UserMapper() {
        // Utility class
    }

    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
