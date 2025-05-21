package com.example.projeto_sd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "users") // Nome da tabela conforme new.sql
@Data // Lombok para getters, setters, toString, equals, hashCode
@NoArgsConstructor // Lombok para construtor padrão
@AllArgsConstructor // Lombok para construtor com todos os argumentos
/**
 * Entidade que representa um usuário do sistema.
 * Armazena identificador e nome do usuário.
 */
public class User {

    /**
     * Chave primária do usuário (String).
     */
    @Id
    @Column(nullable = false)
    private String id;

    /**
     * Nome do usuário.
     */
    @Column(nullable = false)
    private String name;
} 