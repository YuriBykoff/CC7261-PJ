package com.example.projeto_sd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entidade que representa um servidor participante do sistema distribuído.
 * Cada instância armazena informações de identificação, estado e heartbeat.
 */
public class Server {

    /**
     * Chave primária (String, ex: server1).
     */
    @Id
    @Column(nullable = false)
    private String id;

    /**
     * Nome descritivo do servidor.
     */
    @Column(name = "server_name", nullable = false)
    private String serverName;

    /**
     * Hostname ou IP onde o gRPC está ouvindo.
     */
    @Column(nullable = false)
    private String host;

    /**
     * Porta gRPC.
     */
    @Column(nullable = false)
    private Integer port;

    /**
     * Indica se o servidor está ativo (para heartbeat).
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * Indica se o servidor é o coordenador atual.
     */
    @Column(name = "is_coordinator", nullable = false)
    private boolean isCoordinator = false;

    /**
     * Timestamp de criação do registro.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp da última atualização do registro.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Timestamp do último heartbeat recebido (pelo coordenador).
     */
    @Column(name = "last_heartbeat_received")
    private LocalDateTime lastHeartbeatReceived;
} 