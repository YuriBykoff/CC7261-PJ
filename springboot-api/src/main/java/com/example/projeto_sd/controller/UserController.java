package com.example.projeto_sd.controller;

import com.example.projeto_sd.dto.user.CreateUserRequestDTO;
import com.example.projeto_sd.dto.user.UserResponseDTO;
import com.example.projeto_sd.dto.response.ErrorResponse;
import com.example.projeto_sd.exception.UserNotFoundException;
import com.example.projeto_sd.grpc.ServerCommsProto;
import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.model.User;
import com.example.projeto_sd.service.ElectionService;
import com.example.projeto_sd.service.FollowService;
import com.example.projeto_sd.service.GrpcClientService;
import com.example.projeto_sd.service.UserService;
import com.example.projeto_sd.repository.ServerRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final ElectionService electionService;
    private final GrpcClientService grpcClientService;
    private final FollowService followService;
    private final ServerRepository serverRepository;

    @Value("${server.id}")
    private String selfId;

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequestDTO requestDTO) {
        log.info("Recebida requisição POST /api/users com nome: {}", requestDTO.getName());

        if (electionService.isCoordinator()) {
            log.info("Processando requisição createUser localmente como coordenador.");
            try {
                User createdUser = userService.createUser(requestDTO.getName());
                UserResponseDTO userResponseDTO = new UserResponseDTO(createdUser.getId(), createdUser.getName());

                replicateUserCreationToFollowers(createdUser);

                log.info("Requisição createUser processada com sucesso. ID do usuário: {}", userResponseDTO.getId());
                return ResponseEntity.status(HttpStatus.CREATED).body(userResponseDTO);
            } catch (IllegalArgumentException e) {
                log.warn("Criação de usuário falhou localmente: {}", e.getMessage());
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                log.error("Erro interno do servidor durante criação local de usuário: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno do servidor ao criar usuário.");
            }
        } else {
            Optional<String> coordinatorIdOpt = electionService.getCoordinatorId();
            if (coordinatorIdOpt.isEmpty()) {
                log.error("Não é possível encaminhar requisição: ID do coordenador desconhecido.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Serviço temporariamente indisponível: Coordenador desconhecido.");
            }

            String coordinatorId = coordinatorIdOpt.get();
            Optional<Server> coordinatorServerOpt = electionService.getCoordinatorServerDetails(coordinatorId);
            if (coordinatorServerOpt.isEmpty()) {
                log.error("Não é possível encaminhar requisição: Detalhes para o coordenador {} não encontrados.", coordinatorId);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Serviço temporariamente indisponível: Detalhes do coordenador ausentes.");
            }

            Server coordinator = coordinatorServerOpt.get();
            log.info("Encaminhando requisição createUser para o coordenador: {} em {}:{}", 
                    coordinator.getId(), coordinator.getHost(), coordinator.getPort());
            
            try {
                ServerCommsProto.UserResponse grpcResponse = grpcClientService.createUserOnLeader(
                        coordinator.getHost() + ":" + coordinator.getPort(), 
                        requestDTO.getName());
                
                if (grpcResponse == null) {
                    log.error("Falha ao criar usuário: Sem resposta do coordenador {}.", coordinator.getId());
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Falha ao criar usuário: Coordenador não respondeu.");
                }

                UserResponseDTO userResponseDTO = new UserResponseDTO(grpcResponse.getId(), grpcResponse.getName());
                return ResponseEntity.status(HttpStatus.CREATED).body(userResponseDTO);
            } catch (Exception e) {
                log.error("Erro ao encaminhar requisição de criação de usuário para o coordenador {}: {}", coordinator.getId(), e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao comunicar com o coordenador.");
            }
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        log.info("Recebida requisição GET /api/users");
        try {
            List<UserResponseDTO> users = userService.getAllUsers();
            log.info("Retornando {} usuários.", users.size());
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Erro ao buscar usuários: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao buscar usuários: " + e.getMessage()));
        }
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<?> getFollowers(@PathVariable String userId) {
        log.info("Recebida requisição GET /api/users/{}/followers", userId);
        try {
            List<UserResponseDTO> followers = followService.getFollowers(userId);
            log.info("Retornando {} seguidores para o usuário {}", followers.size(), userId);
            return ResponseEntity.ok(followers);
        } catch (UserNotFoundException e) {
            log.error("Usuário não encontrado ao buscar seguidores: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar seguidores para o usuário {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao buscar seguidores: " + e.getMessage()));
        }
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<?> getFollowing(@PathVariable String userId) {
        log.info("Recebida requisição GET /api/users/{}/following", userId);
        try {
            List<UserResponseDTO> following = followService.getFollowing(userId);
            log.info("Usuário {} está seguindo {} usuários", userId, following.size());
            return ResponseEntity.ok(following);
        } catch (UserNotFoundException e) {
            log.error("Usuário não encontrado ao buscar quem está seguindo: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar usuários seguidos por {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao buscar quem está seguindo: " + e.getMessage()));
        }
    }

    private void replicateUserCreationToFollowers(User user) {
        log.info("Coordenador ({}) iniciando replicação para o ID do usuário: {}", this.selfId, user.getId());

        List<Server> activeServers = serverRepository.findByIsActiveTrue();
        ServerCommsProto.UserInfo userInfoProto = ServerCommsProto.UserInfo.newBuilder()
                .setId(user.getId())
                .setName(user.getName())
                .build();

        for (Server server : activeServers) {
            if (server != null && server.getId() != null && !server.getId().equals(this.selfId)) {
                log.debug("Replicando usuário {} para o servidor {} em {}:{}", 
                        user.getId(), server.getId(), server.getHost(), server.getPort());
                try {
                    ServerCommsProto.ReplicationResponse replicationResponse =
                            grpcClientService.replicateUserCreationToPeer(server.getHost(), server.getPort(), userInfoProto);
                    if (replicationResponse == null || !replicationResponse.getSuccess()) {
                        log.warn("Replicação do usuário {} para o servidor {} falhou. Mensagem: {}",
                                user.getId(), server.getId(), 
                                replicationResponse != null ? replicationResponse.getMessage() : "Sem resposta");
                    } else {
                        log.info("Usuário {} replicado com sucesso para o servidor {}", user.getId(), server.getId());
                    }
                } catch (Exception e) {
                    log.error("Erro durante a replicação do usuário {} para o servidor {}: {}",
                            user.getId(), server.getId(), e.getMessage(), e);
                }
            }
        }
        log.info("Processo de replicação finalizado para o ID do usuário: {}", user.getId());
    }
} 