package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.dto.TurnoResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class ExportService {

    private static final String[] HEADERS_TURNOS = {
            "ID", "Fecha", "Hora Inicio", "Hora Fin",
            "Cliente", "Email Cliente", "Telefono",
            "Profesional", "Servicios", "Duracion (min)",
            "Precio Total", "Estado", "Notas"
    };

    // ─── CSV ─────────────────────────────────────────────────────────────────

    public byte[] exportarTurnosCSV(List<TurnoResponseDTO> turnos) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADERS_TURNOS)).append("\n");

        for (TurnoResponseDTO t : turnos) {
            csv.append(escapeCsv(String.valueOf(t.getId()))).append(",")
               .append(escapeCsv(t.getFechaHoraInicio() != null ? t.getFechaHoraInicio().toLocalDate().toString() : "")).append(",")
               .append(escapeCsv(t.getFechaHoraInicio() != null ? t.getFechaHoraInicio().toLocalTime().toString() : "")).append(",")
               .append(escapeCsv(t.getFechaHoraFin()    != null ? t.getFechaHoraFin().toLocalTime().toString()    : "")).append(",")
               .append(escapeCsv(t.getNombreCliente())).append(",")
               .append(escapeCsv(orVacio(t.getEmailCliente()))).append(",")
               .append(escapeCsv(orVacio(t.getTelefonoCliente()))).append(",")
               .append(escapeCsv(t.getNombreProfesional())).append(",")
               .append(escapeCsv(nombresServicios(t))).append(",")
               .append(escapeCsv(String.valueOf(orCero(t.getDuracionTotalMinutos())))).append(",")
               .append(escapeCsv(t.getPrecioTotal() != null ? t.getPrecioTotal().toPlainString() : "0.00")).append(",")
               .append(escapeCsv(t.getEstado())).append(",")
               .append(escapeCsv(orVacio(t.getNotas())))
               .append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ─── Excel ───────────────────────────────────────────────────────────────

    public byte[] exportarTurnosExcel(List<TurnoResponseDTO> turnos) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Turnos");

            // Estilo de cabecera
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Fila de cabecera
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS_TURNOS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS_TURNOS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowNum = 1;
            for (TurnoResponseDTO t : turnos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(t.getId());
                row.createCell(1).setCellValue(t.getFechaHoraInicio() != null ? t.getFechaHoraInicio().toLocalDate().toString() : "");
                row.createCell(2).setCellValue(t.getFechaHoraInicio() != null ? t.getFechaHoraInicio().toLocalTime().toString() : "");
                row.createCell(3).setCellValue(t.getFechaHoraFin()    != null ? t.getFechaHoraFin().toLocalTime().toString()    : "");
                row.createCell(4).setCellValue(orVacio(t.getNombreCliente()));
                row.createCell(5).setCellValue(orVacio(t.getEmailCliente()));
                row.createCell(6).setCellValue(orVacio(t.getTelefonoCliente()));
                row.createCell(7).setCellValue(orVacio(t.getNombreProfesional()));
                row.createCell(8).setCellValue(nombresServicios(t));
                row.createCell(9).setCellValue(orCero(t.getDuracionTotalMinutos()));
                row.createCell(10).setCellValue(t.getPrecioTotal() != null ? t.getPrecioTotal().doubleValue() : 0.0);
                row.createCell(11).setCellValue(orVacio(t.getEstado()));
                row.createCell(12).setCellValue(orVacio(t.getNotas()));
            }

            // Autoajustar columnas
            for (int i = 0; i < HEADERS_TURNOS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Error generando Excel de turnos", e);
            throw new RuntimeException("Error al generar el archivo Excel", e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String orVacio(String value) {
        return value != null ? value : "";
    }

    private int orCero(Integer value) {
        return value != null ? value : 0;
    }

    private String nombresServicios(TurnoResponseDTO t) {
        if (t.getServicios() != null && !t.getServicios().isEmpty()) {
            return t.getServicios().stream()
                    .map(s -> s.getNombre())
                    .reduce((a, b) -> a + " / " + b)
                    .orElse("");
        }
        return orVacio(t.getNombreServicio());
    }
}
