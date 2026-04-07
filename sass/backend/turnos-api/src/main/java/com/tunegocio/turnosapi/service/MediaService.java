package com.tunegocio.turnosapi.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tunegocio.turnosapi.dto.MediaFileDTO;
import com.tunegocio.turnosapi.entity.MediaFile;
import com.tunegocio.turnosapi.exception.BusinessException;
import com.tunegocio.turnosapi.exception.ResourceNotFoundException;
import com.tunegocio.turnosapi.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml",
            "video/mp4", "video/webm"
    );

    private final MediaFileRepository  mediaFileRepository;
    private final ObjectProvider<Cloudinary> cloudinaryProvider;

    @Transactional(readOnly = true)
    public Page<MediaFileDTO> listar(String tipo, String carpeta, Pageable pageable) {
        if (tipo != null && !tipo.isBlank()) {
            return mediaFileRepository.findByTipoOrderByCreatedAtDesc(tipo, pageable).map(this::toDTO);
        }
        if (carpeta != null && !carpeta.isBlank()) {
            return mediaFileRepository.findByCarpetaOrderByCreatedAtDesc(carpeta, pageable).map(this::toDTO);
        }
        return mediaFileRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<String> listarCarpetas() {
        return mediaFileRepository.findDistinctCarpetaBy();
    }

    @Transactional
    public MediaFileDTO subir(MultipartFile file, String carpeta, String nombre, String tags) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Debe enviar un archivo");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Tipo de archivo no permitido");
        }

        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new BusinessException("El proveedor de almacenamiento no está configurado");
        }

        String folderName = (carpeta != null && !carpeta.isBlank()) ? carpeta : "media";
        boolean isVideo   = contentType.startsWith("video/");
        String resourceType = isVideo ? "video" : "image";

        try {
            @SuppressWarnings("rawtypes")
            Map result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folderName,
                            "resource_type", resourceType,
                            "use_filename", true,
                            "unique_filename", true
                    )
            );

            MediaFile mf = new MediaFile();
            mf.setCloudinaryId(String.valueOf(result.get("public_id")));
            mf.setUrl(String.valueOf(result.get("secure_url")));
            mf.setNombre(nombre != null && !nombre.isBlank() ? nombre : file.getOriginalFilename());
            mf.setTipo(resourceType);
            mf.setFormato(String.valueOf(result.getOrDefault("format", "")));
            mf.setTamanioBytes(file.getSize());
            mf.setCarpeta(folderName);
            mf.setTags(tags);

            Object width = result.get("width");
            Object height = result.get("height");
            if (width instanceof Number w) mf.setAncho(w.intValue());
            if (height instanceof Number h) mf.setAlto(h.intValue());

            return toDTO(mediaFileRepository.save(mf));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Error al subir el archivo", ex);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        MediaFile mf = mediaFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Archivo no encontrado"));

        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary != null) {
            try {
                cloudinary.uploader().destroy(mf.getCloudinaryId(),
                        ObjectUtils.asMap("resource_type", mf.getTipo()));
            } catch (Exception ex) {
                log.warn("No se pudo eliminar de Cloudinary: {}", mf.getCloudinaryId(), ex);
            }
        }

        mediaFileRepository.delete(mf);
    }

    private MediaFileDTO toDTO(MediaFile mf) {
        return new MediaFileDTO(
                mf.getId(), mf.getCloudinaryId(), mf.getUrl(), mf.getNombre(), mf.getTipo(),
                mf.getFormato(), mf.getTamanioBytes(), mf.getAncho(), mf.getAlto(),
                mf.getCarpeta(), mf.getTags(), mf.getCreatedAt()
        );
    }
}
