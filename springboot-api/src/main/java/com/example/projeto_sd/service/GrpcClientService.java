package com.example.projeto_sd.service;

import com.example.projeto_sd.grpc.ServerCommsProto.*;
import com.example.projeto_sd.grpc.ServerCommsProto;
import com.example.projeto_sd.grpc.ServerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.List;

@Service
@Slf4j
public class GrpcClientService {


    private final ConcurrentMap<String, ServerServiceGrpc.ServerServiceBlockingStub> stubs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    /**
     * Obtém ou cria um stub gRPC bloqueante para o alvo especificado.
     * Gerencia o cache de canais e stubs.
     *
     * @param target O endereço do servidor no formato "host:port".
     * @return O stub gRPC bloqueante.
     */
    private ServerServiceGrpc.ServerServiceBlockingStub getStub(String target) {
        return stubs.computeIfAbsent(target, key -> {
            log.debug("Criando novo canal gRPC e stub para o alvo: {}", key);
            ManagedChannel channel = ManagedChannelBuilder.forTarget(key)
                    .usePlaintext()  
                    .build();
            channels.put(key, channel);
            return ServerServiceGrpc.newBlockingStub(channel);
        });
    }

    /**
     * Tenta desligar todos os canais gRPC gerenciados.
     * Chamado quando a aplicação Spring está sendo desligada.
     */
    @PreDestroy
    public void shutdownChannels() {
        log.info("Desligando canais do cliente gRPC...");
        channels.forEach((target, channel) -> {
            log.debug("Desligando canal para o alvo: {}", target);
            if (channel != null && !channel.isShutdown()) {
                try {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    log.error("Desligamento do canal gRPC interrompido para o alvo: {}", target, e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Erro ao desligar canal gRPC para o alvo: {}", target, e);
                }
            }
        });
        stubs.clear();
        channels.clear();
        log.info("Desligamento dos canais do cliente gRPC concluído.");
    }


    // --- Métodos RPC ---

    /**
     * Envia solicitação de registro/atualização para um peer.
     */
    public ServerRegistrationResponse registerWithServer(String peerHost, int peerPort, ServerInfo selfInfo) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        try {
            log.debug("Enviando requisição de registro para {}", target);
            return stub.withDeadlineAfter(5, TimeUnit.SECONDS).registerOrUpdateRemoteServer(selfInfo);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar registerOrUpdateRemoteServer em {}: Status={}", target, e.getStatus(), e);
            return ServerRegistrationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar registerOrUpdateRemoteServer em {}: {}", target, e.getMessage(), e);
             return ServerRegistrationResponse.newBuilder().setSuccess(false).setMessage("Erro: " + e.getMessage()).build();
        }
    }

    /**
     * Anuncia o coordenador para um peer.
     */
    public void announceCoordinatorToPeer(String peerHost, int peerPort, String coordinatorId) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        CoordinatorAnnouncement request = CoordinatorAnnouncement.newBuilder().setCoordinatorId(coordinatorId).build();
        try {
            log.debug("Anunciando coordenador {} para o peer {}", coordinatorId, target);
            stub.withDeadlineAfter(5, TimeUnit.SECONDS).announceCoordinator(request);
            log.info("Coordenador {} anunciado com sucesso para o peer {}", coordinatorId, target);
        } catch (StatusRuntimeException e) {
            log.error("Falha ao anunciar coordenador {} para o peer {}: Status={}", coordinatorId, target, e.getStatus(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao anunciar coordenador {} para {}: {}", coordinatorId, target, e.getMessage(), e);
        }
    }

    /**
     * Envia um heartbeat para um peer.
     */
    public HeartbeatResponse sendHeartbeatToPeer(String peerHost, int peerPort, String selfId) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        HeartbeatRequest request = HeartbeatRequest.newBuilder().setServerId(selfId).build();
        try {
            log.trace("Enviando heartbeat para {}", target);
            return stub.withDeadlineAfter(2, TimeUnit.SECONDS).receiveHeartbeat(request);
        } catch (StatusRuntimeException e) {
            log.warn("Falha ao enviar heartbeat para o peer {}: Status={}", target, e.getStatus());
            return null;
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar heartbeat para {}: {}", target, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Solicita o tempo de um peer.
     */
    public GetTimeResponse requestTimeFromPeer(String peerHost, int peerPort) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        GetTimeRequest request = GetTimeRequest.newBuilder().build();
        try {
            log.trace("Solicitando tempo de {}", target);
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS).getTime(request);
        } catch (StatusRuntimeException e) {
            log.warn("Falha ao obter tempo do peer {}: Status={}", target, e.getStatus());
            return null;
        } catch (Exception e) {
            log.error("Erro inesperado ao solicitar tempo de {}: {}", target, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Envia um ajuste de tempo para um peer.
     */
     public AdjustTimeResponse sendTimeAdjustmentToPeer(String peerHost, int peerPort, long offsetMillis) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        AdjustTimeRequest request = AdjustTimeRequest.newBuilder().setTimeOffsetMillis(offsetMillis).build();
        try {
            log.debug("Enviando ajuste de tempo ({}) para {}", offsetMillis, target);
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS).adjustServerTime(request);
        } catch (StatusRuntimeException e) {
            log.warn("Falha ao enviar ajuste de tempo para o peer {}: Status={}", target, e.getStatus());
            return null;
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar ajuste de tempo para {}: {}", target, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Chama o RPC createUserRPC em um servidor remoto (espera-se que seja o líder).
     */
    public UserResponse createUserOnLeader(String target, String name) {
        log.debug("Enviando requisição createUserRPC para {} para o nome de usuário: {}", target, name);
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        CreateUserRequest request = CreateUserRequest.newBuilder()
                .setName(name)
                .build();
        try {
            UserResponse response = stub.withDeadlineAfter(5, TimeUnit.SECONDS).createUserRPC(request);
            log.debug("Resposta recebida de createUserRPC em {}: sucesso={}", target, response != null);
            return response;
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar createUserRPC em {}: Status={} - {}", target, e.getStatus(), e.getStatus().getDescription(), e);
            return null;
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar createUserRPC em {}: {}", target, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Chama o RPC replicateUserCreation em um peer para replicar dados de usuário.
     */
    public ReplicationResponse replicateUserCreationToPeer(String peerHost, int peerPort, UserInfo userInfo) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        log.debug("Enviando requisição replicateUserCreation para {} para o ID de usuário: {}", target, userInfo.getId());
        try {
            return stub.withDeadlineAfter(5, TimeUnit.SECONDS).replicateUserCreation(userInfo);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar replicateUserCreation em {}: Status={}", target, e.getStatus(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar replicateUserCreation em {}: {}", target, e.getMessage(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro: " + e.getMessage()).build();
        }
    }

    // --- Novos Métodos RPC para Follow/Unfollow ---

    /**
     * Chama o RPC FollowUserRPC no líder.
     */
    public ReplicationResponse followUserOnLeader(String target, String followerId, String followedId) {
        log.debug("Enviando requisição FollowUserRPC para {} para seguidor={}, seguido={}", target, followerId, followedId);
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        FollowRequest request = FollowRequest.newBuilder()
                .setFollowerId(followerId)
                .setFollowedId(followedId)
                .build();
        try {
            return stub.withDeadlineAfter(5, TimeUnit.SECONDS).followUserRPC(request);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar FollowUserRPC em {}: Status={} - {}", target, e.getStatus(), e.getStatus().getDescription(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar FollowUserRPC em {}: {}", target, e.getMessage(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro: " + e.getMessage()).build();
        }
    }

    /**
     * Chama o RPC UnfollowUserRPC no líder.
     */
    public ReplicationResponse unfollowUserOnLeader(String target, String followerId, String followedId) {
        log.debug("Enviando requisição UnfollowUserRPC para {} para seguidor={}, seguido={}", target, followerId, followedId);
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        FollowRequest request = FollowRequest.newBuilder()
                .setFollowerId(followerId)
                .setFollowedId(followedId)
                .build();
        try {
            return stub.withDeadlineAfter(5, TimeUnit.SECONDS).unfollowUserRPC(request);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar UnfollowUserRPC em {}: Status={} - {}", target, e.getStatus(), e.getStatus().getDescription(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar UnfollowUserRPC em {}: {}", target, e.getMessage(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro: " + e.getMessage()).build();
        }
    }

    /**
     * Chama o RPC ReplicateFollow em um peer.
     */
    public ReplicationResponse replicateFollowToPeer(String peerHost, int peerPort, String followerId, String followedId) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        FollowRequest request = FollowRequest.newBuilder()
                .setFollowerId(followerId)
                .setFollowedId(followedId)
                .build();
        log.debug("Enviando requisição ReplicateFollow para {} para {} -> {}", target, followerId, followedId);
        try {
            return stub.withDeadlineAfter(5, TimeUnit.SECONDS).replicateFollow(request);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar ReplicateFollow em {}: Status={}", target, e.getStatus(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar ReplicateFollow em {}: {}", target, e.getMessage(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro: " + e.getMessage()).build();
        }
    }

    /**
     * Chama o RPC ReplicateUnfollow em um peer.
     */
    public ReplicationResponse replicateUnfollowToPeer(String peerHost, int peerPort, String followerId, String followedId) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        FollowRequest request = FollowRequest.newBuilder()
                .setFollowerId(followerId)
                .setFollowedId(followedId)
                .build();
        log.debug("Enviando requisição ReplicateUnfollow para {} para {} deixar de seguir {}", target, followerId, followedId);
        try {
            return stub.withDeadlineAfter(5, TimeUnit.SECONDS).replicateUnfollow(request);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar ReplicateUnfollow em {}: Status={}", target, e.getStatus(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar ReplicateUnfollow em {}: {}", target, e.getMessage(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro: " + e.getMessage()).build();
        }
    }

    // --- Métodos de Encaminhamento para o Coordenador ---

    /**
     * Encaminha a chamada createUserRPC para o servidor coordenador especificado.
     *
     * @param coordinatorHost Host do coordenador.
     * @param coordinatorPort Porta gRPC do coordenador.
     * @param request A requisição original.
     * @return A resposta recebida do coordenador.
     * @throws StatusRuntimeException Se ocorrer um erro gRPC durante o encaminhamento.
     */
    public UserResponse forwardCreateUserRPC(String coordinatorHost, int coordinatorPort, CreateUserRequest request) {
        String target = coordinatorHost + ":" + coordinatorPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        log.debug("Encaminhando requisição createUserRPC para o coordenador {}", target);
        return stub.withDeadlineAfter(10, TimeUnit.SECONDS).createUserRPC(request);
    }

    /**
     * Encaminha a chamada sendMessageRPC para o servidor coordenador especificado.
     *
     * @param coordinatorHost Host do coordenador.
     * @param coordinatorPort Porta gRPC do coordenador.
     * @param request A requisição original SendMessageRequest Protobuf.
     * @return A resposta SendMessageResponse recebida do coordenador.
     * @throws StatusRuntimeException Se ocorrer um erro gRPC durante o encaminhamento.
     */
    public SendMessageResponse forwardSendMessageRPC(String coordinatorHost, int coordinatorPort, SendMessageRequest request) {
        String target = coordinatorHost + ":" + coordinatorPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        log.debug("Encaminhando requisição sendMessageRPC (de {} para {}) para o coordenador {}", 
                  request.getSenderId(), request.getReceiverId(), target);
        return stub.withDeadlineAfter(10, TimeUnit.SECONDS).sendMessageRPC(request);
    }

    /**
     * Encaminha a chamada followUserRPC para o servidor coordenador especificado.
     *
     * @param coordinatorHost Host do coordenador.
     * @param coordinatorPort Porta gRPC do coordenador.
     * @param request A requisição original.
     * @return A resposta recebida do coordenador.
     * @throws StatusRuntimeException Se ocorrer um erro gRPC durante o encaminhamento.
     */
    public ReplicationResponse forwardFollowUserRPC(String coordinatorHost, int coordinatorPort, FollowRequest request) {
        String target = coordinatorHost + ":" + coordinatorPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        log.debug("Encaminhando requisição followUserRPC ({} -> {}) para o coordenador {}", request.getFollowerId(), request.getFollowedId(), target);
        return stub.withDeadlineAfter(10, TimeUnit.SECONDS).followUserRPC(request);
    }

    /**
     * Encaminha a chamada unfollowUserRPC para o servidor coordenador especificado.
     *
     * @param coordinatorHost Host do coordenador.
     * @param coordinatorPort Porta gRPC do coordenador.
     * @param request A requisição original.
     * @return A resposta recebida do coordenador.
     * @throws StatusRuntimeException Se ocorrer um erro gRPC durante o encaminhamento.
     */
    public ReplicationResponse forwardUnfollowUserRPC(String coordinatorHost, int coordinatorPort, FollowRequest request) {
        String target = coordinatorHost + ":" + coordinatorPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        log.debug("Encaminhando requisição unfollowUserRPC ({} deixar de seguir {}) para o coordenador {}", request.getFollowerId(), request.getFollowedId(), target);
        return stub.withDeadlineAfter(10, TimeUnit.SECONDS).unfollowUserRPC(request);
    }

    // ----- Métodos de Encaminhamento e Replicação para Posts -----

    /**
     * Encaminha a chamada createPostRPC para o servidor coordenador especificado.
     *
     * @param coordinatorHost Host do coordenador.
     * @param coordinatorPort Porta gRPC do coordenador.
     * @param request A requisição original (DTO precisa ser convertido ou passado como proto).
     * @return A resposta recebida do coordenador.
     * @throws StatusRuntimeException Se ocorrer um erro gRPC durante o encaminhamento.
     */
    public CreatePostResponse forwardCreatePostRPC(String coordinatorHost, int coordinatorPort, CreatePostRequest protoRequest) {
        String target = coordinatorHost + ":" + coordinatorPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        log.debug("Encaminhando requisição createPostRPC para o coordenador {}", target);
        return stub.withDeadlineAfter(10, TimeUnit.SECONDS).createPostRPC(protoRequest);
    }

    /**
     * Chama o RPC ReplicatePostCreation em um peer.
     *
     * @param peerHost Host do peer.
     * @param peerPort Porta gRPC do peer.
     * @param postInfo Os dados do post a serem replicados.
     * @return A resposta da replicação.
     * @throws StatusRuntimeException Se ocorrer um erro gRPC.
     */
    public ServerCommsProto.ReplicationResponse replicatePostCreationToPeer(String peerHost, int peerPort, ServerCommsProto.PostInfo postInfo) {
        log.debug("Replicando criação do post {} para o peer {}:{}", postInfo.getId(), peerHost, peerPort);
        try {
            ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(peerHost + ":" + peerPort);
            
            log.info("Enviando replicação de post para {}:{} com ID {} e relógio lógico {}", 
                    peerHost, peerPort, postInfo.getId(), postInfo.getLogicalClock());
            
            ServerCommsProto.ReplicationResponse response = stub.replicatePostCreation(postInfo);
            return response;
        } catch (Exception e) {
            log.error("Falha ao replicar post {} para o peer {}:{}: {}", postInfo.getId(), peerHost, peerPort, e.getMessage(), e);
            return null;
        }
    }

    // ----- Métodos de Encaminhamento e Replicação para Deletar Posts -----

    /**
     * Encaminha a chamada deletePostRPC para o servidor coordenador especificado.
     */
    public ReplicationResponse forwardDeletePostRPC(String coordinatorHost, int coordinatorPort, String postId, String userId) {
        String target = coordinatorHost + ":" + coordinatorPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        DeletePostRequest protoRequest = DeletePostRequest.newBuilder()
            .setPostId(postId)
            .setUserId(userId)
            .build();
        log.debug("Encaminhando requisição deletePostRPC (post={}, usuário={}) para o coordenador {}", postId, userId, target);
        return stub.withDeadlineAfter(10, TimeUnit.SECONDS).deletePostRPC(protoRequest);
    }

    /**
     * Chama o RPC ReplicatePostDeletion em um peer.
     */
    public ReplicationResponse replicatePostDeletionToPeer(String peerHost, int peerPort, String postId) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        ReplicatePostDeletionRequest request = ReplicatePostDeletionRequest.newBuilder()
            .setPostId(postId)
            .build();
        log.debug("Enviando requisição ReplicatePostDeletion para {} para o ID do post: {}", target, postId);
        return stub.withDeadlineAfter(5, TimeUnit.SECONDS).replicatePostDeletion(request);
    }

    // --- NOVO MÉTODO PARA REPLICAR NOTIFICAÇÃO ---

    /**
     * Replica uma notificação para um peer.
     */
     public ServerCommsProto.ReplicationResponse replicateNotificationToPeer(String host, int port, ServerCommsProto.NotificationProto notificationProto) {
        ManagedChannel channel = null;
        try {
            channel = getChannel(host, port);
            ServerServiceGrpc.ServerServiceBlockingStub stub = ServerServiceGrpc.newBlockingStub(channel);

            // Construir a requisição de replicação
            ServerCommsProto.ReplicateNotificationRequest request = ServerCommsProto.ReplicateNotificationRequest.newBuilder()
                    .setNotification(notificationProto) // Passa o NotificationProto recebido
                    // .setCoordinatorId(this.selfId) // O coordinatorId não é mais passado como arg, pode ser obtido de outra forma se necessário no futuro
                    .build();

            log.debug("Replicando notificação {} para o peer {}:{}", notificationProto.getId(), host, port);
            ServerCommsProto.ReplicationResponse response = stub.withDeadlineAfter(5, TimeUnit.SECONDS).replicateNotification(request);
            log.debug("Resposta da replicação para notificação {} de {}:{}: {}", notificationProto.getId(), host, port, response.getSuccess());
            return response;
        } catch (StatusRuntimeException e) {
             log.error("Erro gRPC ao replicar notificação {} para {}:{}: Status={}. Mensagem={}",
                    notificationProto.getId(), host, port, e.getStatus(), e.getMessage());
            return ServerCommsProto.ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        }
    }

    // --- Added Methods ---

    /**
     * Encaminha uma solicitação para marcar notificações como lidas para o coordenador.
     */
    public void forwardMarkNotificationsRead(String host, int port, String userId, List<String> notificationIds) {
        ManagedChannel channel = null;
        try {
            channel = getChannel(host, port);
            ServerServiceGrpc.ServerServiceBlockingStub stub = ServerServiceGrpc.newBlockingStub(channel);
            ServerCommsProto.MarkNotificationsReadRequest request = ServerCommsProto.MarkNotificationsReadRequest.newBuilder()
                    .setUserId(userId)
                    .addAllNotificationIds(notificationIds)
                    .build();

            log.debug("Encaminhando requisição para marcar notificações como lidas para o usuário {} para {}:{}", userId, host, port);
            stub.withDeadlineAfter(5, TimeUnit.SECONDS).forwardMarkNotificationsRead(request);
            log.info("Requisição para marcar notificações como lidas para o usuário {} encaminhada com sucesso para {}:{}", userId, host, port);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao encaminhar marcação de notificações como lidas para o usuário {} para {}:{}: Status={}. Mensagem={}",
                    userId, host, port, e.getStatus(), e.getMessage());
            throw e;
        }
    }

    /**
     * Replica a ação de marcar notificações como lidas para um peer.
     */
    public ServerCommsProto.ReplicationResponse replicateMarkNotificationsRead(String host, int port, String userId, List<String> notificationIds) {
        ManagedChannel channel = null;
        try {
            channel = getChannel(host, port);
            ServerServiceGrpc.ServerServiceBlockingStub stub = ServerServiceGrpc.newBlockingStub(channel);
            ServerCommsProto.MarkNotificationsReadRequest request = ServerCommsProto.MarkNotificationsReadRequest.newBuilder()
                    .setUserId(userId)
                    .addAllNotificationIds(notificationIds)
                    .build();

            log.debug("Replicando marcação de notificações como lidas para o usuário {} para {}:{}", userId, host, port);
            ServerCommsProto.ReplicationResponse response = stub.withDeadlineAfter(5, TimeUnit.SECONDS).replicateMarkNotificationsRead(request);
            log.debug("Resposta da replicação de {}:{} para o usuário {} marcando notificações como lidas: {}", host, port, userId, response.getSuccess());
            return response;
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao replicar marcação de notificações como lidas para o usuário {} para {}:{}: Status={}. Mensagem={}",
                    userId, host, port, e.getStatus(), e.getMessage());
            return ServerCommsProto.ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        }
    }

    // --- Método para Replicar Mensagem ---

    /**
     * Replica os dados de uma mensagem para um peer.
     *
     * @param peerHost Host do peer.
     * @param peerPort Porta gRPC do peer.
     * @param messageInfo Os dados da mensagem a serem replicados.
     * @return A resposta da replicação.
     * @throws StatusRuntimeException Se ocorrer um erro gRPC.
     */
    public ReplicationResponse replicateMessageToPeer(String peerHost, int peerPort, MessageInfo messageInfo) {
        String target = peerHost + ":" + peerPort;
        ServerServiceGrpc.ServerServiceBlockingStub stub = getStub(target);
        log.debug("Enviando requisição ReplicateMessage para {} para o ID da mensagem: {}", target, messageInfo.getId());

        ReplicateMessageRequest request = ReplicateMessageRequest.newBuilder()
                .setMessageInfo(messageInfo)
                .build();
        try {
            return stub.withDeadlineAfter(5, TimeUnit.SECONDS).replicateMessage(request);
        } catch (StatusRuntimeException e) {
            log.error("Erro gRPC ao chamar ReplicateMessage em {}: Status={}", target, e.getStatus(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro gRPC: " + e.getStatus()).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar ReplicateMessage em {}: {}", target, e.getMessage(), e);
            return ReplicationResponse.newBuilder().setSuccess(false).setMessage("Erro: " + e.getMessage()).build();
        }
    }

    private ManagedChannel getChannel(String host, int port) {
        String target = host + ":" + port;
        return channels.computeIfAbsent(target, t -> {
            log.info("Criando novo canal gRPC para o alvo: {}", t);
            return ManagedChannelBuilder.forTarget(t)
                    .usePlaintext()
                    .build();
        });
    }

    // Método para fechar todos os canais quando a aplicação desliga
    @PreDestroy
    public void shutdownAllChannels() {
        log.info("Desligando todos os canais do cliente gRPC...");
        channels.values().forEach(channel -> {
            try {
                if (!channel.isShutdown()) {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                log.error("Interrompido durante o desligamento do canal gRPC: {}", channel, e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Erro ao desligar canal gRPC: {}", channel, e);
            }
        });
        channels.clear();
        stubs.clear(); // Limpa os stubs também
        log.info("Concluído o desligamento dos canais do cliente gRPC.");
    }
}