package com.example.projeto_sd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "server_clocks",
       uniqueConstraints = {
           @UniqueConstraint(name = "server_clocks_server_id_unique", columnNames = {"server_id"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entidade que representa o clock lógico de um servidor.
 * Armazena o offset em milissegundos em relação ao tempo de referência.
 */
public class ServerClock {

    /**
     * Chave primária (UUID como String).
     */
    @Id
    @Column(nullable = false)
    private String id;

    /**
     * Referência ao servidor ao qual o clock pertence.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "server_clocks_server_id_fk"))
    private Server server;

    /**
     * Diferença em milissegundos para o tempo de referência.
     */
    @Column(name = "offset_ms", nullable = false)
    private int offsetMillis = 0;

    /**
     * Data/hora da última atualização do offset.
     */
    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
} 