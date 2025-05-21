package com.example.projeto_sd.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private String id;
    private String type;
    private String message;
    private String relatedEntityId;
    private LocalDateTime createdAt;
    private boolean read; // Incluir status de leitura no DTO
} 