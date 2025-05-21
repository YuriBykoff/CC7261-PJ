package com.example.projeto_sd.dto.post;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeletePostRequestDto {
    private String userId; // ID do usuário tentando deletar
} 