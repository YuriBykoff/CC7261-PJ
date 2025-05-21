package com.example.projeto_sd.repository;

import com.example.projeto_sd.model.ServerClock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServerClockRepository extends JpaRepository<ServerClock, String> {

    /**
     * Busca o registro de clock para um servidor específico.
     * @param serverId ID do servidor
     * @return registro de clock, se existir
     */
    Optional<ServerClock> findByServerId(String serverId);

} 