package com.innovatech.api_proyectos.dto;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProyectoEvent {
    private Long id;
    private String nombre;
    private Integer progresoPorcentaje;
    private String estado;
    private String action;
    private List<Long> usuarioIds;
}