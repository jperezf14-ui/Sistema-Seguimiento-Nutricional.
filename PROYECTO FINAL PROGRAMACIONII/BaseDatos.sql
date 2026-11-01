-- Universidad Mariano Galvez de Guatemala - Programacion II
-- Proyecto 1: Seguimiento nutricional y control de ayudas para ninos en riesgo
-- VERSION DE TRABAJO. MySQL 8.0.16+ / InnoDB / utf8mb4
-- EJECUTAR EN UNA BASE NUEVA. No borra bases de datos existentes.
CREATE DATABASE IF NOT EXISTS seguimiento_nutricional CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seguimiento_nutricional;

CREATE TABLE IF NOT EXISTS institucion (
 id_institucion INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo_institucional VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(150) NOT NULL, direccion VARCHAR(255) NOT NULL,
 telefono VARCHAR(20) NOT NULL, correo VARCHAR(150) NOT NULL,
 nombre_responsable VARCHAR(150) NOT NULL, logotipo MEDIUMBLOB NULL,
 comunidad_atendida VARCHAR(120), municipio_atendido VARCHAR(120),
 CONSTRAINT ck_institucion_zona CHECK (comunidad_atendida IS NOT NULL OR municipio_atendido IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rol (
 id_rol INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(50) NOT NULL UNIQUE,
 descripcion VARCHAR(255), activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS usuario (
 id_usuario INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_rol INT UNSIGNED NOT NULL,
 nombre_completo VARCHAR(150) NOT NULL,
 nombre_usuario VARCHAR(50) NOT NULL UNIQUE,
 correo VARCHAR(150) NOT NULL UNIQUE,
 telefono VARCHAR(20), clave_hash VARCHAR(255) NOT NULL,
 estado ENUM('Activo','Inactivo') NOT NULL DEFAULT 'Activo',
 fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES rol(id_rol) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS familia (
 id_familia INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(25) NOT NULL UNIQUE,
 nombre_responsable VARCHAR(150) NOT NULL,
 numero_identificacion VARCHAR(30) NOT NULL,
 direccion VARCHAR(255) NOT NULL,
 comunidad VARCHAR(120) NOT NULL, municipio VARCHAR(120) NOT NULL,
 departamento VARCHAR(120) NOT NULL, telefono VARCHAR(20),
 cantidad_integrantes SMALLINT UNSIGNED NOT NULL,
 condiciones_hogar TEXT, fecha_registro DATE NOT NULL,
 estado ENUM('Activa','Inactiva') NOT NULL DEFAULT 'Activa',
 observaciones TEXT,
 CONSTRAINT ck_familia_integrantes CHECK (cantidad_integrantes >= 1)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS nino (
 id_nino INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_familia INT UNSIGNED NOT NULL,
 codigo VARCHAR(25) NOT NULL UNIQUE,
 nombres VARCHAR(100) NOT NULL, apellidos VARCHAR(100) NOT NULL,
 fecha_nacimiento DATE NOT NULL, sexo VARCHAR(20) NOT NULL,
 lugar_nacimiento VARCHAR(150) NOT NULL,
 direccion VARCHAR(255) NOT NULL, comunidad VARCHAR(120) NOT NULL,
 numero_identificacion VARCHAR(30), fecha_ingreso DATE NOT NULL,
 estado_seguimiento ENUM('En evaluación','En seguimiento','Estable','Referido','Finalizado','Inactivo') NOT NULL DEFAULT 'En evaluación',
 observaciones TEXT,
 UNIQUE KEY uq_nino_familia (id_nino,id_familia),
 CONSTRAINT fk_nino_familia FOREIGN KEY (id_familia) REFERENCES familia(id_familia) ON DELETE RESTRICT ON UPDATE RESTRICT,
 INDEX idx_nino_comunidad(comunidad), INDEX idx_nino_estado(estado_seguimiento)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS control_nutricional (
 id_control INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_nino INT UNSIGNED NOT NULL,
 numero_control SMALLINT UNSIGNED NOT NULL,
 fecha_control DATE NOT NULL, edad_meses SMALLINT UNSIGNED NOT NULL,
 peso_kg DECIMAL(5,2) NOT NULL, talla_cm DECIMAL(5,2) NOT NULL,
 perimetro_braquial_cm DECIMAL(5,2),
 clasificacion_nutricional VARCHAR(80) NOT NULL,
 observaciones TEXT, id_profesional INT UNSIGNED NOT NULL,
 proximo_control DATE,
 CONSTRAINT ck_medidas CHECK (numero_control>0 AND peso_kg>0 AND talla_cm>0 AND (perimetro_braquial_cm IS NULL OR perimetro_braquial_cm>0)),
 CONSTRAINT ck_proximo_control CHECK (proximo_control IS NULL OR proximo_control>fecha_control),
 UNIQUE KEY uq_nino_num_control(id_nino,numero_control),
 INDEX idx_control_fecha(id_nino,fecha_control),
 INDEX idx_profesional_control(id_profesional,fecha_control),
 CONSTRAINT fk_control_nino FOREIGN KEY(id_nino) REFERENCES nino(id_nino) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_control_prof FOREIGN KEY(id_profesional) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS evaluacion_seguimiento (
 id_evaluacion INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_control INT UNSIGNED NOT NULL UNIQUE,
 fecha_evaluacion DATE NOT NULL, resultado_evaluacion TEXT NOT NULL,
 nivel_atencion VARCHAR(80) NOT NULL,
 recomendaciones TEXT NOT NULL, plan_seguimiento TEXT NOT NULL,
 frecuencia_controles VARCHAR(80) NOT NULL,
 id_responsable_seguimiento INT UNSIGNED NOT NULL,
 fecha_proxima_cita DATE,
 estado_seguimiento ENUM('En evaluación','En seguimiento','Estable','Referido','Finalizado','Inactivo') NOT NULL,
 observaciones TEXT,
 INDEX idx_eval_responsable_fecha(id_responsable_seguimiento,fecha_evaluacion),
 CONSTRAINT fk_eval_control FOREIGN KEY(id_control) REFERENCES control_nutricional(id_control) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_eval_responsable FOREIGN KEY(id_responsable_seguimiento) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cita (
 id_cita INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_nino INT UNSIGNED NOT NULL,
 fecha_cita DATE NOT NULL, hora_cita TIME NOT NULL,
 tipo_atencion VARCHAR(100) NOT NULL, id_profesional INT UNSIGNED NOT NULL,
 lugar_atencion VARCHAR(150) NOT NULL,
 estado ENUM('Programada','Atendida','Cancelada','Reprogramada','No asistió') NOT NULL DEFAULT 'Programada',
 observaciones TEXT, id_usuario_registra INT UNSIGNED NOT NULL,
 fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 -- SOLO los horarios programados deben ser unicos; MySQL permite multiples NULL en UNIQUE.
 id_profesional_ocupado INT UNSIGNED GENERATED ALWAYS AS (CASE WHEN estado='Programada' THEN id_profesional ELSE NULL END) STORED,
 UNIQUE KEY uq_profesional_horario(id_profesional_ocupado,fecha_cita,hora_cita),
 INDEX idx_cita_nino_fecha(id_nino,fecha_cita), INDEX idx_cita_estado_fecha(estado,fecha_cita),
 CONSTRAINT fk_cita_nino FOREIGN KEY(id_nino) REFERENCES nino(id_nino) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_cita_prof FOREIGN KEY(id_profesional) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_cita_registra FOREIGN KEY(id_usuario_registra) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS visita_comunitaria (
 id_visita INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_familia INT UNSIGNED NOT NULL,
 id_nino INT UNSIGNED NOT NULL,
 fecha_hora DATETIME NOT NULL, id_promotor INT UNSIGNED NOT NULL,
 motivo VARCHAR(255) NOT NULL, situacion_encontrada TEXT NOT NULL,
 acciones_realizadas TEXT NOT NULL, compromisos TEXT,
 proxima_visita DATE, observaciones TEXT,
 INDEX idx_visita_promotor_fecha(id_promotor,fecha_hora),
 CONSTRAINT fk_visita_familia FOREIGN KEY(id_familia) REFERENCES familia(id_familia) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_visita_nino_familia FOREIGN KEY(id_nino,id_familia) REFERENCES nino(id_nino,id_familia) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_visita_prom FOREIGN KEY(id_promotor) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS referencia (
 id_referencia INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 fecha_referencia DATE NOT NULL, id_nino INT UNSIGNED NOT NULL,
 institucion_destino VARCHAR(150) NOT NULL,
 tipo_servicio VARCHAR(120) NOT NULL, motivo TEXT NOT NULL,
 id_profesional INT UNSIGNED NOT NULL,
 estado ENUM('Emitida','Atendida','Pendiente','Cerrada') NOT NULL DEFAULT 'Emitida',
 resultado_seguimiento TEXT, observaciones TEXT,
 INDEX idx_referencia_estado_fecha(estado,fecha_referencia),
 CONSTRAINT fk_referencia_nino FOREIGN KEY(id_nino) REFERENCES nino(id_nino) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_referencia_prof FOREIGN KEY(id_profesional) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS producto_ayuda (
 id_producto INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(25) NOT NULL UNIQUE, nombre VARCHAR(150) NOT NULL,
 tipo ENUM('Alimento fortificado','Suplemento autorizado','Paquete nutricional','Material educativo','Artículo de higiene','Otro') NOT NULL,
 descripcion TEXT, unidad_medida VARCHAR(40) NOT NULL,
 estado ENUM('Activo','Inactivo') NOT NULL DEFAULT 'Activo', observaciones TEXT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lote_ayuda (
 id_lote INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_producto INT UNSIGNED NOT NULL,
 codigo_lote VARCHAR(50) NOT NULL, fecha_vencimiento DATE,
 fecha_ingreso DATE NOT NULL,
 existencia_actual DECIMAL(12,3) NOT NULL DEFAULT 0,
 estado ENUM('Activo','Bloqueado') NOT NULL DEFAULT 'Activo',
 UNIQUE KEY uq_producto_lote(id_producto,codigo_lote),
 INDEX idx_lote_vencimiento(estado,fecha_vencimiento),
 CONSTRAINT ck_lote_existencia CHECK(existencia_actual>=0),
 CONSTRAINT fk_lote_prod FOREIGN KEY(id_producto) REFERENCES producto_ayuda(id_producto) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS entrega_ayuda (
 id_entrega INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 numero_entrega VARCHAR(30) NOT NULL UNIQUE,
 id_nino INT UNSIGNED NOT NULL, id_familia INT UNSIGNED NOT NULL,
 fecha_entrega DATETIME NOT NULL, id_usuario_entrega INT UNSIGNED NOT NULL,
 motivo VARCHAR(255) NOT NULL,
 estado ENUM('Borrador','Confirmada','Anulada') NOT NULL DEFAULT 'Borrador',
 fecha_confirmacion DATETIME, observaciones TEXT,
 INDEX idx_entrega_nino_fecha(id_nino,fecha_entrega),
 INDEX idx_entrega_estado_fecha(estado,fecha_entrega),
 CONSTRAINT fk_entrega_nino_familia FOREIGN KEY(id_nino,id_familia) REFERENCES nino(id_nino,id_familia) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_entrega_familia FOREIGN KEY(id_familia) REFERENCES familia(id_familia) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_entrega_usuario FOREIGN KEY(id_usuario_entrega) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS detalle_entrega (
 id_detalle INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_entrega INT UNSIGNED NOT NULL,
 id_lote INT UNSIGNED NOT NULL, cantidad DECIMAL(12,3) NOT NULL,
 id_usuario_autoriza INT UNSIGNED, fecha_autorizacion DATETIME,
 observaciones TEXT,
 CONSTRAINT ck_detalle_cant CHECK(cantidad>0),
 UNIQUE KEY uq_entrega_lote(id_entrega,id_lote),
 CONSTRAINT fk_detalle_entrega FOREIGN KEY(id_entrega) REFERENCES entrega_ayuda(id_entrega) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_detalle_lote FOREIGN KEY(id_lote) REFERENCES lote_ayuda(id_lote) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_detalle_autoriza FOREIGN KEY(id_usuario_autoriza) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS movimiento_inventario (
 id_movimiento INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 id_lote INT UNSIGNED NOT NULL,
 id_detalle_entrega INT UNSIGNED UNIQUE,
 id_usuario INT UNSIGNED NOT NULL,
 fecha_movimiento DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 tipo_movimiento ENUM('Entrada','Salida','Ajuste positivo','Ajuste negativo') NOT NULL,
 cantidad DECIMAL(12,3) NOT NULL,
 motivo VARCHAR(255) NOT NULL, observaciones TEXT,
 CONSTRAINT ck_mov_cant CHECK(cantidad>0),
 INDEX idx_movimiento_lote_fecha(id_lote,fecha_movimiento),
 CONSTRAINT fk_mov_lote FOREIGN KEY(id_lote) REFERENCES lote_ayuda(id_lote) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_mov_detalle FOREIGN KEY(id_detalle_entrega) REFERENCES detalle_entrega(id_detalle) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_mov_usuario FOREIGN KEY(id_usuario) REFERENCES usuario(id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB;

-- Los controles historicos no deben eliminarse (no basta una FK a evaluaciones).
DROP TRIGGER IF EXISTS tr_no_delete_control;
DELIMITER $$
CREATE TRIGGER tr_no_delete_control BEFORE DELETE ON control_nutricional FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Los controles nutricionales historicos no se pueden eliminar';
END$$
DELIMITER ;

INSERT INTO rol(nombre,descripcion) VALUES
 ('Administrador','Gestiona usuarios, catalogos y datos generales'),
 ('Profesional de salud o nutrición','Registra controles y evaluaciones'),
 ('Promotor comunitario','Registra familias, visitas, citas y ayudas autorizadas'),
 ('Coordinador','Consulta estadisticas e informes')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion);

-- El primer administrador se crea desde la aplicacion, con contrasena cifrada.
