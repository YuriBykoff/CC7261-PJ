package com.example.projeto_sd.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Controller para endpoints de teste e diagnóstico do sistema.
 * Fornece informações sobre o servidor atual e seu estado.
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    // Injeta o ID do servidor definido nas variáveis de ambiente do docker-compose
    @Value("${SERVER_ID:unknown_server}")
    private String serverId;

    /**
     * Endpoint de teste que retorna informações básicas do servidor.
     * 
     * @return ResponseEntity contendo um mapa com:
     *         - message: Mensagem indicando qual servidor processou a requisição
     *         - serverId: ID do servidor atual
     *         - timestamp: Momento exato da resposta
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> testEndpoint() {
        String message = "Request handled by server: " + serverId;
        // Retorna um JSON simples
        Map<String, String> response = Map.of(
                "message", message,
                "serverId", serverId,
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(response);
    }
}