package com.example.projeto_sd.repository;

import com.example.projeto_sd.dto.post.PostResponseDto;
import com.example.projeto_sd.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, String> {

    /**
     * Busca posts de um usuário (não deletados), ordenados do mais recente para o mais antigo.
     * @param userId ID do usuário
     * @param pageable informações de paginação
     * @return página de posts
     */
    Page<Post> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Busca todos os posts não deletados, ordenados do mais recente para o mais antigo.
     * @param pageable informações de paginação
     * @return página de posts
     */
    Page<Post> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Busca posts de um usuário, retornando DTOs para otimizar o tráfego.
     * @param userId ID do usuário
     * @param pageable informações de paginação
     * @return página de DTOs de posts
     */
    @Query("""
           SELECT NEW com.example.projeto_sd.dto.post.PostResponseDto(
               p.id, 
               p.user.id, 
               p.user.name, 
               p.content, 
               p.createdAt, 
               p.logicalClock
           )
           FROM Post p 
           WHERE p.user.id = :userId AND p.isDeleted = false 
           ORDER BY p.createdAt DESC
           """)
    Page<PostResponseDto> findPostsByUserIdDto(@Param("userId") String userId, Pageable pageable);

    /**
     * Busca todos os posts não deletados, retornando DTOs para otimizar o tráfego.
     * @param pageable informações de paginação
     * @return página de DTOs de posts
     */
    @Query("""
           SELECT NEW com.example.projeto_sd.dto.post.PostResponseDto(
               p.id, 
               p.user.id, 
               p.user.name, 
               p.content, 
               p.createdAt, 
               p.logicalClock
           )
           FROM Post p 
           WHERE p.isDeleted = false 
           ORDER BY p.createdAt DESC
           """)
    Page<PostResponseDto> findAllPostsDto(Pageable pageable);

} 