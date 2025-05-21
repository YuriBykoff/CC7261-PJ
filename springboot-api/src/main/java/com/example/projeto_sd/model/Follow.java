package com.example.projeto_sd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "follows",
       uniqueConstraints = {
           @UniqueConstraint(name = "unique_follow", columnNames = {"follower_id", "followed_id"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Follow {

    @Id
    @Column(nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false, foreignKey = @ForeignKey(name = "follows_follower_id_fk"))
    private User follower; // O usuário que segue

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followed_id", nullable = false, foreignKey = @ForeignKey(name = "follows_followed_id_fk"))
    private User followed; // O usuário que é seguido

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
} 