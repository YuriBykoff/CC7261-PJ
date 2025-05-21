package com.example.projeto_sd.repository;

import com.example.projeto_sd.model.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, String> {

    /**
     * Retorna uma página de seguidores de um usuário.
     * @param userId ID do usuário seguido
     * @param pageable informações de paginação
     * @return página de relações Follow
     */
    Page<Follow> findByFollowedId(String userId, Pageable pageable);

    /**
     * Retorna uma página de usuários que um usuário está seguindo.
     * @param userId ID do usuário seguidor
     * @param pageable informações de paginação
     * @return página de relações Follow
     */
    Page<Follow> findByFollowerId(String userId, Pageable pageable);

    /**
     * Verifica se existe uma relação de seguir entre dois usuários.
     * @param followerId ID do seguidor
     * @param followedId ID do seguido
     * @return true se existe relação
     */
    boolean existsByFollowerIdAndFollowedId(String followerId, String followedId);

    /**
     * Busca uma relação específica de seguir.
     * @param followerId ID do seguidor
     * @param followedId ID do seguido
     * @return relação Follow, se existir
     */
    Optional<Follow> findByFollowerIdAndFollowedId(String followerId, String followedId);

    /**
     * Remove uma relação de seguir entre dois usuários.
     * @param followerId ID do seguidor
     * @param followedId ID do seguido
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Follow f WHERE f.follower.id = :followerId AND f.followed.id = :followedId")
    void deleteByFollowerIdAndFollowedId(String followerId, String followedId);

    /**
     * Conta quantos seguidores um usuário possui.
     * @param userId ID do seguido
     * @return número de seguidores
     */
    long countByFollowedId(String userId);

    /**
     * Conta quantos usuários um usuário está seguindo.
     * @param userId ID do seguidor
     * @return número de seguidos
     */
    long countByFollowerId(String userId);

    /**
     * Retorna os IDs dos usuários que um usuário está seguindo.
     * @param followerId ID do seguidor
     * @return lista de IDs de seguidos
     */
    @Query("SELECT f.followed.id FROM Follow f WHERE f.follower.id = :followerId")
    List<String> findFollowedIdsByFollowerId(String followerId);

} 