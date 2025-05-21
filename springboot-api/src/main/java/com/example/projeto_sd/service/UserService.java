package com.example.projeto_sd.service;

import com.example.projeto_sd.dto.user.UserResponseDTO;
import com.example.projeto_sd.model.User;
import com.example.projeto_sd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import com.example.projeto_sd.grpc.ServerCommsProto.UserInfo;
import com.example.projeto_sd.grpc.ServerCommsProto.ReplicationResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final GrpcClientService grpcClientService;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${spring.application.name}")
    private String selfServiceName;

    @Value("${server.id}")
    private String selfServerId;

    @Transactional
    public User createUser(String name) {
        log.info("Tentando criar usuário com nome: {}", name);

        User newUser = new User();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setName(name);

        User savedUser = userRepository.save(newUser);
        log.info("Usuário criado localmente com sucesso. ID: {}, Nome: {}", savedUser.getId(), savedUser.getName());


        return savedUser;
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        log.info("Buscando todos os usuários");
        List<User> users = userRepository.findAll();
        List<UserResponseDTO> userSummaries = users.stream()
                .map(user -> new UserResponseDTO(user.getId(), user.getName()))
                .collect(Collectors.toList());
        log.info("Encontrados {} usuários.", userSummaries.size());
        return userSummaries;
    }


    public void replicateUserCreation(User user) {
        log.info("Coordenador ({}) iniciando replicação para o usuário ID: {}", selfServerId, user.getId());
        List<ServiceInstance> activePeers = getActivePeerInstances();

        if (activePeers.isEmpty()) {
            log.warn("Nenhum peer ativo encontrado pelo DiscoveryClient para replicar a criação do usuário.");
            return;
        }

        UserInfo userInfoProto = UserInfo.newBuilder()
                .setId(user.getId())
                .setName(user.getName())
                .build();

        log.info("Replicando criação do usuário {} para {} peers ativos.", user.getId(), activePeers.size());

        for (ServiceInstance peerInstance : activePeers) {
            String peerHost = peerInstance.getHost();
            int peerPort = getGrpcPortFromInstance(peerInstance);
            String peerId = getServerIdFromInstance(peerInstance);

            log.debug("Replicando usuário {} para peer {} em {}:{}", user.getId(), peerId, peerHost, peerPort);
            try {
                ReplicationResponse response = grpcClientService.replicateUserCreationToPeer(peerHost, peerPort, userInfoProto);
                if (response == null || !response.getSuccess()) {
                    log.warn("Falha ao replicar usuário {} para peer {}. Mensagem: {}",
                            user.getId(), peerId,
                            response != null ? response.getMessage() : "Sem resposta");

                } else {
                    log.info("Usuário {} replicado com sucesso para peer {}", user.getId(), peerId);
                }
            } catch (Exception e) {
                log.error("Erro ao replicar usuário {} para peer {}: {}",
                        user.getId(), peerId, e.getMessage());
            }
        }
        log.info("Processo de replicação finalizado para o usuário ID: {}", user.getId());
    }


    private List<ServiceInstance> getActivePeerInstances() {
        List<ServiceInstance> instances = discoveryClient.getInstances(selfServiceName);
        log.debug("{} instâncias encontradas via DiscoveryClient para o serviço {}", instances.size(), selfServiceName);
        return instances.stream()
                .filter(instance -> !isSelf(instance))
                .collect(Collectors.toList());
    }

    private boolean isSelf(ServiceInstance instance) {
        String instanceServerId = instance.getMetadata().get("server.id");
        return selfServerId.equals(instanceServerId);
    }

     private int getGrpcPortFromInstance(ServiceInstance instance) {
        try {
            return Integer.parseInt(instance.getMetadata().getOrDefault("grpcPort", "9090"));
        } catch (NumberFormatException e) {
            log.warn("Não foi possível converter o grpcPort da instância {}. Usando padrão 9090.", instance.getInstanceId(), e);
            return 9090;
        }
    }

     private String getServerIdFromInstance(ServiceInstance instance) {
        return instance.getMetadata().getOrDefault("server.id", "unknown-" + instance.getInstanceId());
    }

}