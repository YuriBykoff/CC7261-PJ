package com.example.projeto_sd.service;

import com.example.projeto_sd.dto.user.UserResponseDTO;
import com.example.projeto_sd.exception.UserNotFoundException;
import com.example.projeto_sd.model.Follow;
import com.example.projeto_sd.model.User;
import com.example.projeto_sd.repository.FollowRepository;
import com.example.projeto_sd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 200; // 200 milissegundos

    /**
     * Cria a relação de seguir (APENAS LOCALMENTE).
     * A lógica distribuída será tratada no ServerServiceImpl.
     * Adicionada lógica de retry para UserNotFoundException.
     */
    @Transactional
    public void followUser(String followerId, String followedId) {
        log.info("[EXECUÇÃO LOCAL] Tentando seguir: {} -> {}", followerId, followedId);

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                User follower = userRepository.findById(followerId)
                        .orElseThrow(() -> new UserNotFoundException("Usuário seguidor não encontrado com ID: " + followerId));
                User followed = userRepository.findById(followedId)
                        .orElseThrow(() -> new UserNotFoundException("Usuário seguido não encontrado com ID: " + followedId));

                if (followerId.equals(followedId)) {
                    log.warn("Tentativa de seguir a si mesmo negada para o usuário ID: {}", followerId);
                    throw new IllegalArgumentException("Usuário não pode seguir a si mesmo.");
                }

                if (followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
                    log.info("[EXECUÇÃO LOCAL] Relação de seguir {} -> {} já existe. Pulando salvamento.", followerId, followedId);
                    return;
                }

                Follow newFollow = new Follow();
                newFollow.setId(UUID.randomUUID().toString());
                newFollow.setFollower(follower);
                newFollow.setFollowed(followed);
                followRepository.save(newFollow);
                log.info("[EXECUÇÃO LOCAL] Relação de seguir criada com sucesso na tentativa {}: {} -> {}", attempt + 1, followerId, followedId);
                return;
            } catch (UserNotFoundException e) {
                log.warn("[Tentativa {}/{}] Falha ao seguir {} -> {}: {}. Tentando novamente em {}ms...",
                        attempt + 1, MAX_RETRY_ATTEMPTS, followerId, followedId, e.getMessage(), RETRY_DELAY_MS);
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        log.error("Thread de retry interrompida durante followUser.", ie);
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    log.error("[Última Tentativa {}] Falha ao seguir {} -> {}: {}. Desistindo.",
                            attempt + 1, followerId, followedId, e.getMessage());
                    throw e;
                }
            }
        }
    }

    /**
     * Remove a relação de seguir (APENAS LOCALMENTE).
     * A lógica distribuída será tratada no ServerServiceImpl.
     */
    @Transactional
    public void unfollowUser(String followerId, String followedId) {
        log.info("[EXECUÇÃO LOCAL] Tentando deixar de seguir: {} deixa de seguir {}", followerId, followedId);

        followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId);
        log.info("[EXECUÇÃO LOCAL] Processado com sucesso deixar de seguir (tentativa de delete): {} deixou de seguir {}", followerId, followedId);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getFollowers(String userId) {
        log.info("Buscando seguidores para o usuário ID: {}", userId);
        if (!userRepository.existsById(userId)) {
            log.warn("Não é possível obter seguidores: Usuário não encontrado com ID: {}", userId);
            throw new UserNotFoundException("Usuário não encontrado com ID: " + userId);
        }
        List<Follow> follows = followRepository.findByFollowedId(userId, Pageable.unpaged()).getContent();
        if (follows.isEmpty()) return Collections.emptyList();
        List<UserResponseDTO> followers = follows.stream()
                .map(follow -> new UserResponseDTO(follow.getFollower().getId(), follow.getFollower().getName()))
                .collect(Collectors.toList());
        log.info("Encontrados {} seguidores para o usuário ID: {}", followers.size(), userId);
        return followers;
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getFollowing(String userId) {
        log.info("Buscando usuários seguidos pelo usuário ID: {}", userId);
        if (!userRepository.existsById(userId)) {
            log.warn("Não é possível obter quem segue: Usuário não encontrado com ID: {}", userId);
            throw new UserNotFoundException("Usuário não encontrado com ID: " + userId);
        }
        List<Follow> follows = followRepository.findByFollowerId(userId, Pageable.unpaged()).getContent();
        if (follows.isEmpty()) return Collections.emptyList();
        List<UserResponseDTO> following = follows.stream()
                .map(follow -> new UserResponseDTO(follow.getFollowed().getId(), follow.getFollowed().getName()))
                .collect(Collectors.toList());
        log.info("Usuário ID: {} está seguindo {} usuários.", userId, following.size());
        return following;
    }
} 