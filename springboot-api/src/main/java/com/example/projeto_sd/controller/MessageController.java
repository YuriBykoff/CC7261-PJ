package com.example.projeto_sd.controller;

import com.example.projeto_sd.dto.message.CreateMessageRequestDTO;
import com.example.projeto_sd.dto.message.MessageDTO;
import com.example.projeto_sd.dto.response.ErrorResponse;
import com.example.projeto_sd.exception.UserNotFoundException;
import com.example.projeto_sd.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;

    /**
     * POST /api/messages
     * Envia uma nova mensagem privada de um usuário para outro.
     *
     * @param requestDTO DTO contendo senderId, receiverId e content.
     * @return ResponseEntity com status 201 Created e o DTO da mensagem criada, ou um erro.
     */
    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(@RequestBody CreateMessageRequestDTO requestDTO) {
        log.info("Recebida requisição POST /api/messages de {} para {}", requestDTO.getSenderId(), requestDTO.getReceiverId());
        try {
            MessageDTO createdMessage = messageService.sendMessage(requestDTO);

            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(createdMessage.getId())
                    .toUri();

            return ResponseEntity.created(location).body(createdMessage);
        } catch (UserNotFoundException e) {
            log.error("Usuário não encontrado ao enviar mensagem: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem de {} para {}: {}", requestDTO.getSenderId(), requestDTO.getReceiverId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao enviar mensagem: " + e.getMessage()));
        }
    }

    /**
     * GET /api/users/{userId1}/conversation/{userId2}
     * Retorna as mensagens trocadas entre dois usuários (conversa), paginadas.
     * As mensagens mais recentes são retornadas primeiro.
     *
     * @param userId1 ID do primeiro usuário.
     * @param userId2 ID do segundo usuário.
     * @param pageable Informações de paginação (page, size).
     * @return ResponseEntity com a página de MessageDTOs ou um erro.
     */
    @GetMapping("/users/{userId1}/conversation/{userId2}")
    public ResponseEntity<?> getConversation(
            @PathVariable String userId1,
            @PathVariable String userId2,
            Pageable pageable) {
        log.info("Recebida requisição GET /api/users/{}/conversation/{} com pageable: {}", userId1, userId2, pageable);
        try {
            Page<MessageDTO> conversationPage = messageService.getConversation(userId1, userId2, pageable);
            return ResponseEntity.ok(conversationPage);
        } catch (UserNotFoundException e) {
            log.error("Usuário não encontrado ao buscar conversa: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar conversa entre {} e {}: {}", userId1, userId2, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao buscar conversa: " + e.getMessage()));
        }
    }
} 