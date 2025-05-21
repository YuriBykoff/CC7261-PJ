package com.example.projeto_sd.grpc;

import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.model.User;
import com.example.projeto_sd.repository.ServerRepository;
import com.example.projeto_sd.repository.UserRepository;
import com.example.projeto_sd.service.ElectionService;
import com.example.projeto_sd.service.FollowService;
import com.example.projeto_sd.service.HeartbeatService;
import com.example.projeto_sd.service.ClockSyncService;
import com.example.projeto_sd.service.UserService;
import com.google.protobuf.Empty;
import com.example.projeto_sd.grpc.ServerCommsProto.*;
import com.example.projeto_sd.grpc.ServerCommsProto.UserInfo;
import com.example.projeto_sd.grpc.ServerCommsProto.ReplicationResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import com.example.projeto_sd.service.GrpcClientService;
import org.springframework.beans.factory.annotation.Value;
import com.example.projeto_sd.service.PostService;
import com.example.projeto_sd.model.Post;
import com.example.projeto_sd.dto.post.DeletePostRequestDto;
import com.example.projeto_sd.repository.NotificationRepository;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.List;
import java.util.stream.Collectors;
import com.example.projeto_sd.service.NotificationService;
import com.example.projeto_sd.service.MessageService;
import com.example.projeto_sd.dto.message.CreateMessageRequestDTO;
import com.example.projeto_sd.dto.message.MessageDTO;
import com.example.projeto_sd.exception.UserNotFoundException;
import java.util.function.Supplier;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    private final ServerRepository serverRepository;
    private final ElectionService electionService;
    private final HeartbeatService heartbeatService;
    private final ClockSyncService clockSyncService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final GrpcClientService grpcClientService;
    private final FollowService followService;
    private final PostService postService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final MessageService messageService;

    @Value("${server.id}")
    private String selfId;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${spring.application.name}")
    private String selfServiceName;

    // --- Métodos Auxiliares para Conversão ---

    private static UserInfo toUserInfoProto(User user) {
        if (user == null) return UserInfo.newBuilder().build();
        return UserInfo.newBuilder()
                .setId(user.getId() != null ? user.getId() : "")
                .setName(user.getName() != null ? user.getName() : "")
                .build();
    }

    private static UserResponse toUserResponseProto(User user) {
         if (user == null) return UserResponse.newBuilder().build();
         return UserResponse.newBuilder()
                .setId(user.getId() != null ? user.getId() : "")
                .setName(user.getName() != null ? user.getName() : "")
                .build();
    }

    private static PostInfo toPostInfoProto(Post post) {
        if (post == null || post.getUser() == null || post.getCreatedAt() == null) {
             log.warn("Tentando converter Post inválido ou incompleto para PostInfo");
             return PostInfo.newBuilder().build();
        }
         return PostInfo.newBuilder()
            .setId(post.getId() != null ? post.getId() : "")
            .setUserId(post.getUser().getId()) // User ID is assumed non-null if post is valid
            .setContent(post.getContent() != null ? post.getContent() : "")
            .setCreatedAtMillis(post.getCreatedAt().atZone(ZoneId.of("UTC")).toInstant().toEpochMilli())
            .setLogicalClock(post.getLogicalClock())
            .build();
    }

     private static MessageInfo toMessageInfoProto(MessageDTO messageDTO) {
        if (messageDTO == null || messageDTO.getSentAt() == null) {
            log.warn("Tentando converter MessageDTO inválido ou incompleto para MessageInfo");
            return MessageInfo.newBuilder().build();
        }
        return MessageInfo.newBuilder()
            .setId(messageDTO.getId() != null ? messageDTO.getId() : "")
            .setSenderId(messageDTO.getSenderId() != null ? messageDTO.getSenderId() : "")
            .setReceiverId(messageDTO.getReceiverId() != null ? messageDTO.getReceiverId() : "")
            .setContent(messageDTO.getContent() != null ? messageDTO.getContent() : "")
            .setSentAtMillis(messageDTO.getSentAt().atZone(ZoneId.of("UTC")).toInstant().toEpochMilli())
            .setLogicalClock(messageDTO.getLogicalClock())
            .setIsRead(messageDTO.isRead())
            .build();
    }

     // Método necessário para registerOrUpdateRemoteServer
     private ServerRegistrationResponse buildRegistrationResponse(boolean success, String message) {
        return ServerRegistrationResponse.newBuilder()
                .setSuccess(success)
                .setMessage(message != null ? message : "")
                .build();
    }

    /**
     * RPC: Recebe informações de um servidor remoto e salva/atualiza no banco de dados local.
     */
    @Override
    public void registerOrUpdateRemoteServer(ServerInfo request, StreamObserver<ServerRegistrationResponse> responseObserver) {
        log.info("Recebendo registro de servidor: ID={}, Host={}, Porta={}",
                request.getId(), request.getHost(), request.getPort());

        try {
            Server server = processServerRegistration(request);
            serverRepository.save(server);

            ServerRegistrationResponse response = buildRegistrationResponse(true, "Servidor registrado com sucesso");
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Erro no registro do servidor: {}", e.getMessage());
            ServerRegistrationResponse response = buildRegistrationResponse(false, "Erro no registro: " + e.getMessage());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    private Server processServerRegistration(ServerInfo request) {
        return serverRepository.findById(request.getId())
                .map(server -> updateExistingServer(server, request))
                .orElseGet(() -> createNewServer(request));
    }

    private Server updateExistingServer(Server server, ServerInfo request) {
        server.setId(request.getId());
        server.setHost(request.getHost());
        server.setPort(request.getPort());
        server.setActive(true);
        server.setServerName(request.getId());
        return server;
    }

    private Server createNewServer(ServerInfo request) {
        Server newServer = new Server();
        newServer.setId(request.getId());
        newServer.setHost(request.getHost());
        newServer.setPort(request.getPort());
        newServer.setActive(true);
        newServer.setCoordinator(false);
        newServer.setServerName(request.getId());
        return newServer;
    }

    /**
     * RPC: Retorna a lista de todos os servidores ativos conhecidos por este nó.
     */
    @Override
    public void getServerList(Empty request, StreamObserver<ServerListResponse> responseObserver) {
        log.debug("Recebida solicitação da lista de servidores.");
        try {
            List<Server> activeServers = serverRepository.findByIsActiveTrue();
            List<ServerInfo> serverInfoList = activeServers.stream()
                    .map(server -> ServerInfo.newBuilder()
                            .setId(server.getId() != null ? server.getId() : "")
                            .setHost(server.getHost() != null ? server.getHost() : "")
                            .setPort(server.getPort())
                            .build())
                    .collect(Collectors.toList());

            ServerListResponse response = ServerListResponse.newBuilder()
                    .addAllServers(serverInfoList)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
             handleGrpcError(e, responseObserver, "obter lista de servidores");
        }
    }

    /**
     * RPC: Retorna o tempo atual do servidor corrigido pelo offset.
     */
    @Override
    public void getTime(GetTimeRequest request, StreamObserver<GetTimeResponse> responseObserver) {
        String operationName = "getTime";
        try {
            long correctedTime = clockSyncService.getCurrentCorrectedTimeMillis();
            log.trace("Recebida solicitação {}, retornando tempo corrigido: {}", operationName, correctedTime);
            GetTimeResponse response = GetTimeResponse.newBuilder()
                    .setCurrentTimeMillis(correctedTime)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
         } catch (Exception e) {
              handleGrpcError(e, responseObserver, "obter tempo");
        }
    }

    /**
     * RPC: Recebe um ajuste de tempo e o aplica usando o ClockSyncService.
     */
    @Override
    public void adjustServerTime(AdjustTimeRequest request, StreamObserver<AdjustTimeResponse> responseObserver) {
        String operationName = "adjustServerTime";
        long adjustment = request.getTimeOffsetMillis();
        log.info("Recebida solicitação {} com ajuste: {}ms via gRPC", operationName, adjustment);
         try {
            clockSyncService.applyReceivedAdjustment(adjustment);
            AdjustTimeResponse response = AdjustTimeResponse.newBuilder().setSuccess(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
              handleGrpcError(e, responseObserver, "ajustar tempo com offset " + adjustment);
        }
    }

    /**
     * RPC: Recebe um heartbeat de outro servidor.
     * Delega o processamento para o HeartbeatService.
     */
    @Override
    public void receiveHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        String operationName = "receiveHeartbeat";
        String senderId = request.getServerId();
        log.trace("Recebido {} do servidor: {} via gRPC", operationName, senderId);
        try {
            heartbeatService.processIncomingHeartbeat(senderId);
            HeartbeatResponse response = HeartbeatResponse.newBuilder().setAcknowledged(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
             handleGrpcError(e, responseObserver, "receber heartbeat de " + senderId);
        }
    }

    /**
     * RPC: Recebe o anúncio de um novo coordenador.
     * Delega o processamento para o ElectionService.
     */
    @Override
    public void announceCoordinator(CoordinatorAnnouncement request, StreamObserver<Empty> responseObserver) {
        String operationName = "announceCoordinator";
        String newCoordinatorId = request.getCoordinatorId();
        log.info("Recebido {} para {} via gRPC", operationName, newCoordinatorId);
         try {
            electionService.processCoordinatorAnnouncement(newCoordinatorId);
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
             handleGrpcError(e, responseObserver, "anunciar coordenador " + newCoordinatorId);
        }
    }

    /**
     * RPC: Cria um novo usuário. Espera-se que seja chamado apenas no líder.
     * AGORA TAMBÉM INICIA A REPLICAÇÃO.
     */
    @Override
    public void createUserRPC(CreateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        String operationName = "createUser";
        log.info("Recebendo solicitação {}: {}", operationName, request.getName());

        if (electionService.isCurrentNodeCoordinator()) {
            try {
                User createdUser = userService.createUser(request.getName());
                UserResponse response = toUserResponseProto(createdUser); // Usar helper de conversão
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                log.info("Usuário criado com sucesso: ID={}, Nome={}", createdUser.getId(), createdUser.getName());
                replicateUserCreationToFollowers(createdUser);
            } catch (Exception e) {
                 handleGrpcError(e, responseObserver, "criar usuário");
            }
        } else {
            electionService.getCoordinatorId()
                .flatMap(electionService::getCoordinatorServerDetails)
                .ifPresentOrElse(
                    coordinator -> {
                         log.info("Nó {} não é coordenador. Encaminhando {} para o coordenador {} em {}:{}",
                                 selfId, operationName, coordinator.getId(), coordinator.getHost(), coordinator.getPort());
                        try {
                            UserResponse response = grpcClientService.forwardCreateUserRPC(
                                    coordinator.getHost(),
                                    coordinator.getPort(),
                                    request
                            );
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                            log.info("Requisição {} encaminhada com sucesso para o coordenador: {}", operationName, coordinator.getId());
                        } catch (Exception e) {
                           handleForwardingError(coordinator, e, responseObserver, operationName);
                        }
                    },
                    () -> handleCoordinatorNotFoundError(responseObserver, operationName)
                );
        }
    }

    // --- Métodos de Replicação ---

    private void replicateUserCreationToFollowers(User user) {
        if (!electionService.isCurrentNodeCoordinator()) { // Check if it's still coordinator
            log.error("Tentativa de replicação de criação de usuário por nó não-coordenador: {}", selfId);
            return;
        }

        List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfId) && !peerServerId.startsWith("unknown") && getGrpcPortFromInstance(instance) != -1;
                })
                .collect(Collectors.toList());

        UserInfo userInfoProto = toUserInfoProto(user);

        log.info("[UserCreationRepl] Replicando usuário {} para {} peers encontrados via Consul.", user.getId(), activePeerInstances.size());

        for (ServiceInstance peerInstance : activePeerInstances) {
            String peerHost = peerInstance.getHost();
            int peerPort = getGrpcPortFromInstance(peerInstance);
            String peerServerId = getServerIdFromInstance(peerInstance);
            
            replicateToServer(peerHost, peerPort, peerServerId, userInfoProto);
        }
    }

    private void replicateToServer(String host, int port, String peerServerId, UserInfo userInfo) {
        try {
            log.debug("[UserCreationRepl] Replicando usuário {} para o peer {} em {}:{}", userInfo.getId(), peerServerId, host, port);
            ReplicationResponse response = grpcClientService.replicateUserCreationToPeer(
                host, port, userInfo);
            
            if (response == null || !response.getSuccess()) { // Adicionar verificação de nulidade para response
                handleReplicationFailure(peerServerId, "criação de usuário", response != null ? response.getMessage() : "Sem resposta");
            } else {
                log.info("[UserCreationRepl] Usuário {} replicado com sucesso para o peer {}.", userInfo.getId(), peerServerId);
            }
        } catch (Exception e) {
            handleReplicationException(peerServerId, "criação de usuário", e);
        }
    }

    // --- Métodos Auxiliares ---

    private void handleReplicationFailure(String serverId, String operation, String failureMessage) {
        log.error("Falha na replicação de {} para servidor {}. Mensagem: {}. Será retentado no próximo heartbeat.", 
                operation, serverId, failureMessage);
    }

    private void handleReplicationException(String serverId, String operation, Exception e) {
        log.error("Erro durante replicação de {} para servidor {}: {}", 
                operation, serverId, e.getMessage());
    }

    /**
     * RPC: Recebe dados de um usuário criado no coordenador e replica localmente.
     */
    @Override
    public void replicateUserCreation(UserInfo request, StreamObserver<ReplicationResponse> responseObserver) {
        String operationName = "replicateUserCreation";
        log.info("Recebida solicitação {} para ID de usuário: {}, Nome: {}", operationName, request.getId(), request.getName());

        if (electionService.isCurrentNodeCoordinator()) {
            log.warn("Coordenador recebeu uma solicitação de {}. Isso não deveria acontecer. Ignorando.", operationName);
            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Coordenador não deve receber requisições de replicação.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        try {
            validateReplicationRequest(request);
            processUserReplication(request);

            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Usuário replicado com sucesso.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalStateException e) {
            log.error("Erro de validação durante {}: {}", operationName, e.getMessage());
             ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Erro de validação: " + e.getMessage())
                    .build();
             responseObserver.onNext(response);
             responseObserver.onCompleted();
        } catch (Exception e) {
             handleGrpcError(e, responseObserver, "replicar criação de usuário");
        }
    }

    private void validateReplicationRequest(UserInfo request) {
        if (request == null || request.getId().isEmpty() || request.getName().isEmpty()) {
            throw new IllegalStateException("Requisição de replicação inválida: campos obrigatórios ausentes");
        }
    }

    private void processUserReplication(UserInfo request) {
        if (userRepository.existsById(request.getId())) {
            log.warn("Solicitação de replicação para ID de usuário existente: {}. Assumindo que já foi replicado.", request.getId());
            return;
        }

        User userToReplicate = new User(request.getId(), request.getName());
        userRepository.save(userToReplicate);
        log.info("ID de usuário replicado com sucesso: {}, Nome: {}", request.getId(), request.getName());
    }

    /**
     * RPC: Recebe solicitação para seguir um usuário (esperado no coordenador).
     * Executa localmente e depois inicia a replicação.
     */
    @Override
    public void followUserRPC(FollowRequest request, StreamObserver<ReplicationResponse> responseObserver) {
        String followerId = request.getFollowerId();
        String followedId = request.getFollowedId();
        String operationName = "followUser";

        handleSimpleCoordinatorForwarding(
            operationName,
            () -> {
                followService.followUser(followerId, followedId);
                replicateFollowToFollowers(followerId, followedId);
            },
            () -> {
                Server coordinator = electionService.getCoordinatorId()
                    .flatMap(electionService::getCoordinatorServerDetails)
                    .orElseThrow(() -> new IllegalStateException("Coordinator not found when trying to forward " + operationName)); // Lança exceção se coordenador sumir
                return grpcClientService.forwardFollowUserRPC(coordinator.getHost(), coordinator.getPort(), request);
            },
            responseObserver
        );
    }

    /**
     * RPC: Recebe solicitação para deixar de seguir um usuário (esperado no coordenador).
     * Executa localmente e depois inicia a replicação.
     */
    @Override
    public void unfollowUserRPC(FollowRequest request, StreamObserver<ReplicationResponse> responseObserver) {
        String followerId = request.getFollowerId();
        String followedId = request.getFollowedId();
        String operationName = "unfollowUser";

         handleSimpleCoordinatorForwarding(
            operationName,
            () -> {
                followService.unfollowUser(followerId, followedId);
                replicateUnfollowToFollowers(followerId, followedId);
            },
            () -> {
                Server coordinator = electionService.getCoordinatorId()
                    .flatMap(electionService::getCoordinatorServerDetails)
                    .orElseThrow(() -> new IllegalStateException("Coordinator not found when trying to forward " + operationName));
                return grpcClientService.forwardUnfollowUserRPC(coordinator.getHost(), coordinator.getPort(), request);
            },
            responseObserver
        );
    }

    // --- Novos Métodos Auxiliares de Replicação para Follow/Unfollow ---

    private void replicateFollowToFollowers(String followerId, String followedId) {
        log.info("Coordenador ({}) iniciando replicação para SEGUIR: {} -> {}", selfId, followerId, followedId);
        
        List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfId) && !peerServerId.startsWith("unknown") && getGrpcPortFromInstance(instance) != -1;
                })
                .collect(Collectors.toList());

        if (activePeerInstances.isEmpty()) {
            log.info("[FollowRepl] Nenhum peer ativo encontrado via Consul para replicar SEGUIR {} -> {}.", followerId, followedId);
            return;
        }

        log.info("[FollowRepl] Replicando SEGUIR {} -> {} para {} peers via Consul.", followerId, followedId, activePeerInstances.size());

        for (ServiceInstance peerInstance : activePeerInstances) {
            String peerHost = peerInstance.getHost();
            int peerPort = getGrpcPortFromInstance(peerInstance);
            String peerServerId = getServerIdFromInstance(peerInstance);

            if (peerPort == -1 || "unknown".startsWith(peerServerId)) {
                log.warn("[FollowRepl] Pulando peer {} devido a porta gRPC inválida ou ID desconhecido.", peerInstance.getInstanceId());
                continue;
            }

            log.debug("[FollowRepl] Replicando SEGUIR {} -> {} para o peer {} em {}:{}", followerId, followedId, peerServerId, peerHost, peerPort);
            try {
                ServerCommsProto.ReplicationResponse replicationResponse =
                        grpcClientService.replicateFollowToPeer(peerHost, peerPort, followerId, followedId);
                if (replicationResponse == null || !replicationResponse.getSuccess()) {
                    log.warn("[FollowRepl] Replicação SEGUIR {} -> {} para o peer {} falhou. Mensagem: {}",
                             followerId, followedId, peerServerId, replicationResponse != null ? replicationResponse.getMessage() : "Sem resposta");
                }
            } catch (Exception e) {
                log.error("[FollowRepl] Erro durante a replicação de SEGUIR {} -> {} para o peer {} ({}:{}): {}",
                          followerId, followedId, peerServerId, peerHost, peerPort, e.getMessage(), e);
            }
        }
        log.info("[FollowRepl] Processo de replicação finalizado para SEGUIR: {} -> {}", followerId, followedId);
    }

     private void replicateUnfollowToFollowers(String followerId, String followedId) {
        log.info("Coordenador ({}) iniciando replicação para DEIXAR DE SEGUIR: {} -> {}", selfId, followerId, followedId);
        
        List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfId) && !peerServerId.startsWith("unknown") && getGrpcPortFromInstance(instance) != -1;
                })
                .collect(Collectors.toList());

        if (activePeerInstances.isEmpty()) {
            log.info("[UnfollowRepl] Nenhum peer ativo encontrado via Consul para replicar DEIXAR DE SEGUIR {} -> {}.", followerId, followedId);
            return;
        }

        log.info("[UnfollowRepl] Replicando DEIXAR DE SEGUIR {} -> {} para {} peers via Consul.", followerId, followedId, activePeerInstances.size());

        for (ServiceInstance peerInstance : activePeerInstances) {
            String peerHost = peerInstance.getHost();
            int peerPort = getGrpcPortFromInstance(peerInstance);
            String peerServerId = getServerIdFromInstance(peerInstance);

            if (peerPort == -1 || "unknown".startsWith(peerServerId)) {
                log.warn("[UnfollowRepl] Pulando peer {} devido a porta gRPC inválida ou ID desconhecido.", peerInstance.getInstanceId());
                continue;
            }

            log.debug("[UnfollowRepl] Replicando DEIXAR DE SEGUIR {} -> {} para o peer {} em {}:{}", followerId, followedId, peerServerId, peerHost, peerPort);
            try {
                ServerCommsProto.ReplicationResponse replicationResponse =
                        grpcClientService.replicateUnfollowToPeer(peerHost, peerPort, followerId, followedId);
                if (replicationResponse == null || !replicationResponse.getSuccess()) {
                    log.warn("[UnfollowRepl] Replicação DEIXAR DE SEGUIR {} -> {} para o peer {} falhou. Mensagem: {}",
                             followerId, followedId, peerServerId, replicationResponse != null ? replicationResponse.getMessage() : "Sem resposta");
                }
            } catch (Exception e) {
                log.error("[UnfollowRepl] Erro durante a replicação de DEIXAR DE SEGUIR {} -> {} para o peer {} ({}:{}): {}",
                          followerId, followedId, peerServerId, peerHost, peerPort, e.getMessage(), e);
            }
        }
        log.info("[UnfollowRepl] Processo de replicação finalizado para DEIXAR DE SEGUIR: {} -> {}", followerId, followedId);
    }

    /**
     * RPC: Recebe uma solicitação para replicar uma ação de seguir.
     * Executa a ação localmente.
     */
    @Override
    public void replicateFollow(FollowRequest request, StreamObserver<ReplicationResponse> responseObserver) {
        log.info("Recebida solicitação replicateFollow: seguidor={}, seguido={}", request.getFollowerId(), request.getFollowedId());
        if (electionService.isCoordinator()) {
            log.warn("Nó coordenador {} recebeu solicitação replicateFollow. Ignorando.", selfId);
            ReplicationResponse response = ReplicationResponse.newBuilder().setSuccess(false).setMessage("Coordinator should not process replication.").build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }
        try {
            followService.followUser(request.getFollowerId(), request.getFollowedId());

            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Operação 'seguir' replicada com sucesso.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
             log.error("Erro ao replicar seguir para {} -> {}: {}", request.getFollowerId(), request.getFollowedId(), e.getMessage(), e);
             responseObserver.onError(io.grpc.Status.INTERNAL
                 .withDescription("Erro ao replicar seguir: " + e.getMessage())
                 .withCause(e)
                 .asRuntimeException());
        }
    }

    /**
     * RPC: Recebe uma solicitação para replicar uma ação de deixar de seguir.
     * Executa a ação localmente.
     */
    @Override
    public void replicateUnfollow(FollowRequest request, StreamObserver<ReplicationResponse> responseObserver) {
        log.info("Recebida solicitação replicateUnfollow: seguidor={}, deixou de seguir={}", request.getFollowerId(), request.getFollowedId());
        if (electionService.isCoordinator()) {
            log.warn("Nó coordenador {} recebeu solicitação replicateUnfollow. Ignorando.", selfId);
            ReplicationResponse response = ReplicationResponse.newBuilder().setSuccess(false).setMessage("Coordinator should not process replication.").build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }
        try {
            followService.unfollowUser(request.getFollowerId(), request.getFollowedId());

            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Operação 'deixar de seguir' replicada com sucesso.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
             log.error("Erro ao replicar deixar de seguir para {} deixar de seguir {}: {}", request.getFollowerId(), request.getFollowedId(), e.getMessage(), e);
             responseObserver.onError(io.grpc.Status.INTERNAL
                 .withDescription("Erro ao replicar deixar de seguir: " + e.getMessage())
                 .withCause(e)
                 .asRuntimeException());
        }
    }

    // ----- Implementação RPCs para Posts -----

    /**
     * RPC: Recebe uma solicitação de criação de post (esperado no coordenador, ou encaminhado para ele).
     * Processa localmente e inicia a replicação.
     */
    @Override
    public void createPostRPC(CreatePostRequest request, StreamObserver<CreatePostResponse> responseObserver) {
        String operationName = "createPostRPC";
        log.info("Recebida solicitação {} para ID de usuário: {}", operationName, request.getUserId());

        if (!electionService.isCurrentNodeCoordinator()) {
            log.error("{} recebido pelo nó não coordenador {}. Erro de lógica ou encaminhamento.", operationName, selfId);
            responseObserver.onError(io.grpc.Status.FAILED_PRECONDITION
                .withDescription("Nó não é o coordenador e recebeu um " + operationName + " direto.")
                .asRuntimeException());
            return;
        }

        try {
            User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado com ID: " + request.getUserId()));
            
            Post createdPost = postService.processAndReplicatePost(user, request.getContent());

            PostInfo postInfoProto = toPostInfoProto(createdPost);
            CreatePostResponse response = CreatePostResponse.newBuilder()
                .setPostInfo(postInfoProto)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("Processada com sucesso a {} para o post {}", operationName, createdPost.getId());

        } catch (Exception e) {
             handleGrpcError(e, responseObserver, "criar post");
        }
    }

    /**
     * RPC: Recebe dados de um post criado no coordenador e replica localmente.
     */
    @Override
    public void replicatePostCreation(PostInfo request, StreamObserver<ReplicationResponse> responseObserver) {
        String operationName = "replicatePostCreation";
        log.info("Recebida solicitação {} para ID do post: {}", operationName, request.getId());

        if (electionService.isCurrentNodeCoordinator()) {
            log.warn("Coordenador recebeu uma solicitação {}. Ignorando.", operationName);
            responseObserver.onNext(ReplicationResponse.newBuilder().setSuccess(false).setMessage("Coordinator should not process replication.").build());
            responseObserver.onCompleted();
            return;
        }

        try {
            postService.saveReplicatedPost(
                request.getId(),
                request.getUserId(),
                request.getContent(),
                request.getCreatedAtMillis(),
                request.getLogicalClock()
            );

            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Post replicado com sucesso.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            handleGrpcError(e, responseObserver, "replicar criação de post " + request.getId());
        }
    }

    // ----- Implementação RPCs para Deletar Posts -----

    /**
     * RPC: Recebe uma solicitação de deleção de post (esperado no coordenador).
     */
    @Override
    public void deletePostRPC(DeletePostRequest request, StreamObserver<ReplicationResponse> responseObserver) {
        String operationName = "deletePostRPC";
        String postId = request.getPostId();
        String userId = request.getUserId();

        if (!electionService.isCurrentNodeCoordinator()) {
            log.error("{} recebido pelo nó não coordenador {}. Erro de lógica.", operationName, selfId);
            responseObserver.onError(io.grpc.Status.FAILED_PRECONDITION
                .withDescription("Nó não é o coordenador.")
                .asRuntimeException());
            return;
        }

        log.info("Recebida solicitação {} para ID do post: {} pelo usuário: {}", operationName, postId, userId);
        try {
            DeletePostRequestDto dto = new DeletePostRequestDto(userId);

            postService.deletePost(postId, dto);

            ReplicationResponse response = ReplicationResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Exclusão de post processada com sucesso pelo coordenador.")
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("Processada com sucesso a {} para o post {}", operationName, postId);

        } catch (Exception e) {
            handleGrpcError(e, responseObserver, "processar exclusão de post " + postId);
        }
    }

    /**
     * RPC: Recebe instrução para marcar um post como deletado (replicação).
     */
    @Override
    public void replicatePostDeletion(ReplicatePostDeletionRequest request, StreamObserver<ReplicationResponse> responseObserver) {
        String operationName = "replicatePostDeletion";
        String postId = request.getPostId();
        log.info("Recebida solicitação {} para ID do post: {}", operationName, postId);

        if (electionService.isCurrentNodeCoordinator()) {
            log.warn("Coordenador recebeu solicitação {}. Ignorando.", operationName);
            responseObserver.onNext(ReplicationResponse.newBuilder().setSuccess(false).setMessage("Coordinator should not process replication.").build());
            responseObserver.onCompleted();
            return;
        }

        try {
            postService.markReplicatedPostAsDeleted(postId);

            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Exclusão de post replicada com sucesso.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
             handleGrpcError(e, responseObserver, "replicar exclusão de post " + postId);
        }
    }

    // --- IMPLEMENTAÇÃO DO RPC DE REPLICAÇÃO DE NOTIFICAÇÃO ---

    @Override
    public void replicateNotification(ServerCommsProto.ReplicateNotificationRequest request,
                                      StreamObserver<ServerCommsProto.ReplicationResponse> responseObserver) {
        String operationName = "replicateNotification";
        ServerCommsProto.NotificationProto protoNotification = request.getNotification();
        String coordinatorId = request.getCoordinatorId(); // Pode ser útil para logs
        String notificationId = protoNotification.getId();
        String userId = protoNotification.getUserId();

        log.info("[gRPC Replica] Seguidor {} recebeu solicitação {} do coordenador {} para ID da notificação {} (usuário {})",
                selfId, operationName, coordinatorId, notificationId, userId);

        if (electionService.isCurrentNodeCoordinator()) {
            log.warn("[gRPC Replica] Nó coordenador {} recebeu solicitação {}. Ignorando.", selfId, operationName);
            responseObserver.onNext(ServerCommsProto.ReplicationResponse.newBuilder().setSuccess(false).setMessage("Coordinator should not process replication.").build());
            responseObserver.onCompleted();
            return;
        }

        if (notificationRepository.existsById(notificationId)) {
            log.warn("[gRPC Replica] Solicitação {} para ID de notificação existente: {}. Assumindo que já foi replicado.", operationName, notificationId);
            responseObserver.onNext(ServerCommsProto.ReplicationResponse.newBuilder().setSuccess(true).setMessage("Notificação já existe.").build());
            responseObserver.onCompleted();
            return;
        }

        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Não é possível replicar notificação, usuário não encontrado localmente: " + userId));

            com.example.projeto_sd.model.Notification notificationEntity = new com.example.projeto_sd.model.Notification();
            notificationEntity.setId(notificationId);
            notificationEntity.setUser(user);
            notificationEntity.setType(protoNotification.getType());
            notificationEntity.setMessage(protoNotification.getMessage());
            notificationEntity.setRelatedEntityId(protoNotification.getRelatedEntityId());
            notificationEntity.setRead(protoNotification.getIsRead());
            Timestamp protoTimestamp = protoNotification.getCreatedAt();
            Instant instant = Instant.ofEpochSecond(protoTimestamp.getSeconds(), protoTimestamp.getNanos());
            notificationEntity.setCreatedAt(LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))); // Usar UTC

            notificationRepository.save(notificationEntity);
            log.info("[gRPC Replica] Notificação ID: {} replicada e salva com sucesso", notificationId);

            ServerCommsProto.ReplicationResponse response = ServerCommsProto.ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Notificação replicada com sucesso.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
             handleGrpcError(e, responseObserver, "replicar notificação " + notificationId);
        }
    }

    @Override
    public void forwardMarkNotificationsRead(ServerCommsProto.MarkNotificationsReadRequest request, StreamObserver<Empty> responseObserver) {
        String operationName = "forwardMarkNotificationsRead";
        String userId = request.getUserId();
        List<String> notificationIds = request.getNotificationIdsList();

        log.info("[gRPC Forward] Recebida solicitação {} no coordenador {} para o usuário {}", operationName, selfId, userId);

        try {
            // O coordenador chama seu próprio método de serviço, que lida com atualização local + replicação
            notificationService.markNotificationsAsRead(userId, notificationIds);

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
            log.info("[gRPC Forward] Processada com sucesso a solicitação encaminhada {} para o usuário {}", operationName, userId);
        } catch (Exception e) {
            // Usa helper de erro
            handleGrpcError(e, responseObserver, "marcar notificações como lidas para usuário " + userId);
        }
    }

    @Override
    public void replicateMarkNotificationsRead(ServerCommsProto.MarkNotificationsReadRequest request, StreamObserver<ServerCommsProto.ReplicationResponse> responseObserver) {
        String operationName = "replicateMarkNotificationsRead";
        String userId = request.getUserId();
        List<String> notificationIds = request.getNotificationIdsList();

        log.info("[gRPC Replica] Seguidor {} recebeu solicitação {} para o usuário {}", selfId, operationName, userId);

         if (electionService.isCurrentNodeCoordinator()) {
            log.warn("[gRPC Replica] Nó coordenador {} recebeu solicitação {}. Ignorando.", selfId, operationName);
            responseObserver.onNext(ServerCommsProto.ReplicationResponse.newBuilder().setSuccess(false).setMessage("Coordinator should not process replication.").build());
            responseObserver.onCompleted();
            return;
        }

        try {
            notificationService.markReplicatedNotificationsAsRead(userId, notificationIds);

            ServerCommsProto.ReplicationResponse response = ServerCommsProto.ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("[gRPC Replica] Processada com sucesso a {} para o usuário {}", operationName, userId);
        } catch (Exception e) {
             log.error("[gRPC Replica] Erro ao processar {} para o usuário {}: {}", operationName, userId, e.getMessage(), e);
             ServerCommsProto.ReplicationResponse response = ServerCommsProto.ReplicationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Erro ao processar replicação: " + e.getMessage())
                    .build();
             responseObserver.onNext(response);
             responseObserver.onCompleted();
            // Alternativa: Poderíamos usar handleGrpcError se quiséssemos que o coordenador visse um erro gRPC.
            // handleGrpcError(e, responseObserver, operationName + " para usuário " + userId);
        }
    }

    // ----- Implementação RPCs para Mensagens Privadas -----

    /**
     * RPC: Recebe uma solicitação de envio de mensagem (esperado no coordenador).
     * Processa localmente (via MessageService) e retorna a mensagem criada.
     */
    @Override
    public void sendMessageRPC(SendMessageRequest request, StreamObserver<SendMessageResponse> responseObserver) {
        String operationName = "sendMessageRPC";
        String senderId = request.getSenderId();
        String receiverId = request.getReceiverId();
        log.info("Recebida solicitação {} de {} para {}", operationName, senderId, receiverId);

        // RPC executado apenas no coordenador (encaminhamento via REST)
        if (!electionService.isCurrentNodeCoordinator()) {
            log.error("{} recebido pelo nó não coordenador {}. Erro de lógica.", operationName, selfId);
            responseObserver.onError(io.grpc.Status.FAILED_PRECONDITION
                .withDescription("Nó não é o coordenador e recebeu um " + operationName + " direto.")
                .asRuntimeException());
            return;
        }

        try {
            // Converter a requisição gRPC para o DTO esperado pelo serviço
            CreateMessageRequestDTO requestDTO = new CreateMessageRequestDTO(
                senderId,
                receiverId,
                request.getContent()
            );

            // Chamar o serviço que salva localmente e inicia a replicação.
            MessageDTO createdMessageDTO = messageService.sendMessage(requestDTO);

            // Converter o DTO de resposta de volta para o formato protobuf usando helper
            MessageInfo messageInfoProto = toMessageInfoProto(createdMessageDTO);

            SendMessageResponse response = SendMessageResponse.newBuilder()
                .setMessageInfo(messageInfoProto)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("Processada com sucesso a {} para a mensagem {}", operationName, messageInfoProto.getId());

        } catch (Exception e) {
            // Usa o helper de erro que mapeia UserNotFoundException, etc.
            handleGrpcError(e, responseObserver, "enviar mensagem de " + senderId + " para " + receiverId);
        }
    }

    /**
     * RPC: Recebe dados de uma mensagem criada no coordenador e a replica localmente.
     */
    @Override
    public void replicateMessage(ReplicateMessageRequest request, StreamObserver<ReplicationResponse> responseObserver) {
        String operationName = "replicateMessage";
        MessageInfo messageInfo = request.getMessageInfo();
        String messageId = messageInfo.getId();
        log.info("[gRPC Replica] Recebida solicitação {} para ID da mensagem: {} de {} para {}",
                 operationName, messageId, messageInfo.getSenderId(), messageInfo.getReceiverId());

        if (electionService.isCurrentNodeCoordinator()) {
            log.warn("[gRPC Replica] Nó coordenador {} recebeu solicitação {}. Ignorando.", selfId, operationName);
            responseObserver.onNext(ReplicationResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Coordenador não deve processar replicação.")
                .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            // Chamar um método no MessageService para salvar a mensagem replicada
            messageService.saveReplicatedMessage(messageInfo);

            ReplicationResponse response = ReplicationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Mensagem replicada com sucesso.")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("[gRPC Replica] ID da mensagem {} replicado com sucesso", messageId);

        } catch (Exception e) {
            // Usa helper de erro
            handleGrpcError(e, responseObserver, "replicar mensagem " + messageId);
        }
    }

    // --- Métodos Auxiliares para Tratamento de Erros gRPC ---

    /**
     * Mapeia exceções comuns para Status gRPC apropriados.
     */
    private io.grpc.Status mapExceptionToStatus(Exception e) {
        if (e instanceof UserNotFoundException || e instanceof EntityNotFoundException || e instanceof java.util.NoSuchElementException) {
            // Consider NoSuchElementException from Optional.get() without isPresent check
            return io.grpc.Status.NOT_FOUND;
        } else if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            // IllegalStateException pode indicar um estado inválido para a operação
            return io.grpc.Status.INVALID_ARGUMENT; // Ou FAILED_PRECONDITION dependendo do contexto
        } else if (e instanceof SecurityException) {
            return io.grpc.Status.PERMISSION_DENIED;
        }
        // Adicione outros mapeamentos se necessário
        log.error("Erro interno não mapeado encontrado: ", e); // Já em PT
        return io.grpc.Status.INTERNAL; // Default para erros inesperados
    }

    /**
     * Loga e envia um erro gRPC padrão para o observer.
     */
    private <T> void handleGrpcError(Exception e, StreamObserver<T> responseObserver, String operationDescription) {
        log.error("Erro ao processar {}: {}", operationDescription, e.getMessage(), e);
        io.grpc.Status status = mapExceptionToStatus(e);
        responseObserver.onError(status
            .withDescription("Erro ao processar " + operationDescription + ": " + e.getMessage()) // Já em PT
            .withCause(e)
            .asRuntimeException());
    }

    /**
     * Loga e envia um erro gRPC específico para falha ao encontrar coordenador.
     */
    private <T> void handleCoordinatorNotFoundError(StreamObserver<T> responseObserver, String operationDescription) {
        log.error("Não é possível processar {}: Coordenador não encontrado.", operationDescription); // Já em PT
        responseObserver.onError(io.grpc.Status.UNAVAILABLE
            .withDescription("Coordenador não disponível para processar a solicitação de " + operationDescription + ".") // Já em PT
            .asRuntimeException());
    }

    /**
     * Loga e envia um erro gRPC específico para falha ao encaminhar.
     */
     private <T> void handleForwardingError(Server coordinator, Exception e, StreamObserver<T> responseObserver, String operationDescription) {
         log.error("Erro ao encaminhar {} para o coordenador {}: {}", operationDescription, coordinator.getId(), e.getMessage(), e); // Já em PT
         responseObserver.onError(io.grpc.Status.INTERNAL
             .withDescription("Erro ao encaminhar solicitação de " + operationDescription + " para o coordenador: " + e.getMessage()) // Já em PT
             .asRuntimeException());
    }

    // --- Método Auxiliar para Encaminhamento Simples (retornando ReplicationResponse) ---

    /**
     * Encapsula a lógica comum: Se coordenador, executa ação local e responde sucesso.
     * Senão, encaminha a ação para o coordenador.
     * Usado para RPCs que retornam ReplicationResponse após uma ação de escrita.
     */
    private void handleSimpleCoordinatorForwarding(
            String operationName,
            Runnable coordinatorAction, // Ação a ser executada pelo coordenador (inclui iniciar replicação)
            Supplier<ReplicationResponse> forwarderAction, // Função que chama o gRPC client para encaminhar
            StreamObserver<ReplicationResponse> responseObserver) {

        if (electionService.isCurrentNodeCoordinator()) {
            try {
                coordinatorAction.run(); // Executa a ação local + inicia replicação
                ReplicationResponse response = ReplicationResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage(operationName + " processado com sucesso pelo coordenador.") // Traduzir
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                log.info("{} processado localmente pelo coordenador {}. Replicação (se aplicável) iniciada.", operationName, selfId); // Já em PT
            } catch (Exception e) {
                handleGrpcError(e, responseObserver, operationName);
            }
        } else {
            // Tenta obter o coordenador e encaminhar
            electionService.getCoordinatorId()
                .flatMap(electionService::getCoordinatorServerDetails)
                .ifPresentOrElse(
                    coordinator -> {
                        log.info("Nó {} não é coordenador. Encaminhando {} para o coordenador {} em {}:{}", // Já em PT
                                 selfId, operationName, coordinator.getId(), coordinator.getHost(), coordinator.getPort());
                        try {
                            ReplicationResponse coordinatorResponse = forwarderAction.get(); // Chama a função de encaminhamento
                            responseObserver.onNext(coordinatorResponse);
                            responseObserver.onCompleted();
                        } catch (Exception forwardException) {
                            handleForwardingError(coordinator, forwardException, responseObserver, operationName);
                       }
                    },
                    () -> handleCoordinatorNotFoundError(responseObserver, operationName) // Handler para coordenador não encontrado
                );
        }
    }

    private int getGrpcPortFromInstance(ServiceInstance instance) {
        String grpcPortStr = instance.getMetadata().get("gRPC_port");
        if (grpcPortStr == null) {
             log.warn("Metadado 'gRPC_port' não encontrado para a instância {} via DiscoveryClient. Retornando -1.", instance.getInstanceId());
             return -1; 
        }
        try {
            return Integer.parseInt(grpcPortStr);
        } catch (NumberFormatException e) {
            log.warn("Não foi possível converter o metadado gRPC_port ('{}') para a instância {}. Erro: {}", grpcPortStr, instance.getInstanceId(), e.getMessage());
            return -1;
        }
    }

    private String getServerIdFromInstance(ServiceInstance instance) {
        String serverId = instance.getMetadata().get("server-id");
        if (serverId == null || serverId.trim().isEmpty()) {
             log.warn("Metadado 'server-id' não encontrado ou vazio para a instância {} via DiscoveryClient. Retornando fallback.", instance.getInstanceId());
             return "unknown-" + instance.getInstanceId(); 
        }
        return serverId.trim();
    }
} 
