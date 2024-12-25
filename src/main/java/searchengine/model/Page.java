package searchengine.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "page", indexes = {@Index(name = "idx_path", columnList = "path")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "path", columnDefinition = "TEXT", nullable = false)
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}