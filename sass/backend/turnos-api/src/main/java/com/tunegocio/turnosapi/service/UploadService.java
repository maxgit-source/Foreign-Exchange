package com.tunegocio.turnosapi.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tunegocio.turnosapi.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final ObjectProvider<Cloudinary> cloudinaryProvider;

    @Value("${app.upload.max-image-size-bytes:5242880}")
    private long maxImageSizeBytes;

    public String uploadImage(MultipartFile file, String folder) {
        validateImage(file);

        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new BusinessException("El proveedor de almacenamiento de imágenes no está configurado");
        }

        try {
            @SuppressWarnings("rawtypes")
            Map result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "use_filename", true,
                            "unique_filename", true,
                            "overwrite", false,
                            "format", "webp",
                            "transformation", "c_limit,w_800,h_800"
                    )
            );

            Object secureUrl = result.get("secure_url");
            if (!(secureUrl instanceof String url) || url.isBlank()) {
                throw new IllegalStateException("Cloudinary no devolvió una URL válida");
            }
            return url;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo a subir", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo subir la imagen al proveedor configurado", ex);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Debe enviar un archivo de imagen");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("Solo se permiten imágenes JPG, PNG o WEBP");
        }

        if (file.getSize() > maxImageSizeBytes) {
            throw new BusinessException("La imagen supera el tamaño máximo permitido de 5MB");
        }
    }
}
