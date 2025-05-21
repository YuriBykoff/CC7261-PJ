package com.example.projeto_sd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entidade que representa um post criado por um usuário.
 * Inclui conteúdo, timestamps, estado de deleção e informações de replicação.
 */
public class Post {

    /**
     * Chave primária (UUID como String).
     */
    @Id
    @Column(nullable = false)
    private String id;

    /**
     * Usuário autor do post.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "posts_user_id_fk"))
    private User user;

    /**
     * Conteúdo textual do post.
     */
    @Column(nullable = false)
    private String content;

    /**
     * Data/hora de criação do post.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Valor do relógio lógico para ordenação causal.
     */
    @Column(name = "logical_clock", nullable = false)
    private int logicalClock = 0;

    /**
     * Indica se o post foi deletado logicamente.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    /**
     * Servidor que originou ou replicou o post.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false, foreignKey = @ForeignKey(name = "posts_server_id_fk"))
    private Server server;
} 