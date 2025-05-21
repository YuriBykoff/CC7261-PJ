package com.example.projeto_sd.controller;

import com.example.projeto_sd.exception.UserNotFoundException;
import com.example.projeto_sd.dto.response.ErrorResponse;
import com.example.projeto_sd.grpc.ServerCommsProto;
import com.example.projeto_sd.grpc.ServerServiceImpl;
import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.service.ElectionService;
import com.example.projeto_sd.service.GrpcClientService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
@Slf4j
public class FollowController {

    private final ElectionService electionService;
    private final GrpcClientService grpcClientService;
    private final ServerServiceImpl serverServiceImpl;

    /**
     * Endpoint para um usuário (followerId) seguir outro usuário (followedId).
     */
    // Path: /api/follows/{followerId}/follow/{followedId}
    @PostMapping("/{followerId}/follow/{followedId}")
    public ResponseEntity<?> followUser(@PathVariable String followerId, @PathVariable String followedId) {
        log.info("Requisição POST /api/follows recebida: seguidor={}, seguido={}", followerId, followedId);

        try {
            if (electionService.isCoordinator()) {
                // Sou o coordenador: chamar o método RPC localmente para iniciar execução + replicação
                log.info("Processando requisição de seguir como coordenador via simulação de chamada RPC direta.");

                ServerCommsProto.FollowRequest grpcRequest = ServerCommsProto.FollowRequest.newBuilder()
                        .setFollowerId(followerId)
                        .setFollowedId(followedId)
                        .build();

                // Usar um array para armazenar exceção do observer (ou AtomicReference)
                final Throwable[] observerError = {null};
                StreamObserver<ServerCommsProto.ReplicationResponse> responseObserver = new StreamObserver<>() {
                    @Override public void onNext(ServerCommsProto.ReplicationResponse value) { }
                    @Override public void onError(Throwable t) { observerError[0] = t; }
                    @Override public void onCompleted() { }
                };

                serverServiceImpl.followUserRPC(grpcRequest, responseObserver);

                // Verificar se houve erro no observer após a chamada síncrona simulada
                if (observerError[0] != null) {
                    log.error("Erro reportado pelo observer durante a simulação de RPC local: {}", observerError[0].getMessage());
                    // Retornar erro interno, talvez com a mensagem do gRPC?
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro interno do servidor: " + observerError[0].getMessage()));
                }
                return ResponseEntity.ok().build(); // Sucesso se não houve erro no observer

            } else {
                // Não sou o coordenador: encaminhar via gRPC
                log.info("Encaminhando requisição de seguir para o coordenador.");
                Optional<String> coordinatorIdOpt = electionService.getCoordinatorId();
                if (coordinatorIdOpt.isEmpty()) {
                    log.error("Não é possível encaminhar a requisição de seguir: ID do coordenador desconhecido.");
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("Serviço indisponível: Coordenador desconhecido."));
                }
                String coordinatorId = coordinatorIdOpt.get();
                Optional<Server> coordinatorOpt = electionService.getCoordinatorServerDetails(coordinatorId);
                if (coordinatorOpt.isEmpty()) {
                    log.error("Não é possível encaminhar a requisição de seguir: Detalhes para o coordenador {} não disponíveis.", coordinatorId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("Serviço indisponível: Detalhes do coordenador não encontrados."));
                }
                Server coordinator = coordinatorOpt.get();
                String target = coordinator.getHost() + ":" + coordinator.getPort();
                log.info("Encaminhando requisição de seguir {} -> {} para o coordenador {} em {}", followerId, followedId, coordinator.getId(), target);

                ServerCommsProto.ReplicationResponse grpcResponse = grpcClientService.followUserOnLeader(target, followerId, followedId);

                if (grpcResponse == null || !grpcResponse.getSuccess()) {
                     String errorMsg = (grpcResponse != null) ? grpcResponse.getMessage() : "No response from coordinator.";
                    log.error("Falha ao encaminhar requisição de seguir para o coordenador {}: {}", target, errorMsg);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Falha ao processar requisição de seguir via coordenador: " + errorMsg));
                }
                log.info("Requisição de seguir processada com sucesso pelo coordenador para {} -> {}", followerId, followedId);
                return ResponseEntity.ok().build();
            }

        } catch (Exception e) {
             log.error("Erro ao processar requisição de seguir: {} -> {}. Erro: {}", followerId, followedId, e.getMessage(), e);
            if (e instanceof UserNotFoundException) {
                 return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            if (e instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao processar requisição de seguir: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para um usuário (followerId) deixar de seguir outro usuário (followedId).
     */
    // Path: /api/follows/{followerId}/unfollow/{followedId}
    @DeleteMapping("/{followerId}/unfollow/{followedId}")
    public ResponseEntity<?> unfollowUser(@PathVariable String followerId, @PathVariable String followedId) {
        log.info("Requisição DELETE /api/follows recebida: seguidor={}, deixou de seguir={}", followerId, followedId);

        try {
             if (electionService.isCoordinator()) {
                // Sou o coordenador: chamar o método RPC localmente
                log.info("Processando requisição de deixar de seguir como coordenador via simulação de chamada RPC direta.");
                ServerCommsProto.FollowRequest grpcRequest = ServerCommsProto.FollowRequest.newBuilder()
                        .setFollowerId(followerId)
                        .setFollowedId(followedId)
                        .build();

                final Throwable[] observerError = {null};
                StreamObserver<ServerCommsProto.ReplicationResponse> responseObserver = new StreamObserver<>() {
                    @Override public void onNext(ServerCommsProto.ReplicationResponse value) { }
                    @Override public void onError(Throwable t) { observerError[0] = t; }
                    @Override public void onCompleted() { }
                };
                serverServiceImpl.unfollowUserRPC(grpcRequest, responseObserver);

                if (observerError[0] != null) {
                     log.error("Erro reportado pelo observer durante a simulação de RPC local: {}", observerError[0].getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro interno do servidor: " + observerError[0].getMessage()));
                }
                return ResponseEntity.ok().build();

            } else {
                // Não sou o coordenador: encaminhar via gRPC
                log.info("Encaminhando requisição de deixar de seguir para o coordenador.");
                 Optional<String> coordinatorIdOpt = electionService.getCoordinatorId();
                if (coordinatorIdOpt.isEmpty()) {
                    log.error("Não é possível encaminhar a requisição de deixar de seguir: ID do coordenador desconhecido.");
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("Serviço indisponível: Coordenador desconhecido."));
                }
                String coordinatorId = coordinatorIdOpt.get();
                 Optional<Server> coordinatorOpt = electionService.getCoordinatorServerDetails(coordinatorId);
                if (coordinatorOpt.isEmpty()) {
                    log.error("Não é possível encaminhar a requisição de deixar de seguir: Detalhes para o coordenador {} não disponíveis.", coordinatorId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("Serviço indisponível: Detalhes do coordenador não encontrados."));
                }
                Server coordinator = coordinatorOpt.get();
                String target = coordinator.getHost() + ":" + coordinator.getPort();
                log.info("Encaminhando requisição de deixar de seguir {} deixa de seguir {} para o coordenador {} em {}", followerId, followedId, coordinator.getId(), target);

                ServerCommsProto.ReplicationResponse grpcResponse = grpcClientService.unfollowUserOnLeader(target, followerId, followedId);

                 if (grpcResponse == null || !grpcResponse.getSuccess()) {
                    String errorMsg = (grpcResponse != null) ? grpcResponse.getMessage() : "No response from coordinator.";
                    log.error("Falha ao encaminhar requisição de deixar de seguir para o coordenador {}: {}", target, errorMsg);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Falha ao processar requisição de deixar de seguir via coordenador: " + errorMsg));
                }
                log.info("Requisição de deixar de seguir processada com sucesso pelo coordenador para {} deixa de seguir {}", followerId, followedId);
                return ResponseEntity.ok().build();
            }
        } catch (Exception e) {
            log.error("Erro ao processar requisição de deixar de seguir: {} deixou de seguir {}. Erro: {}", followerId, followedId, e.getMessage(), e);
            // Não precisamos tratar UserNotFound aqui, pois o unfollow local é idempotente
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erro ao processar requisição de deixar de seguir: " + e.getMessage()));
        }
    }
} 