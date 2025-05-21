package com.example.projeto_sd.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Messages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    private String id;
    private String senderId;
    private String receiverId;
    private String content;
    private LocalDateTime sentAt; // Using LocalDateTime, but could be String or Long for JSON compatibility
    private int logicalClock;
    private boolean isRead;

} 