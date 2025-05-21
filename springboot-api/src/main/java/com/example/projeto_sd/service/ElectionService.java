package com.example.projeto_sd.service;

import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionService {

    @Autowired
    private DiscoveryClient discoveryClient;

    private final ServerRepository serverRepository;
    private final GrpcClientService grpcClientService;

    @Value("${server.id}")
    private String selfServerId;

    @Value("${spring.application.name}")
    private String selfServiceName;

    @Setter
    private volatile String currentCoordinatorId = null;
    private volatile boolean electionInProgress = false;

    @Transactional
    public synchronized void startElection() {
        if (electionInProgress) {
            log.info("Eleição já em andamento, pulando requisição.");
            return;
        }
        electionInProgress = true;
        log.info("Iniciando processo de eleição para o servidor {} via DiscoveryClient.", selfServerId);

        try {
            List<ServiceInstance> activePeers = getActivePeerInstances();

            List<ServiceInstance> higherIdPeers = activePeers.stream()
                    .filter(instance -> {
                        String peerServerId = getServerIdFromInstance(instance);
                        if ("unknown".equals(peerServerId)) {
                            log.warn("Não foi possível determinar o ID do servidor para a instância {}. Excluindo da verificação de eleição.", instance.getInstanceId());
                            return false;
                        }
                        return peerServerId.compareTo(selfServerId) > 0;
                    })
                    .collect(Collectors.toList());

            if (higherIdPeers.isEmpty()) {
                log.info("Nenhum peer ativo com ID maior encontrado via DiscoveryClient. Servidor {} se declara coordenador.", selfServerId);
                becomeCoordinator();
            } else {
                log.info("Encontrados {} servidor(es) ativo(s) com ID maior via DiscoveryClient. Aguardando anúncio do coordenador.", higherIdPeers.size());
            }
        } catch (Exception e) {
           log.error("Erro durante o processo de eleição usando DiscoveryClient: {}", e.getMessage(), e);
        } finally {
            electionInProgress = false;
        }
    }

    @Transactional
    private void becomeCoordinator() {
        try {
            List<Server> allServersInDb = serverRepository.findAll();
            for (Server server : allServersInDb) {
                if (!server.getId().equals(selfServerId) && server.isCoordinator()) {
                    log.info("becomeCoordinator: Rebaixando {} no banco local antes de {} se tornar o novo coordenador.", server.getId(), selfServerId);
                    server.setCoordinator(false);
                    serverRepository.save(server);
                }
            }

            Optional<Server> selfOpt = serverRepository.findById(selfServerId);
            if (selfOpt.isPresent()) {
                Server self = selfOpt.get();
                self.setCoordinator(true);
                self.setActive(true);
                serverRepository.save(self);
                this.currentCoordinatorId = selfServerId;
                log.info("Servidor {} marcado com sucesso como coordenador no banco de dados.", selfServerId);
                announceToOthers(selfServerId);
            } else {
                log.error("Não foi possível encontrar o próprio servidor ({}) no banco de dados para marcar como coordenador!", selfServerId);
            }
        } catch (Exception e) {
            log.error("Falha ao se tornar coordenador: {}", e.getMessage(), e);
            this.currentCoordinatorId = null;
        }
    }

    private void announceToOthers(String coordinatorId) {
        List<ServiceInstance> activePeers = getActivePeerInstances();
        log.info("Anunciando coordenador {} para {} outros servidores ativos encontrados via DiscoveryClient.", coordinatorId, activePeers.size());

        for (ServiceInstance targetInstance : activePeers) {
            String targetHost = targetInstance.getHost();
            int targetPort = getGrpcPortFromInstance(targetInstance);
            String targetServerId = getServerIdFromInstance(targetInstance);

            if (targetPort != -1 && !"unknown".equals(targetServerId)) {
                if (!targetServerId.equals(selfServerId)) {
                    log.debug("Anunciando coordenador {} para o servidor {} em {}:{}", coordinatorId, targetServerId, targetHost, targetPort);
                    try {
                        grpcClientService.announceCoordinatorToPeer(targetHost, targetPort, coordinatorId);
                    } catch (Exception e) {
                        log.warn("Falha ao anunciar coordenador para o servidor {} (InstanceId: {} em {}:{}): {}", 
                                 targetServerId, targetInstance.getInstanceId(), targetHost, targetPort, e.getMessage());
                    }
                } else {
                    log.debug("Pulando anúncio para si mesmo ({})", selfServerId);
                }
            } else {
                 log.warn("Pulando anúncio para instância {} pois não foi possível obter ID do servidor ou porta gRPC dos metadados.", targetInstance.getInstanceId());
            }
        }
    }
    
    @Transactional
    public synchronized void processCoordinatorAnnouncement(String announcedCoordinatorId) {
        log.info("Processando anúncio de coordenador: {} é o novo coordenador.", announcedCoordinatorId);

        if (announcedCoordinatorId.equals(this.currentCoordinatorId)) {
            log.debug("Anúncio para o coordenador já conhecido ({}), nenhuma ação necessária.", announcedCoordinatorId);
            return;
        }

        Optional<Server> newCoordinatorOpt = serverRepository.findById(announcedCoordinatorId);
        Server newCoordinatorServerEntity;

        if (newCoordinatorOpt.isEmpty()) {
            log.warn("Coordenador anunciado {} não encontrado no banco de dados local. Criando nova entrada...", announcedCoordinatorId);
            newCoordinatorServerEntity = new Server();
            newCoordinatorServerEntity.setId(announcedCoordinatorId);
            newCoordinatorServerEntity.setServerName(announcedCoordinatorId);
            Optional<ServiceInstance> coordinatorInstanceOpt = getActivePeerInstances().stream()
                    .filter(instance -> announcedCoordinatorId.equals(getServerIdFromInstance(instance)))
                    .findFirst();

            if (coordinatorInstanceOpt.isPresent()) {
                ServiceInstance coordinatorInstance = coordinatorInstanceOpt.get();
                newCoordinatorServerEntity.setHost(coordinatorInstance.getHost());
                int grpcPort = getGrpcPortFromInstance(coordinatorInstance);
                if (grpcPort != -1) {
                    newCoordinatorServerEntity.setPort(grpcPort);
                } else {
                    log.warn("Não foi possível obter a porta gRPC para o novo coordenador {} a partir dos metadados da instância {}. A porta não será definida.", announcedCoordinatorId, coordinatorInstance.getInstanceId());
                }
                log.info("Detalhes para o novo coordenador {} (Host: {}, Port: {}) obtidos via DiscoveryClient e serão usados para a nova entrada no BD.", 
                         announcedCoordinatorId, newCoordinatorServerEntity.getHost(), newCoordinatorServerEntity.getPort());
            } else {
                log.warn("Não foi possível encontrar a instância do novo coordenador {} via DiscoveryClient para obter host/porta. A entrada será criada com informações limitadas.", announcedCoordinatorId);
            }
        } else {
            newCoordinatorServerEntity = newCoordinatorOpt.get();
        }
        
        if (!newCoordinatorServerEntity.isActive()) {
            log.info("Coordenador anunciado {} estava marcado como inativo. Ativando.", announcedCoordinatorId);
            newCoordinatorServerEntity.setActive(true);
        }


        if (selfServerId.equals(this.currentCoordinatorId)) {
            log.info("Recebido anúncio para {}, mas eu ({}) pensava ser o coordenador. Rebaixando-me.", announcedCoordinatorId, selfServerId);
            demoteSelf(); 
        }

        List<Server> allServersInDb = serverRepository.findAll();
        for (Server server : allServersInDb) {
            if (!server.getId().equals(announcedCoordinatorId) && server.isCoordinator()) {
                log.info("processCoordinatorAnnouncement: Rebaixando {} no banco local pois {} é o novo coordenador.", server.getId(), announcedCoordinatorId);
                server.setCoordinator(false);
                serverRepository.save(server);
            }
        }

        if (!newCoordinatorServerEntity.isCoordinator()) { 
            newCoordinatorServerEntity.setCoordinator(true);
        }
        serverRepository.save(newCoordinatorServerEntity);
        
        this.currentCoordinatorId = announcedCoordinatorId;
        log.info("Estado local atualizado com sucesso: {} é o coordenador.", announcedCoordinatorId);

        if (electionInProgress) {
            log.info("Coordenador {} anunciado, parando qualquer eleição em andamento.", announcedCoordinatorId);
            electionInProgress = false;
        }
    }

    @Transactional
    private void demoteSelf() {
        serverRepository.findById(selfServerId).ifPresent(self -> {
            if (self.isCoordinator()) {
                self.setCoordinator(false);
                serverRepository.save(self);
                log.info("Servidor {} rebaixou a si mesmo com sucesso.", selfServerId);
            }
        });
        if (selfServerId.equals(this.currentCoordinatorId)) {
             this.currentCoordinatorId = null;
        }
    }

    public String getCurrentCoordinatorId() {
        return currentCoordinatorId;
    }

    public boolean isCurrentNodeCoordinator() {
        return selfServerId.equals(this.currentCoordinatorId);
    }

    public boolean isCoordinator() {
        return selfServerId.equals(currentCoordinatorId);
    }

    public Optional<String> getCoordinatorId() {
        return Optional.ofNullable(this.currentCoordinatorId);
    }

    public Optional<Server> getCoordinatorServerDetails(String coordinatorServerId) {
        if (coordinatorServerId == null) {
            return Optional.empty();
        }
        return serverRepository.findById(coordinatorServerId);
    }

    private List<ServiceInstance> getActivePeerInstances() {
        List<ServiceInstance> instances = discoveryClient.getInstances(selfServiceName);
        log.debug("Encontradas {} instâncias via DiscoveryClient para o serviço {}", instances.size(), selfServiceName);
        return instances.stream()
                .collect(Collectors.toList());
    }

    private int getGrpcPortFromInstance(ServiceInstance instance) {
        String grpcPortStr = instance.getMetadata().get("gRPC_port");
        if (grpcPortStr == null) {
             log.warn("Metadado 'gRPC_port' não encontrado para a instância {}.", instance.getInstanceId());
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
             return "unknown";
        }
        return serverId.trim();
    }
} 