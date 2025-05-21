package com.example.projeto_sd.repository;

import com.example.projeto_sd.model.Message;
import com.example.projeto_sd.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /**
     * Busca mensagens trocadas entre dois usuários, ordenadas da mais recente para a mais antiga.
     * @param userId1 ID do primeiro usuário
     * @param userId2 ID do segundo usuário
     * @param pageable informações de paginação
     * @return página de mensagens
     */
    @Query("SELECT m FROM Message m WHERE (m.sender.id = :userId1 AND m.receiver.id = :userId2) OR (m.sender.id = :userId2 AND m.receiver.id = :userId1) ORDER BY m.sentAt DESC")
    Page<Message> findConversation(String userId1, String userId2, Pageable pageable);

    /**
     * Busca todas as mensagens recebidas por um usuário.
     * @param receiver destinatário
     * @return lista de mensagens recebidas
     */
    List<Message> findByReceiver(User receiver);

} 