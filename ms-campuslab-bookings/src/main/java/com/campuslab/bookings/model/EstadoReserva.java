package com.campuslab.bookings.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados posibles del ciclo de vida de una reserva (booking) de laboratorio/equipo.
 *
 * Maquina de estados:
 *
 *   SOLICITADA ──► APROBADA ──► EN_PREPARACION ──► EN_USO ──► DEVUELTA
 *       │              │               │
 *       └──────────────┴───────────────┴──────► CANCELADA
 *
 * Reglas:
 * - Solo se puede llegar a EN_USO habiendo pasado por EN_PREPARACION (que a su vez
 *   requiere haber pasado por APROBADA). Este enum define la topologia general;
 *   la regla de negocio "aprobado por tecnico/admin" se valida en la capa de servicio
 *   porque requiere el contexto de seguridad (quien y cuando aprobo), no solo el estado.
 * - DEVUELTA y CANCELADA son estados terminales (no permiten mas transiciones).
 */
public enum EstadoReserva {

    SOLICITADA,
    APROBADA,
    EN_PREPARACION,
    EN_USO,
    DEVUELTA,
    CANCELADA;

    private static final Map<EstadoReserva, Set<EstadoReserva>> TRANSICIONES_VALIDAS = new EnumMap<>(EstadoReserva.class);

    static {
        TRANSICIONES_VALIDAS.put(SOLICITADA, EnumSet.of(APROBADA, CANCELADA));
        TRANSICIONES_VALIDAS.put(APROBADA, EnumSet.of(EN_PREPARACION, CANCELADA));
        TRANSICIONES_VALIDAS.put(EN_PREPARACION, EnumSet.of(EN_USO, CANCELADA));
        TRANSICIONES_VALIDAS.put(EN_USO, EnumSet.of(DEVUELTA));
        TRANSICIONES_VALIDAS.put(DEVUELTA, EnumSet.noneOf(EstadoReserva.class));
        TRANSICIONES_VALIDAS.put(CANCELADA, EnumSet.noneOf(EstadoReserva.class));
    }

    /**
     * Indica si es valido pasar de "this" estado al estado "destino" segun la topologia
     * de la maquina de estados (sin considerar aun reglas de negocio adicionales,
     * como aprobacion previa por rol).
     */
    public boolean puedeTransicionarA(EstadoReserva destino) {
        return TRANSICIONES_VALIDAS.getOrDefault(this, Set.of()).contains(destino);
    }

    public boolean esEstadoTerminal() {
        return this == DEVUELTA || this == CANCELADA;
    }
}
