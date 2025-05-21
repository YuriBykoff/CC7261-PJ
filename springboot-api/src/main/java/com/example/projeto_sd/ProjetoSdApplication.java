package com.example.projeto_sd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ProjetoSdApplication {

	public static void main(String[] args) {
		try {
			log.info("Iniciando aplicação...");
			SpringApplication.run(ProjetoSdApplication.class, args);
			log.info("Aplicação iniciada com sucesso!");
		} catch (Exception e) {
			log.error("Erro ao iniciar a aplicação: {}", e.getMessage(), e);
			System.exit(1);
		}
	}

}
