package com.example.projeto_sd.service;

import com.example.projeto_sd.dto.post.CreatePostRequestDto;
import com.example.projeto_sd.dto.user.UserResponseDTO;
import com.example.projeto_sd.model.Post;
import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.model.User;
import com.example.projeto_sd.repository.PostRepository;
import com.example.projeto_sd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.projeto_sd.grpc.ServerCommsProto;
import com.example.projeto_sd.dto.post.PostResponseDto;
import com.example.projeto_sd.dto.post.DeletePostRequestDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.projeto_sd.repository.NotificationRepository;
import com.example.projeto_sd.model.Notification;

import java.time.ZoneOffset;
import java.time.Instant;

import java.time.ZoneId;
import com.google.protobuf.Timestamp;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final com.example.projeto_sd.repository.ServerRepository serverRepository;
    private final ElectionService electionService;
    private final GrpcClientService grpcClientService;
    private final ClockSyncService clockSyncService;
    private final FollowService followService;
    private final NotificationRepository notificationRepository;
    private final LogicalClock logicalClock;

    @Value("${server.id}")
    private String selfServerId;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${spring.application.name}")
    private String selfServiceName;

    private int getGrpcPortFromInstance(ServiceInstance instance) {
        String grpcPortStr = instance.getMetadata().get("gRPC_port");
        if (grpcPortStr == null) {
             log.warn("Metadado 'gRPC_port' não encontrado para a instância {}. Usando porta padrão ou retornando -1.", instance.getInstanceId());
             return -1; 
        }
        try {
            return Integer.parseInt(grpcPortStr);
        } catch (NumberFormatException e) {
            log.warn("Não foi possível converter o metadata gRPC_port ('{}') para a instância {}. Erro: {}", grpcPortStr, instance.getInstanceId(), e.getMessage());
            return -1;
        }
    }

    private String getServerIdFromInstance(ServiceInstance instance) {
        String serverId = instance.getMetadata().get("server-id");
        if (serverId == null || serverId.trim().isEmpty()) {
             log.warn("Não foi possível encontrar ou metadado 'server-id' está vazio para a instância {}.", instance.getInstanceId());
             return "unknown-" + instance.getInstanceId();
        }
        return serverId.trim();
    }

    @Transactional
    public void createPost(CreatePostRequestDto requestDto) {
        log.info("Processando requisição createPost para o usuário: {}", requestDto.getUserId());

        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + requestDto.getUserId()));

        if (electionService.isCoordinator()) {
            log.info("Nó {} é o coordenador. Processando createPost localmente.", selfServerId);
            processAndReplicatePost(user, requestDto.getContent());
        } else {
            Optional<Server> coordinatorOpt = electionService.getCoordinatorId()
                                                               .flatMap(electionService::getCoordinatorServerDetails);
            if (coordinatorOpt.isPresent()) {
                Server coordinator = coordinatorOpt.get();
                log.info("Nó {} não é o coordenador. Encaminhando requisição createPost para o coordenador {} em {}:{}",
                         selfServerId, coordinator.getId(), coordinator.getHost(), coordinator.getPort());
                
                ServerCommsProto.CreatePostRequest protoRequest = ServerCommsProto.CreatePostRequest.newBuilder()
                    .setUserId(requestDto.getUserId())
                    .setContent(requestDto.getContent())
                    .build();

                try {
                    grpcClientService.forwardCreatePostRPC(coordinator.getHost(), coordinator.getPort(), protoRequest);
                } catch (Exception e) {
                    log.error("Erro ao encaminhar requisição createPost para o coordenador {}: {}", coordinator.getId(), e.getMessage(), e);
                    throw new IllegalStateException("Falha ao encaminhar requisição para o coordenador.", e);
                }
            } else {
                log.error("Não foi possível criar o post: Coordenador não encontrado.");
                throw new IllegalStateException("Coordenador não disponível para processar a requisição.");
            }
        }
    }

    /**
     * Método interno do coordenador para criar, salvar e iniciar a replicação do post.
     * AGORA RETORNA O POST CRIADO e é público.
     */
    @Transactional
    public Post processAndReplicatePost(User user, String content) {
        LocalDateTime now = clockSyncService.getCurrentCorrectedLocalDateTime();
        
        int postLogicalClock = logicalClock.increment();
        
        Server coordinadorServerEntity = serverRepository.findById(selfServerId)
                .orElseThrow(() -> new IllegalStateException("Servidor local (coordenador) " + selfServerId + " não encontrado no BD ao processar post."));

        Post newPost = new Post();
        newPost.setId(UUID.randomUUID().toString());
        newPost.setUser(user);
        newPost.setContent(content);
        newPost.setCreatedAt(now);
        newPost.setLogicalClock(postLogicalClock);
        newPost.setDeleted(false);
        newPost.setServer(coordinadorServerEntity);

        Post savedPost = postRepository.save(newPost);
        log.info("[CreatePost-Coord] Post {} do usuário {} salvo localmente com sucesso (relógio lógico: {}, servidor: {}).", 
                savedPost.getId(), user.getId(), postLogicalClock, selfServerId);

        replicatePostCreation(savedPost);

        createAndSaveNotifications(savedPost);

        return savedPost;
    }

    /**
     * Inicia o processo de replicação de um post recém-criado.
     * (Executado pelo Coordenador)
     */
    private void replicatePostCreation(Post post) {
        log.info("[Replicação-PostCriado] Servidor {} (coordenador) iniciando replicação para o post {}.", selfServerId, post.getId());

        List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfServerId) && !"unknown".startsWith(peerServerId);
                })
                .collect(Collectors.toList());

        if (activePeerInstances.isEmpty()) {
            log.info("[Replicação-PostCriado] Nenhum peer ativo encontrado via Consul. Pulando replicação.");
            return;
        }

        long createdAtMillis = post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        ServerCommsProto.PostInfo postInfo = ServerCommsProto.PostInfo.newBuilder()
                .setId(post.getId())
                .setUserId(post.getUser().getId())
                .setContent(post.getContent())
                .setCreatedAtMillis(createdAtMillis)
                .setLogicalClock(post.getLogicalClock())
                .build();

        log.info("[Replicação-PostCriado] Iniciando replicação do post {} (relógio lógico: {}) para {} peers ativos via Consul", 
                 post.getId(), post.getLogicalClock(), activePeerInstances.size());
        
        for (ServiceInstance peerInstance : activePeerInstances) {
            String peerHost = peerInstance.getHost();
            int peerPort = getGrpcPortFromInstance(peerInstance);
            String peerServerId = getServerIdFromInstance(peerInstance);

            if (peerPort == -1) {
                log.warn("[Replicação-PostCriado] Não foi possível obter a porta gRPC para o peer {}. Pulando replicação para este peer.", peerServerId);
                continue;
            }

            try {
                ServerCommsProto.ReplicationResponse response = 
                        grpcClientService.replicatePostCreationToPeer(peerHost, peerPort, postInfo);
                
                if (response == null || !response.getSuccess()) {
                    log.error("[Replicação-PostCriado] Falha ao replicar post {} para o peer {} ({}:{}): {}", 
                              post.getId(), peerServerId, peerHost, peerPort,
                              response != null ? response.getMessage() : "Sem resposta");
                } else {
                    log.info("[Replicação-PostCriado] Post {} replicado com sucesso para o peer {} ({}:{})", post.getId(), peerServerId, peerHost, peerPort);
                }
            } catch (Exception e) {
                log.error("[Replicação-PostCriado] Erro durante a replicação do post {} para o peer {} ({}:{}): {}", 
                          post.getId(), peerServerId, peerHost, peerPort, e.getMessage(), e);
            }
        }
        log.info("[Replicação-PostCriado] Processo de replicação finalizado para o post {}", post.getId());
    }

    /**
     * Cria e salva no banco de dados as notificações para todos os seguidores do autor do post.
     * (Executado pelo Coordenador)
     */
    private void createAndSaveNotifications(Post post) {
        String authorId = post.getUser().getId();
        log.info("[Notificações-PostCriado] Coordenador ({}) iniciando criação de notificações no BD para o post {} do autor {}", selfServerId, post.getId(), authorId);

        List<UserResponseDTO> followers = followService.getFollowers(authorId);

        if (followers.isEmpty()) {
            log.info("[Notificações-PostCriado] Autor {} não possui seguidores. Nenhuma notificação de BD a ser criada para o post {}.", authorId, post.getId());
            return;
        }

        log.info("Encontrados {} seguidores para o autor {}. Criando notificações no BD.", followers.size(), authorId);

        List<String> followerIds = followers.stream().map(UserResponseDTO::getId).collect(Collectors.toList());
        List<User> followerUsers = userRepository.findAllById(followerIds); 

        if (followerUsers.isEmpty()) {
            log.warn("[Notificações-PostCriado] Não foi possível encontrar entidades User para os IDs dos seguidores. Pulando salvamento de notificações.");
            return;
        }

        List<Notification> notificationsToSave = new ArrayList<>();
        String message = String.format("Usuário '%s' publicou um novo post.", post.getUser().getName());

        for (User follower : followerUsers) {
            Notification notification = new Notification();
            notification.setId(UUID.randomUUID().toString());
            notification.setUser(follower);
            notification.setType("NEW_POST");
            notification.setMessage(message);
            notification.setRelatedEntityId(post.getId());
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
            notificationsToSave.add(notification);
        }

        try {
            notificationRepository.saveAll(notificationsToSave);
            log.info("[Notificações-PostCriado] Salvas {} notificações no BD com sucesso para o post {}", notificationsToSave.size(), post.getId());
        } catch (Exception e) {
            log.error("[Notificações-PostCriado] Erro ao salvar notificações no BD para o post {}: {}", post.getId(), e.getMessage(), e);
        }

        if (!notificationsToSave.isEmpty()) {
            log.info("[Notificações-PostCriado] Coordenador ({}) iniciando replicação de {} notificações para o post {}", selfServerId, notificationsToSave.size(), post.getId());
            
            List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfServerId) && !"unknown".startsWith(peerServerId);
                })
                .collect(Collectors.toList());

            if (activePeerInstances.isEmpty()) {
                 log.warn("[Notificações-PostCriado] Nenhum peer ativo encontrado via Consul para replicar notificações para o post {}.", post.getId());
                 return;
            }

            notificationsToSave.forEach(notificationEntity -> {
                Timestamp createdAtProto = Timestamp.newBuilder()
                    .setSeconds(notificationEntity.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                    .setNanos(notificationEntity.getCreatedAt().getNano())
                    .build();
                ServerCommsProto.NotificationProto notificationProto = ServerCommsProto.NotificationProto.newBuilder()
                    .setId(notificationEntity.getId())
                    .setUserId(notificationEntity.getUser().getId())
                    .setType(notificationEntity.getType())
                    .setMessage(notificationEntity.getMessage())
                    .setRelatedEntityId(notificationEntity.getRelatedEntityId() != null ? notificationEntity.getRelatedEntityId() : "")
                    .setIsRead(notificationEntity.isRead())
                    .setCreatedAt(createdAtProto)
                    .build();
                
                activePeerInstances.forEach(peerInstance -> {
                    String peerHost = peerInstance.getHost();
                    int peerPort = getGrpcPortFromInstance(peerInstance);
                    String peerServerId = getServerIdFromInstance(peerInstance);

                    if(peerPort == -1){
                        log.warn("[Notificações-PostCriado] Não foi possível obter porta gRPC para peer {}. Pulando replicação da notificação {} para este peer.", peerServerId, notificationEntity.getId());
                        return;
                    }

                    log.debug("[Notificações-PostCriado] Replicando notificação {} para o peer {} ({}:{})", notificationEntity.getId(), peerServerId, peerHost, peerPort);
                    try {
                        grpcClientService.replicateNotificationToPeer(peerHost, peerPort, notificationProto);
                    } catch (Exception e) {
                        log.error("[Notificações-PostCriado] Erro ao replicar notificação {} para o peer {} ({}:{}): {}", notificationEntity.getId(), peerServerId, peerHost, peerPort, e.getMessage(), e);
                    }
                });
            });
            log.info("[Notificações-PostCriado] Finalizada a iniciação da replicação para as notificações do post {}", post.getId());
        }
    }

     /**
     * Método chamado por RPC para salvar um post replicado.
     * (Executado por Nós Seguidores)
     */
    @Transactional
    public void saveReplicatedPost(String postId, String userId, String content, long createdAtMillis, int logicalClock) {
        log.info("[PostReplicado] Processando post replicado {} do usuário {} com relógio lógico {}.", postId, userId, logicalClock);
        
        this.logicalClock.synchronizeWith(logicalClock);
        
        if (postRepository.existsById(postId)) {
            log.info("[PostReplicado] Post {} já existe. Pulando salvamento.", postId);
            return;
        }

        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado ao salvar post replicado: " + userId));

        Server seguidorServerEntity = serverRepository.findById(selfServerId) 
                .orElseThrow(() -> new IllegalStateException("Servidor local (seguidor) " + selfServerId + " não encontrado no BD ao salvar post replicado."));

        Post post = new Post();
        post.setId(postId);
        post.setUser(userEntity);
        post.setContent(content);
        post.setCreatedAt(Instant.ofEpochMilli(createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDateTime());
        post.setLogicalClock(logicalClock);
        post.setDeleted(false);
        post.setServer(seguidorServerEntity);
        
        postRepository.save(post);
        log.info("[PostReplicado] Post replicado {} salvo com sucesso com relógio lógico {} no servidor {}.", postId, logicalClock, selfServerId);
    }

    /**
     * Busca todos os posts não deletados, paginados, retornando DTOs diretamente.
     */
    public Page<PostResponseDto> getAllPosts(Pageable pageable) {
        log.info("Buscando todos os posts DTOs, página: {}, tamanho: {}", pageable.getPageNumber(), pageable.getPageSize());
        return postRepository.findAllPostsDto(pageable);
    }

    /**
     * Busca todos os posts não deletados de um usuário específico, paginados, retornando DTOs diretamente.
     */
    public Page<PostResponseDto> getPostsByUserId(String userId, Pageable pageable) {
        log.info("Buscando posts DTOs para o usuário: {}, página: {}, tamanho: {}", userId, pageable.getPageNumber(), pageable.getPageSize());
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Usuário não encontrado com ID: " + userId);
        }
        return postRepository.findPostsByUserIdDto(userId, pageable);
    }

    @Transactional
    public void deletePost(String postId, DeletePostRequestDto requestDto) {
        log.info("Processando requisição deletePost para o post ID: {} pelo usuário: {}", postId, requestDto.getUserId());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post não encontrado com ID: " + postId));

        if (post.isDeleted()) {
             log.warn("Post {} já está deletado.", postId);
             return; 
        }

        if (!post.getUser().getId().equals(requestDto.getUserId())) {
            log.error("Usuário {} tentou deletar o post {} pertencente ao usuário {}. Proibido.", 
                      requestDto.getUserId(), postId, post.getUser().getId());
            throw new SecurityException("Usuário não autorizado a deletar este post."); 
        }

        if (electionService.isCoordinator()) {
            log.info("Nó {} é o coordenador. Processando deletePost localmente.", selfServerId);
            processAndDeleteReplicate(post);
        } else {
            Optional<Server> coordinatorOpt = electionService.getCoordinatorId()
                                                               .flatMap(electionService::getCoordinatorServerDetails);
            if (coordinatorOpt.isPresent()) {
                Server coordinator = coordinatorOpt.get();
                log.info("Nó {} não é o coordenador. Encaminhando requisição deletePost para o post {} para o coordenador {} em {}:{}",
                         selfServerId, postId, coordinator.getId(), coordinator.getHost(), coordinator.getPort());
                
                try {
                    grpcClientService.forwardDeletePostRPC(coordinator.getHost(), coordinator.getPort(), postId, requestDto.getUserId());
                } catch (Exception e) {
                    log.error("Erro ao encaminhar requisição deletePost para o coordenador {}: {}", coordinator.getId(), e.getMessage(), e);
                    throw new IllegalStateException("Falha ao encaminhar requisição para o coordenador.", e);
                }
            } else {
                log.error("Não foi possível deletar o post: Coordenador não encontrado.");
                throw new IllegalStateException("Coordenador não disponível para processar a requisição.");
            }
        }
    }

    /**
     * Método interno do coordenador para marcar o post como deletado e replicar.
     */
    @Transactional
    protected void processAndDeleteReplicate(Post post) {
        log.debug("Coordenador marcando o post {} como deletado.", post.getId());
        post.setDeleted(true);
        Post deletedPost = postRepository.save(post);
        log.info("Post {} marcado como deletado localmente pelo coordenador {}. Iniciando replicação...", deletedPost.getId(), selfServerId);

        replicatePostDeletion(deletedPost.getId());
    }

    /**
     * Inicia o processo de replicação de uma deleção de post.
     * (Executado pelo Coordenador)
     */
    private void replicatePostDeletion(String postId) {
        log.info("[Replicação-PostDeletado] Coordenador ({}) iniciando replicação para deleção do post ID: {}", selfServerId, postId);
        
        List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfServerId) && !"unknown".startsWith(peerServerId);
                })
                .collect(Collectors.toList());

        if (activePeerInstances.isEmpty()) {
            log.info("[Replicação-PostDeletado] Nenhum peer ativo encontrado via Consul. Pulando replicação da deleção.");
            return;
        }
        
        log.info("[Replicação-PostDeletado] Iniciando replicação da deleção do post {} para {} peers ativos via Consul", 
                 postId, activePeerInstances.size());

        activePeerInstances.forEach(peerInstance -> {
            String peerHost = peerInstance.getHost();
            int peerPort = getGrpcPortFromInstance(peerInstance);
            String peerServerId = getServerIdFromInstance(peerInstance);
            
            if(peerPort == -1){
                 log.warn("[Replicação-PostDeletado] Não foi possível obter porta gRPC para peer {}. Pulando replicação da deleção do post {} para este peer.", peerServerId, postId);
                return;
            }

            log.debug("[Replicação-PostDeletado] Replicando deleção do post {} para o servidor {} ({}:{})", postId, peerServerId, peerHost, peerPort);
            try {
                ServerCommsProto.ReplicationResponse replicationResponse = 
                    grpcClientService.replicatePostDeletionToPeer(peerHost, peerPort, postId);
                
                if (replicationResponse == null || !replicationResponse.getSuccess()) {
                     log.warn("[Replicação-PostDeletado] Replicação da deleção do post {} para o servidor {} ({}:{}) falhou. Mensagem: {}",
                              postId, peerServerId, peerHost, peerPort, replicationResponse != null ? replicationResponse.getMessage() : "Sem resposta");
                } else {
                     log.info("[Replicação-PostDeletado] Replicação da deleção do post {} para o servidor {} ({}:{}) iniciada com sucesso", postId, peerServerId, peerHost, peerPort);
                }
            } catch (Exception e) {
                log.error("[Replicação-PostDeletado] Erro ao replicar deleção do post {} para o servidor {} ({}:{}): {}", postId, peerServerId, peerHost, peerPort, e.getMessage(), e);
            }
        });
        log.info("[Replicação-PostDeletado] Finalizada a iniciação do processo de replicação para deleção do post ID: {}", postId);
    }

    @Transactional
    public void markReplicatedPostAsDeleted(String postId) {
        log.info("Recebida requisição de replicação para deletar post ID: {}", postId);

        if (electionService.isCoordinator()) {
            log.warn("Coordenador recebeu uma requisição de replicação para deletar o post {}. Ignorando.", postId);
            return;
        }

        postRepository.findById(postId).ifPresent(post -> {
            if (!post.isDeleted()) {
                post.setDeleted(true);
                postRepository.save(post);
                log.info("Post replicado {} marcado como deletado com sucesso.", postId);
            } else {
                 log.warn("Requisição de replicação para deletar post {}, mas já estava deletado localmente.", postId);
            }
        });
        if (!postRepository.existsById(postId)){
             log.warn("Recebida requisição de replicação para deletar post ID inexistente: {}", postId);
        }
    }

} 