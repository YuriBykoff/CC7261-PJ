package com.example.projeto_sd.controller;

import com.example.projeto_sd.dto.notification.NotificationDTO;
import com.example.projeto_sd.dto.response.ErrorResponse;
import com.example.projeto_sd.exception.UserNotFoundException;
import com.example.projeto_sd.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api") // Base path para todos os endpoints neste controller
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/users/{userId}/notifications
     * Retorna a lista de notificações não lidas para o usuário especificado.
     *
     * @param userId O ID do usuário.
     * @return ResponseEntity contendo a lista de NotificationDTOs ou status apropriado.
     */
    @GetMapping("/users/{userId}/notifications")
    public ResponseEntity<?> getUnreadNotifications(@PathVariable String userId) {
        log.info("Recebida requisição GET /api/users/{}/notifications", userId);
        try {
            List<NotificationDTO> notifications = notificationService.getUnreadNotifications(userId);
            return ResponseEntity.ok(notifications);
        } catch (UserNotFoundException e) {
            log.error("Usuário não encontrado ao buscar notificações: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar notificações para o usuário {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao buscar notificações: " + e.getMessage()));
        }
    }

    /**
     * POST /api/users/{userId}/notifications/mark-read
     * Marca as notificações especificadas como lidas para o usuário.
     *
     * @param userId O ID do usuário.
     * @param notificationIds Lista de IDs das notificações a serem marcadas.
     * @return ResponseEntity com status 204 No Content em caso de sucesso.
     */
    @PostMapping("/users/{userId}/notifications/mark-read")
    public ResponseEntity<?> markNotificationsAsRead(@PathVariable String userId,
                                                   @RequestBody List<String> notificationIds) {
        log.info("Recebida requisição POST /api/users/{}/notifications/mark-read para {} notificação(ões)", 
                userId, notificationIds != null ? notificationIds.size() : 0);
        try {
            notificationService.markNotificationsAsRead(userId, notificationIds);
            return ResponseEntity.noContent().build();
        } catch (UserNotFoundException e) {
            log.error("Usuário não encontrado ao marcar notificações como lidas: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao marcar notificações como lidas para o usuário {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao marcar notificações como lidas: " + e.getMessage()));
        }
    }
} 