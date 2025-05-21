package com.example.projeto_sd.service;

import com.example.projeto_sd.dto.notification.NotificationDTO;
import com.example.projeto_sd.model.Notification;
import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.repository.NotificationRepository;
import com.example.projeto_sd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final GrpcClientService grpcClientService;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private ElectionService electionService;

    @Value("${spring.application.name}")
    private String selfServiceName;

    @Value("${server.id}")
    private String selfServerId;

    private int getGrpcPortFromInstance(ServiceInstance instance) {
        String grpcPortStr = instance.getMetadata().get("gRPC_port");
        if (grpcPortStr == null) {
             log.warn("Metadado 'gRPC_port' não encontrado para a instância {}. Retornando -1.", instance.getInstanceId());
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
             log.warn("Metadado 'server-id' não encontrado ou vazio para a instância {}. Retornando fallback.", instance.getInstanceId());
             return "unknown-" + instance.getInstanceId(); 
        }
        return serverId.trim();
    }

    /**
     * Busca as notificações não lidas para um usuário específico.
     *
     * @param userId O ID do usuário.
     * @return Uma lista de NotificationDTOs representando as notificações não lidas.
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getUnreadNotifications(String userId) {
        log.debug("Buscando notificações não lidas para o usuário ID: {}", userId);

        if (!userRepository.existsById(userId)) {
            log.warn("Tentativa de buscar notificações para usuário inexistente ID: {}", userId);
            return List.of();
        }

        List<Notification> notifications = notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false);

        List<NotificationDTO> dtos = notifications.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        log.info("Encontradas {} notificações não lidas para o usuário ID: {}", dtos.size(), userId);
        return dtos;
    }

    /**
     * Ponto de entrada para marcar notificações como lidas.
     * Verifica se é o coordenador, processa localmente e replica, ou encaminha para o coordenador.
     *
     * @param userId O ID do usuário.
     * @param notificationIds A lista de IDs das notificações a serem marcadas como lidas.
     */
    @Transactional // Needs transaction for potential local write or for consistency during check
    public void markNotificationsAsRead(String userId, List<String> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            log.info("[MarcarLido] Nenhum ID de notificação fornecido para o usuário {}. Ignorando.", userId);
            return;
        }
        log.info("[MarcarLido] Requisição recebida para o usuário {} marcar {} notificações como lidas.", userId, notificationIds.size());

        boolean isCoordinator = electionService.isCoordinator();

        if (isCoordinator) {
            log.info("[MarcarLido] Nó {} é o COORDENADOR. Processando localmente e replicando.", selfServerId);
            processAndReplicateMarkAsRead(userId, notificationIds);
        } else {
            log.info("[MarcarLido] Nó {} é SEGUIDOR. Encaminhando requisição para o coordenador.", selfServerId);
            Optional<String> coordinatorIdOpt = electionService.getCoordinatorId();
            if (coordinatorIdOpt.isEmpty()) {
                log.error("[MarcarLido] ID do Coordenador desconhecido via ElectionService. Não é possível encaminhar.");
                throw new RuntimeException("Coordenador não disponível para processar a requisição de marcar notificações como lidas.");
            }
            String coordinatorId = coordinatorIdOpt.get();
            Optional<Server> coordinatorServerOpt = electionService.getCoordinatorServerDetails(coordinatorId);

            if (coordinatorServerOpt.isPresent()) {
                Server coordinator = coordinatorServerOpt.get();
                try {
                    log.info("[MarcarLido] Encaminhando para coordenador {} ({}:{})", coordinator.getId(), coordinator.getHost(), coordinator.getPort());
                    grpcClientService.forwardMarkNotificationsRead(coordinator.getHost(), coordinator.getPort(), userId, notificationIds);
                    log.info("[MarcarLido] Requisição encaminhada com sucesso para o coordenador {}", coordinator.getId());
                } catch (Exception e) {
                    log.error("[MarcarLido] Falha ao encaminhar requisição para o coordenador {}: {}", coordinator.getId(), e.getMessage(), e);
                    throw new RuntimeException("Falha ao encaminhar requisição para o coordenador.", e);
                }
            } else {
                log.error("[MarcarLido] Detalhes do coordenador {} não encontrados via ElectionService. Não é possível encaminhar a requisição.", coordinatorId);
                throw new RuntimeException("Detalhes do coordenador não encontrados, não é possível processar a requisição.");
            }
        }
    }

    /**
     * (Coordenador) Processa a marcação como lido localmente e inicia a replicação.
     */
    private void processAndReplicateMarkAsRead(String userId, List<String> notificationIds) {
        log.info("[MarcarLido-Coord] Coordenador {} processando marcação como lido para o usuário {}.", selfServerId, userId);

        int updatedCount = 0;
        try {
            if (!userRepository.existsById(userId)) {
                log.warn("[MarcarLido-Coord] Tentativa de marcar notificações como lidas para usuário inexistente ID: {}. Ignorando atualização e replicação.", userId);
                return;
            }
            updatedCount = notificationRepository.markAsRead(userId, notificationIds);
            log.info("[MarcarLido-Coord] {} notificações marcadas como lidas localmente para o usuário ID: {}", updatedCount, userId);
        } catch (Exception e) {
            log.error("[MarcarLido-Coord] Erro ao marcar notificações como lidas localmente para o usuário ID {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Falha ao marcar notificações como lidas localmente.", e);
        }

        if (updatedCount == 0) {
             log.warn("[MarcarLido-Coord] Nenhuma notificação foi atualizada localmente para o usuário {}. Ignorando replicação.", userId);
             return;
        }

        List<ServiceInstance> activePeerInstances = discoveryClient.getInstances(selfServiceName)
                .stream()
                .filter(instance -> {
                    String peerServerId = getServerIdFromInstance(instance);
                    return !peerServerId.equals(selfServerId) && !peerServerId.startsWith("unknown") && getGrpcPortFromInstance(instance) != -1;
                })
                .collect(Collectors.toList());

        if (activePeerInstances.isEmpty()) {
            log.info("[MarcarLido-Coord] Nenhum outro peer ativo encontrado via Consul. Ignorando replicação.");
            return;
        }

        log.info("[MarcarLido-Coord] Iniciando replicação para {} peers ativos via Consul.", activePeerInstances.size());
        activePeerInstances.forEach(peerInstance -> {
            String peerHost = peerInstance.getHost();
            int peerPort = getGrpcPortFromInstance(peerInstance);
            String peerServerId = getServerIdFromInstance(peerInstance);
            try {
                log.debug("[MarcarLido-Coord] Replicando marcação como lido para o usuário {} para o peer {} ({}:{})", userId, peerServerId, peerHost, peerPort);
                grpcClientService.replicateMarkNotificationsRead(peerHost, peerPort, userId, notificationIds);
                 log.debug("[MarcarLido-Coord] Replicação iniciada com sucesso para o peer {}", peerServerId);
            } catch (Exception e) {
                log.error("[MarcarLido-Coord] Erro ao replicar marcação como lido para o usuário {} para o peer {} ({}:{}): {}", userId, peerServerId, peerHost, peerPort, e.getMessage(), e);
            }
        });
        log.info("[MarcarLido-Coord] Finalizada a iniciação do processo de replicação.");
    }

     /**
     * (Seguidor) Processa a marcação como lido recebida via replicação.
     */
    @Transactional // Each replication call should be in its own transaction
    public void markReplicatedNotificationsAsRead(String userId, List<String> notificationIds) {
         log.info("[MarcarLido-Replica] Seguidor {} recebeu requisição de replicação para marcar notificações como lidas para o usuário {}.", selfServerId, userId);

         if (notificationIds == null || notificationIds.isEmpty()) {
            log.warn("[MarcarLido-Replica] Lista de IDs de notificação vazia recebida para o usuário {}. Ignorando.", userId);
            return;
        }

         try {
            int updatedCount = notificationRepository.markAsRead(userId, notificationIds);
            log.info("[MarcarLido-Replica] Replicação processada com sucesso, {} notificações marcadas como lidas para o usuário ID: {}", updatedCount, userId);
        } catch (Exception e) {
            log.error("[MarcarLido-Replica] Erro ao processar replicação para o usuário ID {}: {}", userId, e.getMessage(), e);
        }
    }

    private NotificationDTO convertToDto(Notification notification) {
        return new NotificationDTO(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getRelatedEntityId(),
                notification.getCreatedAt(),
                notification.isRead()
        );
    }
} 