package com.innovatech.api_proyectos.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
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
    private Long usuarioId; // Solo guardamos el ID

    @Transient // transient indica que se calcula acá y no en la base de datos
    public String getEstadoCalculado() {
        if (progresoPorcentaje != null && progresoPorcentaje >= 100) { // esto indica que el proyecto está terminado
            return "COMPLETADO";
        }

        LocalDate hoy = LocalDate.now(); // esto inicializa la fecha de hoy para caluclar los atrasos

        if (fechaEntrega != null && hoy.isAfter(fechaEntrega)) { // si el dia de hoy es mayor a la entrega está atrasado
            return "ATRASADO";
        }

        if (fechaEntrega != null && hoy.plusDays(7).isAfter(fechaEntrega)) { // esto es para alertar de que queda una semana
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