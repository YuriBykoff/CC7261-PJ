package com.example.projeto_sd.service;

import com.example.projeto_sd.model.Server;
import com.example.projeto_sd.model.ServerClock;
import com.example.projeto_sd.repository.ServerClockRepository;
import com.example.projeto_sd.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.HashMap;

import com.example.projeto_sd.grpc.ServerCommsProto.GetTimeResponse;


@Service
@RequiredArgsConstructor
@Slf4j
public class ClockSyncService {

    private final ServerRepository serverRepository;
    private final ServerClockRepository serverClockRepository;
    private final GrpcClientService grpcClientService;
    private final ElectionService electionService;

    @Value("${server.id}")
    private String selfServerId;

    @Value("${clocksync.interval.ms:60000}")
    private long clockSyncIntervalMs;

    private volatile long timeOffsetMillis = 0;


    public void initializeOffset() {
        loadOffsetFromDb();
    }

    /**
     * Tarefa agendada para iniciar a sincronização (executada apenas pelo coordenador).
     */
    @Scheduled(fixedDelayString = "${clocksync.interval.ms:60000}", initialDelayString = "${clocksync.initial.delay.ms:30000}")
    public void initiateClockSync() {
        if (electionService.isCoordinator()) {
            log.info("Coordenador {} iniciando sincronização de relógio (Algoritmo de Berkeley).", selfServerId);
            synchronizeClocks();
        }
    }

    /**
     * Método principal para sincronização de relógios via Berkeley.
     */
    @Transactional
    private void synchronizeClocks() {
        if (!electionService.isCoordinator()) {
            log.debug("Não é o coordenador. Pulando sincronização de relógio.");
            return;
        }

        log.info("[Berkeley] Coordenador iniciando processo de sincronização de relógio");

        List<Server> activeServers = serverRepository.findAllActiveServersExcludingSelf(selfServerId);
        if (activeServers.isEmpty()) {
            log.info("[Berkeley] Nenhum peer ativo encontrado. Pulando sincronização.");
            return;
        }

        Map<String, Long> peerTimes = new HashMap<>();

        for (Server server : activeServers) {
            try {
                log.debug("[Berkeley] Solicitando tempo do servidor {}", server.getId());
                GetTimeResponse response = grpcClientService.requestTimeFromPeer(server.getHost(), server.getPort());
                if (response != null) {
                    peerTimes.put(server.getId(), response.getCurrentTimeMillis());
                    log.debug("[Berkeley] Servidor {} reportou tempo: {}ms", server.getId(), response.getCurrentTimeMillis());
                }
            } catch (Exception e) {
                log.error("[Berkeley] Erro ao obter tempo do servidor {}: {}", server.getId(), e.getMessage());
            }
        }

        if (peerTimes.isEmpty()) {
            log.warn("[Berkeley] Não foi possível coletar tempo de nenhum peer. Abortando sincronização.");
            return;
        }

        Map<String, Long> adjustments = calculateBerkeleyAdjustments(peerTimes);

        for (Server server : activeServers) {
            String serverId = server.getId();
            Long adjustment = adjustments.get(serverId);

            if (adjustment == null) {
                log.warn("[Berkeley] Nenhum ajuste calculado para o servidor {}. Pulando.", serverId);
                continue;
            }

            try {
                log.info("[Berkeley] Enviando ajuste de {}ms para o servidor {}", adjustment, serverId);
                grpcClientService.sendTimeAdjustmentToPeer(server.getHost(), server.getPort(), adjustment);
            } catch (Exception e) {
                log.error("[Berkeley] Erro ao enviar ajuste para o servidor {}: {}", serverId, e.getMessage());
            }
        }

        log.info("[Berkeley] Processo de sincronização de relógio concluído");
    }

    /**
     * (Seguidor) Aplica um ajuste de tempo recebido do coordenador.
     */
    @Transactional
    public void applyReceivedAdjustment(long adjustment) {
        if (electionService.isCoordinator()) {
            log.warn("[Berkeley] Coordenador recebeu requisição de ajuste de tempo. Ignorando.");
            return;
        }

        long newOffset = timeOffsetMillis + adjustment;
        log.info("[Berkeley] Aplicando ajuste de tempo recebido: {}ms. Novo offset: {}ms",
                 adjustment, newOffset);

        timeOffsetMillis = newOffset;

        applyAndPersistOffset(newOffset);

        simulateRandomClockDrift();
    }

    /**
     * Aplica um novo valor de offset localmente e persiste no ServerClock.
     */
    private void applyAndPersistOffset(long newOffsetMillis) {
        log.debug("Atualizando offset de tempo local de {}ms para {}ms", this.timeOffsetMillis, newOffsetMillis);
        this.timeOffsetMillis = newOffsetMillis;
        ServerClock serverClock = serverClockRepository.findByServerId(selfServerId)
                .orElseGet(() -> {
                    ServerClock newClock = new ServerClock();
                    newClock.setId(java.util.UUID.randomUUID().toString());
                    serverRepository.findById(selfServerId).ifPresent(newClock::setServer);
                    return newClock;
                });
        serverClock.setOffsetMillis((int) newOffsetMillis);
        serverClockRepository.save(serverClock);
        log.info("Offset de tempo local atualizado para {}ms e persistido.", newOffsetMillis);
    }

    /**
     * Carrega o offset do banco de dados ao iniciar.
     */
    private void loadOffsetFromDb() {
        Optional<ServerClock> serverClockOpt = serverClockRepository.findByServerId(selfServerId);
        if (serverClockOpt.isPresent()) {
            this.timeOffsetMillis = serverClockOpt.get().getOffsetMillis();
            log.info("Offset de tempo persistido carregado para o servidor {}: {}ms", selfServerId, this.timeOffsetMillis);
        } else {
            log.info("Nenhum offset de tempo persistido encontrado para o servidor {}. Inicializando com 0ms.", selfServerId);
            this.timeOffsetMillis = 0;
            applyAndPersistOffset(0);
        }
    }

    /**
     * Retorna o tempo atual do sistema corrigido pelo offset de sincronização.
     * @return Tempo corrigido em milissegundos (Unix epoch).
     */
    public long getCurrentCorrectedTimeMillis() {
        return Instant.now().toEpochMilli() + this.timeOffsetMillis;
    }

    /**
     * Retorna o tempo atual do sistema corrigido pelo offset de sincronização como LocalDateTime.
     * @return Tempo corrigido como LocalDateTime na zona padrão do sistema.
     */
    public LocalDateTime getCurrentCorrectedLocalDateTime() {
        long correctedMillis = getCurrentCorrectedTimeMillis();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(correctedMillis), java.time.ZoneId.systemDefault());
    }

    /**
     * Simula um clock drift aleatório após uma sincronização.
     */
    private void simulateRandomClockDrift() {
        int randomDriftMs = ThreadLocalRandom.current().nextInt(-1000, 1001);

        long newOffset = timeOffsetMillis + randomDriftMs;

        log.info("[Clock Drift] Simulando drift aleatório de relógio de {}ms. Novo offset: {}ms",
                randomDriftMs, newOffset);

        timeOffsetMillis = newOffset;

        try {
            applyAndPersistOffset(newOffset);
        } catch (Exception e) {
            log.error("[Clock Drift] Erro ao persistir ajuste de drift: {}", e.getMessage(), e);
        }
    }

    /**
     * (Coordenador) Calcula o ajuste de tempo pelo algoritmo de Berkeley.
     */
    public Map<String, Long> calculateBerkeleyAdjustments(Map<String, Long> peerTimes) {
        log.info("[Berkeley] Coordenador calculando ajustes de tempo para {} peers", peerTimes.size());

        long coordinatorTime = System.currentTimeMillis() + timeOffsetMillis;
        peerTimes.put(selfServerId, coordinatorTime);

        long sum = peerTimes.values().stream().mapToLong(Long::longValue).sum();
        long averageTime = sum / peerTimes.size();

        log.info("[Berkeley] Tempo médio calculado: {}ms", averageTime);

        Map<String, Long> adjustments = new HashMap<>();
        for (Map.Entry<String, Long> entry : peerTimes.entrySet()) {
            String serverId = entry.getKey();
            long serverTime = entry.getValue();
            long adjustment = averageTime - serverTime;

            adjustments.put(serverId, adjustment);
            log.info("[Berkeley] Tempo do servidor {}: {}ms, ajuste: {}ms",
                    serverId, serverTime, adjustment);
        }

        long selfAdjustment = adjustments.getOrDefault(selfServerId, 0L);
        if (selfAdjustment != 0) {
            log.info("[Berkeley] Aplicando auto-ajuste de {}ms ao coordenador", selfAdjustment);
            applyAndPersistOffset(this.timeOffsetMillis + selfAdjustment);
        } else {
            log.info("[Berkeley] Coordenador já está sincronizado (ajuste 0ms).");
        }

        simulateRandomClockDrift();

        adjustments.remove(selfServerId);

        return adjustments;
    }
} 