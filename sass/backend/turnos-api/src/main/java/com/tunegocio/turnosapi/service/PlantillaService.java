package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.PlantillaDTO;
import com.tunegocio.turnosapi.entity.Plantilla;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.PlantillaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantillaService {

    private final PlantillaRepository plantillaRepository;

    @Transactional(readOnly = true)
    public List<PlantillaDTO> listarActivas() {
        return plantillaRepository.findByActivaTrueOrderByOrdenAsc()
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<PlantillaDTO> listarPorCategoria(String categoria) {
        return plantillaRepository.findByCategoriaAndActivaTrueOrderByOrdenAsc(categoria)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public PlantillaDTO obtenerPorCodigo(String codigo) {
        return plantillaRepository.findByCodigo(codigo)
                .map(this::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla no encontrada: " + codigo));
    }

    private PlantillaDTO toDTO(Plantilla p) {
        return new PlantillaDTO(
                p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                p.getCategoria(), p.getPreviewUrl(), p.getThumbnailUrl(),
                p.getTags(), p.isActiva(), p.getOrden(), p.getConfigDefault()
        );
    }
}
