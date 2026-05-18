package com.innovatech.api_proyectos.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    private String prioridad;

    @Column(name = "progreso_porcentaje")
    private Integer progresoPorcentaje;

    // Relación con el microservicio de Usuarios
    @Column(name = "usuario_id")
    private Long usuarioId; // Ya es un Long de objeto, esto está excelente.

    // 🛡️ SOLUCIÓN AL ERROR 500: Le decimos a Jackson que este campo es SOLO LECTURA.
    // Así ignorará el campo "estadoCalculado" cuando React lo envíe en el POST/PUT.
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getEstadoCalculado() {
        if (progresoPorcentaje != null && progresoPorcentaje >= 100) {
            return "COMPLETADO";
        }

        LocalDate hoy = LocalDate.now();

        if (fechaEntrega != null && hoy.isAfter(fechaEntrega)) {
            return "ATRASADO";
        }

        if (fechaEntrega != null && hoy.plusDays(7).isAfter(fechaEntrega)) {
            return "CRÍTICO (Próximo a vencer)";
        }

        return "A TIEMPO";
    }

    // Relación One-to-Many con Tareas
    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks;

    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Asignacion> asignaciones;
}