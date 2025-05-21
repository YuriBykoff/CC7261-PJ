package com.example.projeto_sd.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementação de um relógio lógico de Lamport.
 * Mantém um contador que é incrementado a cada evento local
 * e sincronizado quando eventos externos são recebidos.
 */
@Component
@Slf4j
public class LogicalClock {

    private final AtomicInteger clock = new AtomicInteger(0);

    /**
     * Incrementa o relógio lógico para um novo evento local.
     * @return O novo valor do relógio.
     */
    public int increment() {
        int newValue = clock.incrementAndGet();
        log.debug("Relógio lógico incrementado para: {}", newValue);
        return newValue;
    }

    /**
     * Sincroniza o relógio local com o valor de um relógio externo.
     * Seguindo o algoritmo de Lamport, definimos o relógio local como
     * max(local, externo) + 1.
     * 
     * @param receivedClock O valor do relógio recebido de outro processo.
     * @return O novo valor do relógio local após a sincronização.
     */
    public int synchronizeWith(int receivedClock) {
        int currentValue;
        int newValue;
        
        do {
            currentValue = clock.get();
            newValue = Math.max(currentValue, receivedClock) + 1;
        } while (!clock.compareAndSet(currentValue, newValue));
        
        log.debug("Relógio lógico sincronizado com valor externo {}, novo valor: {}", receivedClock, newValue);
        return newValue;
    }

    /**
     * Obtém o valor atual do relógio lógico.
     * @return O valor atual.
     */
    public int getValue() {
        return clock.get();
    }
} 