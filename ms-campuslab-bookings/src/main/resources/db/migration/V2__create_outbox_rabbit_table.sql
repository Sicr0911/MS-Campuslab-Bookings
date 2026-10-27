-- ============================================================
-- V2__create_outbox_rabbit_table.sql
-- Outbox transaccional de los comandos que bookings publica a RabbitMQ
-- (email / ticket / voucher). El mensaje se guarda en la MISMA transaccion
-- que el cambio de estado de la reserva y OutboxRelay lo envia despues con
-- confirmacion del broker: si RabbitMQ esta caido, el mensaje espera aqui
-- en vez de perderse.
-- Motor: Oracle Database
-- ============================================================

CREATE SEQUENCE OUTBOX_RABBIT_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

CREATE TABLE OUTBOX_RABBIT (
    ID              NUMBER(19)      DEFAULT OUTBOX_RABBIT_SEQ.NEXTVAL NOT NULL,
    EVENT_ID        VARCHAR2(64)    NOT NULL,
    TIPO            VARCHAR2(40)    NOT NULL,
    EXCHANGE_NAME   VARCHAR2(120)   NOT NULL,
    ROUTING_KEY     VARCHAR2(120)   NOT NULL,
    CUERPO          CLOB            NOT NULL,
    ESTADO          VARCHAR2(20)    NOT NULL,
    INTENTOS        NUMBER(10)      DEFAULT 0 NOT NULL,
    ULTIMO_ERROR    VARCHAR2(500),
    CREADO_EN       TIMESTAMP       NOT NULL,
    ENVIADO_EN      TIMESTAMP,
    CONSTRAINT PK_OUTBOX_RABBIT PRIMARY KEY (ID),
    CONSTRAINT UQ_OUTBOX_RABBIT_EVENT_ID UNIQUE (EVENT_ID),
    CONSTRAINT CK_OUTBOX_RABBIT_ESTADO CHECK (ESTADO IN ('PENDIENTE', 'ENVIADO', 'FALLIDO'))
);

CREATE INDEX IX_OUTBOX_RABBIT_ESTADO ON OUTBOX_RABBIT (ESTADO, ID);

COMMENT ON TABLE OUTBOX_RABBIT IS 'Comandos para notify pendientes/enviados a RabbitMQ (patron outbox)';
