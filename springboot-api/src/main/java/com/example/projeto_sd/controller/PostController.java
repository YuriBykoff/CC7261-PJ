package com.example.projeto_sd.controller;

import com.example.projeto_sd.dto.post.CreatePostRequestDto;
import com.example.projeto_sd.dto.post.DeletePostRequestDto;
import com.example.projeto_sd.dto.post.PostResponseDto;
import com.example.projeto_sd.dto.response.ErrorResponse;
import com.example.projeto_sd.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody CreatePostRequestDto requestDto) {
        log.info("Recebida requisição POST /api/posts: userId={}, trecho do conteúdo={}", 
                 requestDto.getUserId(), 
                 requestDto.getContent() != null ? requestDto.getContent().substring(0, Math.min(requestDto.getContent().length(), 50)) + "..." : "null");
        
        if (requestDto.getUserId() == null || requestDto.getUserId().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("ID do usuário é obrigatório."));
        }
        if (requestDto.getContent() == null || requestDto.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Conteúdo não pode estar vazio."));
        }

        try {
            postService.createPost(requestDto);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalArgumentException e) {
            log.warn("Requisição inválida ao criar post: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Erro de estado ao criar post: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro interno do servidor ao criar post para o usuário {}: {}", requestDto.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Ocorreu um erro interno."));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllPosts(Pageable pageable) {
        log.info("Recebida requisição GET /api/posts com pageable: {}", pageable);
        try {
            Page<PostResponseDto> posts = postService.getAllPosts(pageable);
            return ResponseEntity.ok(posts);
        } catch (IllegalStateException e) {
            log.error("Erro de estado ao buscar posts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar todos os posts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Ocorreu um erro interno."));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPostsByUserId(@PathVariable String userId, Pageable pageable) {
        log.info("Recebida requisição GET /api/posts/user/{} com pageable: {}", userId, pageable);
        try {
            Page<PostResponseDto> posts = postService.getPostsByUserId(userId, pageable);
            return ResponseEntity.ok(posts);
        } catch (IllegalArgumentException e) {
            log.warn("Requisição inválida ao buscar posts para o usuário {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Erro de estado ao buscar posts para o usuário {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar posts para o usuário {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Ocorreu um erro interno."));
        }
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable String postId, @RequestBody DeletePostRequestDto requestDto) {
        log.info("Recebida requisição DELETE /api/posts/{} do usuário: {}", postId, requestDto.getUserId());

        if (requestDto.getUserId() == null || requestDto.getUserId().isBlank()) {
            return ResponseEntity.badRequest().body("ID do usuário é obrigatório no corpo da requisição.");
        }

        try {
            postService.deletePost(postId, requestDto);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Requisição inválida ao deletar post {}: {}", postId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (SecurityException e) {
            log.error("Erro de autorização ao deletar post {}: {}", postId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Erro de estado ao deletar post {}: {}", postId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro interno do servidor ao deletar post {}: {}", postId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Ocorreu um erro interno."));
        }
    }
} 