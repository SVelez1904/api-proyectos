package com.innovatech.api_proyectos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "proyecto")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "proyectos_gen")
    @SequenceGenerator(name = "proyectos_gen", sequenceName = "seq_proyectos", allocationSize = 1)
    private Long id;

    private String nombre;
    private String descripcion;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    private String prioridad;

    @Column(name = "progreso_porcentaje")
    private Integer progresoPorcentaje;

    // Getters y Setters
}