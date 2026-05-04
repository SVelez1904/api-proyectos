package com.innovatech.api_proyectos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "proyectos")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    private String prioridad; // Sugerencia: LOW, MEDIUM, HIGH

    @Column(name = "progreso_porcentaje")
    private Integer progresoPorcentaje;

    // Relación con el microservicio de Usuarios
    @Column(name = "usuario_id")
    private Long usuarioId; // Solo guardamos el ID

    // Relación One-to-Many con Tareas
    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks;

    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asignacion> asignaciones;
}