package com.example.projeto_sd.service;
import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ServerRepository serverRepository;
    private final ElectionService electionService;
    private final ClockSyncService clockSyncService;

    @Value("${server.id}")
    private String selfServerId;
    @Value("${grpc.server.port}")
    private int selfGrpcPort;
    @Value("${server.host}")
    private String selfHost;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Aplicação pronta. Iniciando inicialização para o servidor: {}", selfServerId);
        registerSelfLocally();

        clockSyncService.initializeOffset();

        log.info("Aguardando um momento para estabilizar registros de peers no Consul antes da eleição...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Espera de inicialização interrompida", e);
        }
        log.info("Espera finalizada. Prosseguindo com verificação/eleição de coordenador.");

        log.info("Verificando coordenador e potencialmente iniciando eleição...");
        if (electionService.getCurrentCoordinatorId() == null) {
             Optional<Server> coordinator = serverRepository.findByIsCoordinatorTrue();
             if(coordinator.isEmpty()){
                  log.info("Nenhum coordenador encontrado no BD. Iniciando eleição.");
                  electionService.startElection();
             } else {
                 log.info("Coordenador {} encontrado no BD.", coordinator.get().getId());
                 electionService.setCurrentCoordinatorId(coordinator.get().getId());
             }
        } else {
             log.info("Coordenador {} já conhecido.", electionService.getCurrentCoordinatorId());
        }

        log.info("Inicialização completa para o servidor: {}", selfServerId);
    }

    private void registerSelfLocally() {
        log.info("Registrando/atualizando próprio servidor ({}) no banco de dados local... Host: {}, Porta: {}", selfServerId, selfHost, selfGrpcPort);
        Server self = serverRepository.findById(selfServerId)
                .orElse(new Server());

        self.setId(selfServerId);
        self.setHost(selfHost);
        self.setPort(selfGrpcPort);
        self.setActive(true);
        self.setServerName(selfServerId);


        try {
            serverRepository.save(self);
            log.info("Próprio servidor ({}) registrado/atualizado com sucesso no banco de dados.", selfServerId);
        } catch (Exception e) {
            log.error("Falha ao registrar próprio servidor ({}) localmente: {}", selfServerId, e.getMessage(), e);
        }
    }

    public String getServerId() {
        return selfServerId;
    }

    public boolean isCoordinator() {
        return electionService.isCurrentNodeCoordinator();
    }
} 