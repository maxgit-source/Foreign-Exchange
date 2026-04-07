package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.*;
import com.tunegocio.turnosapi.entity.*;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ConflictException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SitioBuilderService {

    private final SitioConfigRepository sitioConfigRepository;
    private final PaginaRepository       paginaRepository;
    private final SeccionRepository      seccionRepository;

    // ─── Sitio Config ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SitioConfigDTO obtenerConfig() {
        SitioConfig cfg = sitioConfigRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Configuración del sitio no encontrada"));
        return toConfigDTO(cfg);
    }

    @Transactional
    public SitioConfigDTO actualizarConfig(SitioConfigUpdateDTO dto) {
        SitioConfig cfg = sitioConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(SitioConfig::new);

        if (dto.plantillaCodigo()      != null) cfg.setPlantillaCodigo(dto.plantillaCodigo());
        if (dto.dominioCustom()        != null) cfg.setDominioCustom(dto.dominioCustom());
        if (dto.nombreSitio()          != null) cfg.setNombreSitio(dto.nombreSitio());
        if (dto.descripcionSitio()     != null) cfg.setDescripcionSitio(dto.descripcionSitio());
        if (dto.faviconUrl()           != null) cfg.setFaviconUrl(dto.faviconUrl());
        if (dto.logoUrl()              != null) cfg.setLogoUrl(dto.logoUrl());
        if (dto.paleta()               != null) cfg.setPaleta(dto.paleta());
        if (dto.redesSociales()        != null) cfg.setRedesSociales(dto.redesSociales());
        if (dto.seoTitulo()            != null) cfg.setSeoTitulo(dto.seoTitulo());
        if (dto.seoDescripcion()       != null) cfg.setSeoDescripcion(dto.seoDescripcion());
        if (dto.seoKeywords()          != null) cfg.setSeoKeywords(dto.seoKeywords());
        if (dto.ogImagenUrl()          != null) cfg.setOgImagenUrl(dto.ogImagenUrl());
        if (dto.fuentePrincipal()      != null) cfg.setFuentePrincipal(dto.fuentePrincipal());
        if (dto.fuenteTitulos()        != null) cfg.setFuenteTitulos(dto.fuenteTitulos());
        if (dto.animacionesActivas()   != null) cfg.setAnimacionesActivas(dto.animacionesActivas());
        if (dto.modoOscuroDefault()    != null) cfg.setModoOscuroDefault(dto.modoOscuroDefault());
        if (dto.moduloTienda()         != null) cfg.setModuloTienda(dto.moduloTienda());
        if (dto.moduloBlog()           != null) cfg.setModuloBlog(dto.moduloBlog());
        if (dto.moduloTurnos()         != null) cfg.setModuloTurnos(dto.moduloTurnos());
        if (dto.sitioActivo()          != null) cfg.setSitioActivo(dto.sitioActivo());
        if (dto.mensajeMantenimiento() != null) cfg.setMensajeMantenimiento(dto.mensajeMantenimiento());
        if (dto.configExtra()          != null) cfg.setConfigExtra(dto.configExtra());

        return toConfigDTO(sitioConfigRepository.save(cfg));
    }

    // ─── Páginas ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaginaDTO> listarPaginas() {
        return paginaRepository.findByActivaTrueOrderByOrdenNavAsc()
                .stream().map(p -> toPaginaDTO(p, false)).toList();
    }

    @Transactional(readOnly = true)
    public PaginaDTO obtenerPagina(Long id) {
        Pagina p = paginaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Página no encontrada"));
        return toPaginaDTO(p, true);
    }

    @Transactional(readOnly = true)
    public PaginaDTO obtenerPaginaPorSlug(String slug) {
        Pagina p = paginaRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Página no encontrada: " + slug));
        return toPaginaDTO(p, true);
    }

    @Transactional
    public PaginaDTO crearPagina(PaginaCreateDTO dto) {
        if (paginaRepository.existsBySlug(dto.slug())) {
            throw new ConflictException("Ya existe una página con el slug: " + dto.slug());
        }
        Pagina p = new Pagina();
        p.setSlug(dto.slug());
        p.setTitulo(dto.titulo());
        p.setDescripcion(dto.descripcion());
        p.setActiva(dto.activa());
        p.setEnNav(dto.enNav());
        p.setOrdenNav(dto.ordenNav());
        p.setSeoTitulo(dto.seoTitulo());
        p.setSeoDescripcion(dto.seoDescripcion());
        p.setOgImagenUrl(dto.ogImagenUrl());
        return toPaginaDTO(paginaRepository.save(p), false);
    }

    @Transactional
    public PaginaDTO actualizarPagina(Long id, PaginaCreateDTO dto) {
        Pagina p = paginaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Página no encontrada"));

        if (!p.getSlug().equals(dto.slug()) && paginaRepository.existsBySlug(dto.slug())) {
            throw new ConflictException("Ya existe una página con el slug: " + dto.slug());
        }
        if (p.isEsHome()) {
            throw new BusinessException("La página de inicio no puede ser modificada desde este endpoint");
        }

        p.setSlug(dto.slug());
        p.setTitulo(dto.titulo());
        p.setDescripcion(dto.descripcion());
        p.setActiva(dto.activa());
        p.setEnNav(dto.enNav());
        p.setOrdenNav(dto.ordenNav());
        p.setSeoTitulo(dto.seoTitulo());
        p.setSeoDescripcion(dto.seoDescripcion());
        p.setOgImagenUrl(dto.ogImagenUrl());
        return toPaginaDTO(paginaRepository.save(p), false);
    }

    @Transactional
    public void eliminarPagina(Long id) {
        Pagina p = paginaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Página no encontrada"));
        if (p.isEsHome()) {
            throw new BusinessException("No se puede eliminar la página de inicio");
        }
        paginaRepository.delete(p);
    }

    // ─── Secciones ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SeccionDTO> listarSecciones(Long paginaId) {
        paginaRepository.findById(paginaId)
                .orElseThrow(() -> new ResourceNotFoundException("Página no encontrada"));
        return seccionRepository.findByPaginaIdOrderByOrdenAsc(paginaId)
                .stream().map(this::toSeccionDTO).toList();
    }

    @Transactional
    public SeccionDTO agregarSeccion(Long paginaId, SeccionCreateDTO dto) {
        Pagina pagina = paginaRepository.findById(paginaId)
                .orElseThrow(() -> new ResourceNotFoundException("Página no encontrada"));

        Seccion s = new Seccion();
        s.setPagina(pagina);
        s.setTipo(dto.tipo());
        s.setOrden(dto.orden());
        s.setVisible(dto.visible());
        s.setConfig(dto.config() != null ? dto.config() : "{}");
        s.setAnimacionEntrada(dto.animacionEntrada());
        s.setAnimacionDuracion(dto.animacionDuracion() != null ? dto.animacionDuracion() : 600);
        s.setAnimacionDelay(dto.animacionDelay() != null ? dto.animacionDelay() : 0);
        s.setEstilos(dto.estilos() != null ? dto.estilos() : "{}");
        return toSeccionDTO(seccionRepository.save(s));
    }

    @Transactional
    public SeccionDTO actualizarSeccion(Long paginaId, Long seccionId, SeccionCreateDTO dto) {
        Seccion s = seccionRepository.findById(seccionId)
                .filter(sec -> sec.getPagina().getId().equals(paginaId))
                .orElseThrow(() -> new ResourceNotFoundException("Sección no encontrada"));

        if (dto.tipo()             != null) s.setTipo(dto.tipo());
        s.setOrden(dto.orden());
        s.setVisible(dto.visible());
        if (dto.config()           != null) s.setConfig(dto.config());
        if (dto.animacionEntrada() != null) s.setAnimacionEntrada(dto.animacionEntrada());
        if (dto.animacionDuracion()!= null) s.setAnimacionDuracion(dto.animacionDuracion());
        if (dto.animacionDelay()   != null) s.setAnimacionDelay(dto.animacionDelay());
        if (dto.estilos()          != null) s.setEstilos(dto.estilos());
        return toSeccionDTO(seccionRepository.save(s));
    }

    @Transactional
    public void eliminarSeccion(Long paginaId, Long seccionId) {
        Seccion s = seccionRepository.findById(seccionId)
                .filter(sec -> sec.getPagina().getId().equals(paginaId))
                .orElseThrow(() -> new ResourceNotFoundException("Sección no encontrada"));
        seccionRepository.delete(s);
    }

    @Transactional
    public List<SeccionDTO> reordenarSecciones(Long paginaId, List<Long> ordenIds) {
        paginaRepository.findById(paginaId)
                .orElseThrow(() -> new ResourceNotFoundException("Página no encontrada"));
        List<Seccion> secciones = seccionRepository.findByPaginaIdOrderByOrdenAsc(paginaId);
        for (int i = 0; i < ordenIds.size(); i++) {
            final int orden = i;
            final Long secId = ordenIds.get(i);
            secciones.stream()
                    .filter(s -> s.getId().equals(secId))
                    .findFirst()
                    .ifPresent(s -> s.setOrden(orden));
        }
        seccionRepository.saveAll(secciones);
        return seccionRepository.findByPaginaIdOrderByOrdenAsc(paginaId)
                .stream().map(this::toSeccionDTO).toList();
    }

    // ─── Mappers privados ────────────────────────────────────────────────────

    private SitioConfigDTO toConfigDTO(SitioConfig c) {
        return new SitioConfigDTO(
                c.getId(), c.getPlantillaCodigo(), c.getDominioCustom(), c.getNombreSitio(),
                c.getDescripcionSitio(), c.getFaviconUrl(), c.getLogoUrl(), c.getPaleta(),
                c.getRedesSociales(), c.getSeoTitulo(), c.getSeoDescripcion(), c.getSeoKeywords(),
                c.getOgImagenUrl(), c.getFuentePrincipal(), c.getFuenteTitulos(),
                c.isAnimacionesActivas(), c.isModoOscuroDefault(), c.isModuloTienda(),
                c.isModuloBlog(), c.isModuloTurnos(), c.isSitioActivo(),
                c.getMensajeMantenimiento(), c.getConfigExtra()
        );
    }

    private PaginaDTO toPaginaDTO(Pagina p, boolean includeSecciones) {
        List<SeccionDTO> secs = includeSecciones
                ? p.getSecciones().stream().map(this::toSeccionDTO).toList()
                : List.of();
        return new PaginaDTO(
                p.getId(), p.getSlug(), p.getTitulo(), p.getDescripcion(),
                p.isActiva(), p.isEsHome(), p.isEnNav(), p.getOrdenNav(),
                p.getSeoTitulo(), p.getSeoDescripcion(), p.getOgImagenUrl(), secs
        );
    }

    private SeccionDTO toSeccionDTO(Seccion s) {
        return new SeccionDTO(
                s.getId(), s.getPagina().getId(), s.getTipo(), s.getOrden(), s.isVisible(),
                s.getConfig(), s.getAnimacionEntrada(), s.getAnimacionDuracion(),
                s.getAnimacionDelay(), s.getEstilos()
        );
    }
}
