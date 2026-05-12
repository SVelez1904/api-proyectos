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

    // Aquí puedes incluir el estado calculado para que el Frontend lo muestre
    private String estado;

    // El ID del usuario responsable
    private Long usuarioId;

    // Opcional: Si quieres enviar los datos del usuario ya completos en la misma respuesta
    private UsuarioDTO usuarioDetalles;
}