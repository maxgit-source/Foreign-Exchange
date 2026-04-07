package com.tunegocio.turnosapi.service;

import com.tunegocio.turnosapi.entity.NotificacionLog;
import com.tunegocio.turnosapi.entity.Pedido;
import com.tunegocio.turnosapi.entity.PedidoItem;
import com.tunegocio.turnosapi.entity.Servicio;
import com.tunegocio.turnosapi.entity.Tenant;
import com.tunegocio.turnosapi.entity.Turno;
import com.tunegocio.turnosapi.entity.Usuario;
import com.tunegocio.turnosapi.repository.NotificacionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy 'a las' HH:mm", new Locale("es", "AR"));

    private final JavaMailSender             mailSender;
    private final TenantDateTimeMapper       tenantDateTimeMapper;
    private final NotificacionLogRepository  notificacionLogRepository;

    @Value("${spring.mail.username:noreply@tunegocio.com}")
    private String fromEmail;

    @Value("${app.name:TuNegocio}")
    private String appName;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void enviarConfirmacionTurno(Turno turno) {
        String asunto = "Confirmación de turno - " + tenant(turno).getNombre();
        enviarEmailClienteConLog(turno, asunto, buildEmailConfirmacion(turno), "confirmación", "CONFIRMACION");
    }

    @Async
    public void enviarRecordatorio(Turno turno) {
        String asunto = "Recordatorio: turno mañana - " + tenant(turno).getNombre();
        enviarEmailClienteConLog(turno, asunto, buildEmailRecordatorio(turno), "recordatorio", "RECORDATORIO");
    }

    @Async
    public void enviarCancelacion(Turno turno) {
        String asunto = "Tu turno fue cancelado - " + tenant(turno).getNombre();
        enviarEmailClienteConLog(turno, asunto, buildEmailCancelacion(turno), "cancelación", "CANCELACION");
    }

    @Async
    public void enviarReprogramacion(Turno turno) {
        String asunto = "Tu turno fue reprogramado - " + tenant(turno).getNombre();
        enviarEmailClienteConLog(turno, asunto, buildEmailReprogramacion(turno), "reprogramación", "REPROGRAMACION");
    }

    @Async
    public void enviarConfirmacionPedido(Pedido pedido, Tenant tenant) {
        String email = pedido.getEmailCliente();
        if (email == null || email.isBlank()) {
            return;
        }

        try {
            enviarEmail(email, "Confirmacion de pedido - " + tenant.getNombre(),
                    buildEmailConfirmacionPedido(pedido, tenant));
            log.info("Email de confirmacion enviado a '{}' para pedido id={}", email, pedido.getId());
        } catch (Exception ex) {
            log.warn("No se pudo enviar email de confirmacion para pedido id={}: {}", pedido.getId(), ex.getMessage());
        }
    }

    @Async
    public void enviarInvitacionStaff(String destinatario, Tenant tenant, String rawToken, LocalDateTime expiresAt) {
        try {
            String asunto = "Invitación para unirte al equipo de " + tenant.getNombre();
            String link = frontendUrl + "/staff/aceptar-invitacion?token=" + rawToken;
            String cuerpo = """
                    <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 640px; margin: 0 auto;">
                      <div style="background: %s; padding: 24px; border-radius: 8px 8px 0 0;">
                        <h1 style="color: white; margin: 0;">Invitación al equipo</h1>
                      </div>
                      <div style="padding: 24px; border: 1px solid #e5e7eb; border-radius: 0 0 8px 8px;">
                        <p>Fuiste invitado a unirte como miembro del staff de <strong>%s</strong>.</p>
                        <p>La invitación vence el <strong>%s</strong>.</p>
                        <p>
                          <a href="%s" style="display:inline-block;padding:12px 18px;background:%s;color:#fff;text-decoration:none;border-radius:6px;">
                            Aceptar invitación
                          </a>
                        </p>
                        <p style="color:#6b7280;font-size:13px;">Si no esperabas este correo, podés ignorarlo.</p>
                      </div>
                    </body></html>
                    """.formatted(
                    tenant.getColorPrimario(),
                    tenant.getNombre(),
                    expiresAt.format(FORMATO_FECHA),
                    link,
                    tenant.getColorPrimario()
            );
            enviarEmail(destinatario, asunto, cuerpo);
        } catch (Exception ex) {
            log.warn("No se pudo enviar invitación de staff a '{}': {}", destinatario, ex.getMessage());
        }
    }

    @Async
    public void enviarBienvenidaStaff(Usuario staff) {
        try {
            String asunto = "Bienvenido al equipo de " + staff.getTenant().getNombre();
            String cuerpo = """
                    <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                      <div style="background: %s; padding: 24px; border-radius: 8px 8px 0 0;">
                        <h1 style="color: white; margin: 0;">Alta de staff confirmada</h1>
                      </div>
                      <div style="padding: 24px; border: 1px solid #e5e7eb; border-radius: 0 0 8px 8px;">
                        <p>Hola <strong>%s</strong>,</p>
                        <p>Tu cuenta de staff para <strong>%s</strong> ya está activa.</p>
                        <p>Podés iniciar sesión con este email: <strong>%s</strong>.</p>
                      </div>
                    </body></html>
                    """.formatted(
                    staff.getTenant().getColorPrimario(),
                    staff.getNombre(),
                    staff.getTenant().getNombre(),
                    staff.getEmail()
            );
            enviarEmail(staff.getEmail(), asunto, cuerpo);
        } catch (Exception ex) {
            log.warn("No se pudo enviar bienvenida de staff a '{}': {}", staff.getEmail(), ex.getMessage());
        }
    }

    private void enviarEmailClienteConLog(Turno turno, String asunto, String cuerpo,
                                           String tipoLog, String tipoEnum) {
        String emailCliente = turno.getCliente().getEmail();
        if (emailCliente == null || emailCliente.isBlank()) {
            return;
        }

        try {
            enviarEmail(emailCliente, asunto, cuerpo);
            log.info("Email de {} enviado a '{}' para turno id={}", tipoLog, emailCliente, turno.getId());
            notificacionLogRepository.save(
                    NotificacionLog.enviado(tipoEnum, emailCliente, asunto, turno.getId()));
        } catch (Exception ex) {
            log.warn("No se pudo enviar email de {} para turno id={}: {}", tipoLog, turno.getId(), ex.getMessage());
            notificacionLogRepository.save(
                    NotificacionLog.error(tipoEnum, emailCliente, asunto, turno.getId(), ex.getMessage()));
        }
    }

    /** @deprecated Usar {@link #enviarEmailClienteConLog} */
    private void enviarEmailCliente(Turno turno, String asunto, String cuerpo, String tipo) {
        enviarEmailClienteConLog(turno, asunto, cuerpo, tipo, tipo.toUpperCase());
    }

    private void enviarEmail(String destinatario, String asunto, String cuerpoHtml) throws Exception {
        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, appName);
        helper.setTo(destinatario);
        helper.setSubject(asunto);
        helper.setText(cuerpoHtml, true);
        mailSender.send(message);
    }

    private String buildEmailConfirmacion(Turno turno) {
        return """
                <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                  <div style="background: %s; padding: 24px; border-radius: 8px 8px 0 0;">
                    <h1 style="color: white; margin: 0;">Turno confirmado</h1>
                  </div>
                  <div style="padding: 24px; border: 1px solid #e0e0e0; border-radius: 0 0 8px 8px;">
                    <p>Hola <strong>%s</strong>,</p>
                    <p>Tu turno fue confirmado con los siguientes detalles:</p>
                    <table style="width: 100%%; border-collapse: collapse; margin: 16px 0;">
                      <tr><td style="padding: 8px; border-bottom: 1px solid #eee; font-weight: bold;">Servicios</td>
                          <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                      <tr><td style="padding: 8px; border-bottom: 1px solid #eee; font-weight: bold;">Profesional</td>
                          <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                      <tr><td style="padding: 8px; border-bottom: 1px solid #eee; font-weight: bold;">Fecha y hora</td>
                          <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                      <tr><td style="padding: 8px; font-weight: bold;">Duración total</td>
                          <td style="padding: 8px;">%d minutos</td></tr>
                    </table>
                    <p style="color: #888; font-size: 12px;">Equipo de %s</p>
                  </div>
                </body></html>
                """.formatted(
                tenant(turno).getColorPrimario(),
                turno.getCliente().getNombreCompleto(),
                descripcionServicios(turno),
                turno.getProfesional().getNombre(),
                formatFecha(turno),
                turno.getDuracionTotalMinutos(),
                tenant(turno).getNombre()
        );
    }

    private String buildEmailRecordatorio(Turno turno) {
        return """
                <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                  <div style="background: #f59e0b; padding: 24px; border-radius: 8px 8px 0 0;">
                    <h1 style="color: white; margin: 0;">Recordatorio de turno</h1>
                  </div>
                  <div style="padding: 24px; border: 1px solid #e0e0e0; border-radius: 0 0 8px 8px;">
                    <p>Hola <strong>%s</strong>,</p>
                    <p>Te recordamos que mañana tenés turno:</p>
                    <p style="font-size: 18px; font-weight: bold; color: %s;">%s</p>
                    <p><strong>Servicios:</strong> %s con %s</p>
                    <p style="color: #888; font-size: 12px;">%s</p>
                  </div>
                </body></html>
                """.formatted(
                turno.getCliente().getNombreCompleto(),
                tenant(turno).getColorPrimario(),
                formatFecha(turno),
                descripcionServicios(turno),
                turno.getProfesional().getNombre(),
                tenant(turno).getNombre()
        );
    }

    private String buildEmailCancelacion(Turno turno) {
        return """
                <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                  <div style="background: #ef4444; padding: 24px; border-radius: 8px 8px 0 0;">
                    <h1 style="color: white; margin: 0;">Turno cancelado</h1>
                  </div>
                  <div style="padding: 24px; border: 1px solid #e0e0e0; border-radius: 0 0 8px 8px;">
                    <p>Hola <strong>%s</strong>,</p>
                    <p>Tu turno del <strong>%s</strong> fue cancelado.</p>
                    <p>Si querés reprogramarlo, comunicate con <strong>%s</strong>.</p>
                  </div>
                </body></html>
                """.formatted(
                turno.getCliente().getNombreCompleto(),
                formatFecha(turno),
                tenant(turno).getNombre()
        );
    }

    private String buildEmailReprogramacion(Turno turno) {
        return """
                <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                  <div style="background: #2563eb; padding: 24px; border-radius: 8px 8px 0 0;">
                    <h1 style="color: white; margin: 0;">Turno reprogramado</h1>
                  </div>
                  <div style="padding: 24px; border: 1px solid #e0e0e0; border-radius: 0 0 8px 8px;">
                    <p>Hola <strong>%s</strong>,</p>
                    <p>Tu turno fue reprogramado para el <strong>%s</strong>.</p>
                    <p><strong>Servicios:</strong> %s con %s</p>
                    <p style="color: #888; font-size: 12px;">%s</p>
                  </div>
                </body></html>
                """.formatted(
                turno.getCliente().getNombreCompleto(),
                formatFecha(turno),
                descripcionServicios(turno),
                turno.getProfesional().getNombre(),
                tenant(turno).getNombre()
        );
    }

    private String buildEmailConfirmacionPedido(Pedido pedido, Tenant tenant) {
        String items = pedido.getItems().stream()
                .sorted(Comparator.comparing(PedidoItem::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(this::descripcionItemPedido)
                .reduce((a, b) -> a + b)
                .orElse("<tr><td colspan=\"2\" style=\"padding: 8px; border-bottom: 1px solid #eee;\">Pedido sin items</td></tr>");

        return """
                <html><body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                  <div style="background: %s; padding: 24px; border-radius: 8px 8px 0 0;">
                    <h1 style="color: white; margin: 0;">Pedido confirmado</h1>
                  </div>
                  <div style="padding: 24px; border: 1px solid #e0e0e0; border-radius: 0 0 8px 8px;">
                    <p>Hola <strong>%s</strong>,</p>
                    <p>Recibimos y confirmamos tu pedido. Este es el resumen:</p>
                    <table style="width: 100%%; border-collapse: collapse; margin: 16px 0;">
                      %s
                      <tr><td style="padding: 8px; border-top: 2px solid #e5e7eb; font-weight: bold;">Subtotal</td>
                          <td style="padding: 8px; border-top: 2px solid #e5e7eb; text-align: right;">$ %s</td></tr>
                      <tr><td style="padding: 8px; font-weight: bold;">Total</td>
                          <td style="padding: 8px; text-align: right; font-weight: bold;">$ %s</td></tr>
                    </table>
                    <p><strong>Metodo de pago:</strong> %s</p>
                    <p style="color: #888; font-size: 12px;">Equipo de %s</p>
                  </div>
                </body></html>
                """.formatted(
                tenant.getColorPrimario(),
                pedido.getNombreCliente(),
                items,
                pedido.getSubtotal(),
                pedido.getTotal(),
                pedido.getPaymentMethod() != null ? pedido.getPaymentMethod().name() : "PENDIENTE",
                tenant.getNombre()
        );
    }

    private String formatFecha(Turno turno) {
        return tenantDateTimeMapper.toLocalDateTime(turno.getFechaHoraInicio(), tenant(turno)).format(FORMATO_FECHA);
    }

    private String descripcionItemPedido(PedidoItem item) {
        return """
                <tr>
                  <td style="padding: 8px; border-bottom: 1px solid #eee;">%s x%d</td>
                  <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right;">$ %s</td>
                </tr>
                """.formatted(item.getNombre(), item.getCantidad(), item.getSubtotal());
    }

    private String descripcionServicios(Turno turno) {
        List<Servicio> servicios = turno.getServicios().stream()
                .sorted(Comparator.comparing(Servicio::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return servicios.stream()
                .map(Servicio::getNombre)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Servicio");
    }

    private Tenant tenant(Turno turno) {
        return turno.getProfesional().getTenant();
    }

}
