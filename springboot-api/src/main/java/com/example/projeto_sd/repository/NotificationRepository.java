package com.example.projeto_sd.repository;

import com.example.projeto_sd.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    /**
     * Busca todas as notificações (lidas ou não) de um usuário, ordenadas da mais recente para a mais antiga.
     * @param userId ID do usuário
     * @param read estado de leitura (false para não lidas)
     * @return lista de notificações
     */
    List<Notification> findByUserIdAndReadOrderByCreatedAtDesc(String userId, boolean read);

    /**
     * Marca uma lista de notificações como lidas para um usuário específico.
     * @param userId ID do usuário
     * @param notificationIds lista de IDs das notificações
     * @return número de notificações atualizadas
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.id IN :notificationIds AND n.read = false")
    int markAsRead(@Param("userId") String userId, @Param("notificationIds") List<String> notificationIds);


} 