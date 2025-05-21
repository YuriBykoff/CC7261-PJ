package com.example.projeto_sd.repository;

import com.example.projeto_sd.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    /**
     * Busca um usuário pelo nome.
     * @param name nome do usuário
     * @return Optional com o usuário, se encontrado
     */
    Optional<User> findByName(String name);

} 