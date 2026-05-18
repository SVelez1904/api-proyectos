package com.innovatech.api_proyectos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProyectoDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private LocalDate fechaInicio;
    private LocalDate fechaEntrega;
    private String prioridad;
    private Integer progresoPorcentaje;
    private String estado;
    private Long usuarioId;
    private UsuarioDTO usuarioDetalles;
}