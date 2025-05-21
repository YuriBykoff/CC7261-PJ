package com.example.projeto_sd.service;

import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.repository.ServerRepository;
import com.example.projeto_sd.grpc.ServerCommsProto.HeartbeatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private final ServerRepository serverRepository;
    private final GrpcClientService grpcClientService;
    private final ElectionService electionService;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${spring.application.name}")
    private String selfServiceName;

    @Value("${server.id}")
    private String selfServerId;

    @Value("${heartbeat.interval.ms:10000}")
    private long heartbeatIntervalMs;

    @Value("${heartbeat.timeout.multiplier:3}")
    private int timeoutMultiplier;


    /**
     * Tarefa agendada para enviar heartbeats (se não for coordenador)
     * ou verificar a atividade de outros servidores (se for coordenador).
     */
    @Scheduled(fixedDelayString = "${heartbeat.interval.ms:10000}", initialDelayString = "${heartbeat.initial.delay.ms:15000}") // Delay inicial maior para permitir setup
    @Transactional
    public void performHeartbeatCheck() {
        if (electionService.isCoordinator()) {
            log.trace("Coordenador {}: Pulando verificação de atividade de cliente local (gerenciado pelo Consul).", selfServerId);
        } else {
            sendHeartbeatToCoordinator();
        }
    }

    /**
     * Lógica para enviar heartbeat ao coordenador.
     */
    private void sendHeartbeatToCoordinator() {
        String coordinatorId = electionService.getCurrentCoordinatorId();
        if (coordinatorId == null) {
            log.warn("Nenhum coordenador conhecido. Iniciando eleição a partir do serviço de heartbeat.");
            electionService.startElection();
            return;
        }

        if (coordinatorId.equals(selfServerId)) {
            log.error("Nó não-coordenador {} acredita ser o coordenador {}. Isso não deveria acontecer.", selfServerId, coordinatorId);
            return;
        }

        Optional<ServiceInstance> coordinatorInstanceOpt = discoveryClient.getInstances(selfServiceName)
            .stream()
            .filter(instance -> coordinatorId.equals(getServerIdFromInstance(instance)))
            .findFirst();

        if (coordinatorInstanceOpt.isPresent()) {
            ServiceInstance coordinatorInstance = coordinatorInstanceOpt.get();
            String host = coordinatorInstance.getHost();
            int port = getGrpcPortFromInstance(coordinatorInstance);
            log.trace("Enviando heartbeat de {} para o coordenador {} encontrado via DiscoveryClient em {}:{}", selfServerId, coordinatorId, host, port);
            HeartbeatResponse response = grpcClientService.sendHeartbeatToPeer(host, port, selfServerId);
            if (response == null || !response.getAcknowledged()) {
                log.warn("Heartbeat para o coordenador {} em {}:{} não foi reconhecido ou falhou. Iniciando eleição.", coordinatorId, host, port);
                electionService.startElection();
            } else {
                log.trace("Heartbeat reconhecido pelo coordenador {}", coordinatorId);
            }
        } else {
            log.warn("Coordenador {} não encontrado ou não registrado/saudável no Consul. Iniciando eleição.", coordinatorId);
            electionService.setCurrentCoordinatorId(null);
            electionService.startElection();
        }
    }

    /**
     * Processa um heartbeat recebido (chamado pelo ServerServiceImpl).
     * Registra o recebimento do heartbeat se o nó atual for o coordenador.
     *
     * @param senderId O ID do servidor que enviou o heartbeat.
     */
    @Transactional
    public void processIncomingHeartbeat(String senderId) {
        if (!electionService.isCoordinator()) {
            log.warn("Nó não-coordenador {} recebeu um heartbeat de {}. Ignorando.", selfServerId, senderId);
            return;
        }

        log.trace("Coordenador {} processando heartbeat de {}", selfServerId, senderId);
        Optional<Server> senderOpt = serverRepository.findById(senderId);
        if (senderOpt.isPresent()) {
            
            
            log.trace("Heartbeat recebido do servidor conhecido {}. (Timestamp DB não mais atualizado)", senderId);
        } else {
            log.warn("Recebido heartbeat de servidor desconhecido ID: {}. Ele deve se registrar primeiro.", senderId);
        }
    }

    private int getGrpcPortFromInstance(ServiceInstance instance) {
        try {
            return Integer.parseInt(instance.getMetadata().getOrDefault("grpcPort", "9090"));
        } catch (NumberFormatException e) {
            log.warn("Não foi possível converter o metadata grpcPort da instância {}. Utilizando padrão 9090.", instance.getInstanceId(), e);
            return 9090;
        }
    }

    private String getServerIdFromInstance(ServiceInstance instance) {
        return instance.getMetadata().getOrDefault("server.id", "desconhecido-" + instance.getInstanceId());
    }
} 