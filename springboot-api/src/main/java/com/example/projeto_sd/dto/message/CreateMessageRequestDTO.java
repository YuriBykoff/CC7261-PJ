package com.example.projeto_sd.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMessageRequestDTO {

    @NotNull(message = "Sender ID cannot be null")
    @NotBlank(message = "Sender ID cannot be blank")
    private String senderId;

    @NotNull(message = "Receiver ID cannot be null")
    @NotBlank(message = "Receiver ID cannot be blank")
    private String receiverId;

    @NotNull(message = "Content cannot be null")
    @NotBlank(message = "Content cannot be blank")
    private String content;
} 