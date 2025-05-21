package com.example.projeto_sd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entidade que representa uma notificação para um usuário.
 * Inclui tipo, mensagem, referência a entidade relacionada e estado de leitura.
 */
public class Notification {

    /**
     * Chave primária (UUID como String).
     */
    @Id
    private String id;

    /**
     * Usuário que recebe a notificação.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Tipo da notificação (ex: "NEW_POST").
     */
    @Column(nullable = false)
    private String type;

    /**
     * Mensagem da notificação.
     */
    @Column(nullable = false)
    private String message;

    /**
     * ID da entidade relacionada (ex: post_id).
     */
    @Column(name = "related_entity_id")
    private String relatedEntityId;

    /**
     * Indica se a notificação foi lida.
     */
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    /**
     * Data/hora de criação da notificação.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
} 