-- ============================================================
-- V1__create_reservas_table.sql
-- Tabla principal del microservicio ms-campuslab-bookings
-- Motor: Oracle Database
-- ============================================================

CREATE SEQUENCE RESERVAS_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

CREATE TABLE RESERVAS (
    ID                      NUMBER(19)      DEFAULT RESERVAS_SEQ.NEXTVAL NOT NULL,
    RECURSO_ID              NUMBER(19)      NOT NULL,
    USUARIO_SOLICITANTE_ID  NUMBER(19)      NOT NULL,
    ESTADO                  VARCHAR2(20)    NOT NULL,
    FECHA_INICIO            TIMESTAMP       NOT NULL,
    FECHA_FIN               TIMESTAMP       NOT NULL,
    OBSERVACIONES           VARCHAR2(500),
    APROBADO_POR            NUMBER(19),
    FECHA_APROBACION        TIMESTAMP,
    MOTIVO_CANCELACION      VARCHAR2(500),
    FECHA_CREACION          TIMESTAMP       NOT NULL,
    FECHA_ACTUALIZACION     TIMESTAMP,
    CONSTRAINT PK_RESERVAS PRIMARY KEY (ID),
    CONSTRAINT CK_RESERVAS_ESTADO CHECK (ESTADO IN (
        'SOLICITADA', 'APROBADA', 'EN_PREPARACION', 'EN_USO', 'DEVUELTA', 'CANCELADA'
    )),
    CONSTRAINT CK_RESERVAS_FECHAS CHECK (FECHA_FIN > FECHA_INICIO),
    -- Refuerzo a nivel de BD de la regla critica: si el estado es EN_USO,
    -- debe existir constancia de aprobacion (defensa en profundidad ademas
    -- de la validacion en la capa de servicio).
    CONSTRAINT CK_RESERVAS_EN_USO_REQ_APROB CHECK (
        ESTADO != 'EN_USO' OR (APROBADO_POR IS NOT NULL AND FECHA_APROBACION IS NOT NULL)
    )
);

-- Indices para los filtros mas frecuentes del listado (GET /api/bookings)
CREATE INDEX IX_RESERVAS_ESTADO           ON RESERVAS (ESTADO);
CREATE INDEX IX_RESERVAS_RECURSO_ID       ON RESERVAS (RECURSO_ID);
CREATE INDEX IX_RESERVAS_USUARIO_SOLIC    ON RESERVAS (USUARIO_SOLICITANTE_ID);
CREATE INDEX IX_RESERVAS_FECHAS           ON RESERVAS (FECHA_INICIO, FECHA_FIN);

COMMENT ON TABLE RESERVAS IS 'Reservas de laboratorios/equipos del sistema CampusLab';
COMMENT ON COLUMN RESERVAS.ESTADO IS 'Maquina de estados: SOLICITADA, APROBADA, EN_PREPARACION, EN_USO, DEVUELTA, CANCELADA';
COMMENT ON COLUMN RESERVAS.APROBADO_POR IS 'ID del usuario (tecnico/admin) que aprobo la reserva; NULL si aun no ha sido aprobada';
