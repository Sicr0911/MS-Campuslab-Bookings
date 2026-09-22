package com.campuslab.bookings.controller;

import com.campuslab.bookings.dto.CambioEstadoRequestDTO;
import com.campuslab.bookings.dto.ReservaRequestDTO;
import com.campuslab.bookings.dto.ReservaResponseDTO;
import com.campuslab.bookings.model.EstadoReserva;
import com.campuslab.bookings.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /**
     * Crear una reserva. Cualquier usuario autenticado (ej. estudiante, docente)
     * puede solicitar una reserva; nace en estado SOLICITADA.
     */
    @PostMapping
    public ResponseEntity<ReservaResponseDTO> crear(@Valid @RequestBody ReservaRequestDTO request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        ReservaResponseDTO creada = reservaService.crear(request, extraerUsuarioId(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /**
     * Detalle de una reserva por id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id));
    }

    /**
     * Listado con filtros opcionales y paginacion.
     * Ej: GET /api/bookings?estado=SOLICITADA&recursoId=10&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<ReservaResponseDTO>> listar(
            @RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) Long recursoId,
            @RequestParam(required = false) Long usuarioSolicitanteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            Pageable pageable) {
        Page<ReservaResponseDTO> resultado = reservaService.listar(
                estado, recursoId, usuarioSolicitanteId, desde, hasta, pageable);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Cambiar el estado de una reserva.
     *
     * Solo TECNICO o ADMIN pueden aprobar (APROBADA) o mover a EN_PREPARACION/EN_USO.
     * El solicitante (dueño de la reserva) o un TECNICO/ADMIN pueden cancelarla o
     * marcarla como DEVUELTA. El detalle fino de "quien puede pedir que transicion"
     * se refuerza ademas en el servicio (ver ReservaServiceImpl), que es la fuente
     * de verdad de la regla critica EN_USO <- requiere APROBADA previa.
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN', 'ESTUDIANTE', 'DOCENTE')")
    public ResponseEntity<ReservaResponseDTO> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {

        Long usuarioId = extraerUsuarioId(jwt);
        Set<String> roles = extraerRoles(jwt);

        ReservaResponseDTO actualizada = reservaService.cambiarEstado(id, request, usuarioId, roles);
        return ResponseEntity.ok(actualizada);
    }

    // ------------------------------------------------------------------
    // Helpers de extraccion de claims del JWT
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Set<String> extraerRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            // Fallback si el IdP entrega los roles como "authorities" o dentro de "realm_access.roles"
            roles = jwt.getClaimAsStringList("authorities");
        }
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .map(r -> r.replace("ROLE_", ""))
                .collect(Collectors.toSet());
    }

    /**
     * Azure AD no emite un id numerico de usuario: "sub"/"oid" son GUIDs, no
     * Long. Si el IdP manda un claim "userId" explicito (ej. otro IdP tipo
     * Keycloak configurado para ese caso) se usa tal cual; si no, se deriva
     * un id numerico estable a partir del subject (mismo usuario -> mismo id
     * siempre) en vez de intentar Long.valueOf(jwt.getSubject()), que
     * revienta con NumberFormatException contra un GUID real.
     */
    private Long extraerUsuarioId(Jwt jwt) {
        Object userId = jwt.getClaim("userId");
        if (userId != null) {
            try {
                return Long.valueOf(userId.toString());
            } catch (NumberFormatException ignored) {
                // cae al derivado desde el subject
            }
        }
        return (long) Math.abs(jwt.getSubject().hashCode());
    }
}
