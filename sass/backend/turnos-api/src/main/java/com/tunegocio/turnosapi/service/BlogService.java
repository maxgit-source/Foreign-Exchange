package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.*;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.BlogCategoriaRepository;
import com.tunegocio.turnosapi.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogPostRepository     blogPostRepository;
    private final BlogCategoriaRepository blogCategoriaRepository;

    // ─── Categorías ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlogCategoriaDTO> listarCategorias() {
        return blogCategoriaRepository.findAllByOrderByNombreAsc()
                .stream().map(this::toCategoriaDTO).toList();
    }

    @Transactional
    public BlogCategoriaDTO crearCategoria(BlogCategoriaCreateDTO dto) {
        if (blogCategoriaRepository.existsBySlug(dto.slug())) {
            throw new ConflictException("Ya existe una categoría con el slug: " + dto.slug());
        }
        BlogCategoria cat = new BlogCategoria();
        cat.setNombre(dto.nombre());
        cat.setSlug(dto.slug());
        cat.setDescripcion(dto.descripcion());
        return toCategoriaDTO(blogCategoriaRepository.save(cat));
    }

    @Transactional
    public void eliminarCategoria(Long id) {
        BlogCategoria cat = blogCategoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
        blogCategoriaRepository.delete(cat);
    }

    // ─── Posts ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BlogPostDTO> listarPublicados(Pageable pageable) {
        return blogPostRepository.findByEstadoOrderByPublicadoAtDesc(BlogEstado.PUBLICADO, pageable)
                .map(this::toPostDTO);
    }

    @Transactional(readOnly = true)
    public Page<BlogPostDTO> listarTodos(Pageable pageable) {
        return blogPostRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toPostDTO);
    }

    @Transactional(readOnly = true)
    public Page<BlogPostDTO> listarPorCategoria(Long categoriaId, Pageable pageable) {
        return blogPostRepository.findByCategoriaIdAndEstadoOrderByPublicadoAtDesc(
                categoriaId, BlogEstado.PUBLICADO, pageable).map(this::toPostDTO);
    }

    @Transactional(readOnly = true)
    public BlogPostDTO obtenerPorSlug(String slug) {
        return blogPostRepository.findBySlug(slug)
                .map(this::toPostDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado: " + slug));
    }

    @Transactional(readOnly = true)
    public BlogPostDTO obtenerPorId(Long id) {
        return blogPostRepository.findById(id)
                .map(this::toPostDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado"));
    }

    @Transactional
    public BlogPostDTO crear(BlogPostCreateDTO dto) {
        if (blogPostRepository.existsBySlug(dto.slug())) {
            throw new ConflictException("Ya existe un post con el slug: " + dto.slug());
        }
        BlogPost post = buildPost(new BlogPost(), dto);
        return toPostDTO(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostDTO actualizar(Long id, BlogPostCreateDTO dto) {
        BlogPost post = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado"));
        if (!post.getSlug().equals(dto.slug()) && blogPostRepository.existsBySlug(dto.slug())) {
            throw new ConflictException("Ya existe un post con el slug: " + dto.slug());
        }
        return toPostDTO(blogPostRepository.save(buildPost(post, dto)));
    }

    @Transactional
    public BlogPostDTO publicar(Long id) {
        BlogPost post = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado"));
        post.setEstado(BlogEstado.PUBLICADO);
        if (post.getPublicadoAt() == null) {
            post.setPublicadoAt(LocalDateTime.now());
        }
        return toPostDTO(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostDTO archivar(Long id) {
        BlogPost post = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado"));
        post.setEstado(BlogEstado.ARCHIVADO);
        return toPostDTO(blogPostRepository.save(post));
    }

    @Transactional
    public void eliminar(Long id) {
        BlogPost post = blogPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado"));
        blogPostRepository.delete(post);
    }

    @Transactional
    public void registrarVista(Long id) {
        blogPostRepository.incrementarVistas(id);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private BlogPost buildPost(BlogPost post, BlogPostCreateDTO dto) {
        post.setTitulo(dto.titulo());
        post.setSlug(dto.slug());
        post.setExtracto(dto.extracto());
        post.setContenido(dto.contenido());
        post.setImagenPortada(dto.imagenPortada());
        post.setAutorNombre(dto.autorNombre());
        post.setSeoTitulo(dto.seoTitulo());
        post.setSeoDescripcion(dto.seoDescripcion());
        post.setOgImagenUrl(dto.ogImagenUrl());
        post.setTags(dto.tags());

        BlogEstado estado = dto.estado() != null ? dto.estado() : BlogEstado.BORRADOR;
        post.setEstado(estado);
        if (estado == BlogEstado.PUBLICADO && post.getPublicadoAt() == null) {
            post.setPublicadoAt(LocalDateTime.now());
        }

        if (dto.categoriaId() != null) {
            BlogCategoria cat = blogCategoriaRepository.findById(dto.categoriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
            post.setCategoria(cat);
        } else {
            post.setCategoria(null);
        }

        return post;
    }

    private BlogCategoriaDTO toCategoriaDTO(BlogCategoria c) {
        return new BlogCategoriaDTO(c.getId(), c.getNombre(), c.getSlug(), c.getDescripcion());
    }

    private BlogPostDTO toPostDTO(BlogPost p) {
        return new BlogPostDTO(
                p.getId(), p.getTitulo(), p.getSlug(), p.getExtracto(), p.getContenido(),
                p.getImagenPortada(), p.getEstado(), p.getPublicadoAt(),
                p.getCategoria() != null ? toCategoriaDTO(p.getCategoria()) : null,
                p.getAutorNombre(), p.getSeoTitulo(), p.getSeoDescripcion(), p.getOgImagenUrl(),
                p.getTags(), p.getVistas(), p.getCreatedAt()
        );
    }
}
