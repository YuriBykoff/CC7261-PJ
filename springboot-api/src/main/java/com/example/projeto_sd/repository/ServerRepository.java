package com.example.projeto_sd.repository;

import com.example.projeto_sd.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServerRepository extends JpaRepository<Server, String> {

    /**
     * Busca todos os servidores ativos.
     * @return lista de servidores ativos
     */
    List<Server> findByIsActiveTrue();

    /**
     * Busca o coordenador atual, se houver.
     * @return Optional com o coordenador
     */
    Optional<Server> findByIsCoordinatorTrue();

    /**
     * Busca servidores ativos com ID maior que o fornecido (para eleição do valentão).
     * @param id ID de referência
     * @return lista de servidores
     */
    List<Server> findByIdGreaterThanAndIsActiveTrue(String id);

    /**
     * Busca um servidor pelo host e porta gRPC.
     * @param host host do servidor
     * @param port porta gRPC
     * @return Optional com o servidor
     */
    Optional<Server> findByHostAndPort(String host, int port);

    /**
     * Busca todos os servidores ativos, exceto o de ID fornecido.
     * @param id ID a ser excluído
     * @return lista de servidores
     */
    List<Server> findByIsActiveTrueAndIdNot(String id);

    /**
     * Busca o servidor coordenador atual.
     * @return Optional com o coordenador
     */
    @Query("SELECT s FROM Server s WHERE s.isCoordinator = true AND s.isActive = true")
    Optional<Server> findCoordinator();

    /**
     * Busca todos os servidores ativos, exceto o próprio servidor.
     * @param serverId ID do servidor a ser excluído
     * @return lista de servidores ativos
     */
    @Query("SELECT s FROM Server s WHERE s.isActive = true AND s.id <> :serverId")
    List<Server> findAllActiveServersExcludingSelf(@Param("serverId") String serverId);

    /**
     * Busca todos os servidores (ativos ou inativos), exceto o próprio servidor.
     * @param serverId ID do servidor a ser excluído
     * @return lista de servidores
     */
    List<Server> findByIdNot(String serverId);


} 