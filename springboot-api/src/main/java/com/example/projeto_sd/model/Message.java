package com.example.projeto_sd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entidade que representa uma mensagem trocada entre usuários.
 * Inclui remetente, destinatário, conteúdo, timestamps e informações de replicação.
 */
public class Message {

    /**
     * Chave primária (UUID como String).
     */
    @Id
    @Column(nullable = false)
    private String id;

    /**
     * Usuário remetente da mensagem.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "messages_sender_id_fk"))
    private User sender;

    /**
     * Usuário destinatário da mensagem.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false, foreignKey = @ForeignKey(name = "messages_receiver_id_fk"))
    private User receiver;

    /**
     * Conteúdo textual da mensagem.
     */
    @Column(nullable = false)
    private String content;

    /**
     * Data/hora de envio da mensagem.
     */
    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    /**
     * Valor do relógio lógico para ordenação causal.
     */
    @Column(name = "logical_clock", nullable = false)
    private int logicalClock = 0;

    /**
     * Indica se a mensagem foi lida.
     */
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    /**
     * Servidor que processou ou replicou a mensagem.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false, foreignKey = @ForeignKey(name = "messages_server_id_fk"))
    private Server server;
} 