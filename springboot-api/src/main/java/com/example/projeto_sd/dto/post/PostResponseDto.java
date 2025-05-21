package com.example.projeto_sd.dto.post;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostResponseDto {
    private String id;
    private String userId;
    private String userName; // Incluir nome do usuário para conveniência
    private String content;
    private LocalDateTime createdAt;
    private int logicalClock; // Incluir relógio lógico
} 