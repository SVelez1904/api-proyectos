package com.innovatech.api_proyectos.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*; // 👈 Asegura que la importación de Lombok esté presente

@Entity
@Getter @Setter              // 👈 ¡FALTABA ESTO! Genera los getters y setters (setRolId, setUsuarioId, etc.)
@NoArgsConstructor           // 👈 ¡FALTABA ESTO! Necesario para que JPA pueda instanciar la entidad
@AllArgsConstructor          // 👈 ¡FALTABA ESTO! Genera el constructor con todos los campos
@Table(name = "asignaciones")
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId; // ID en api-usuarios

    private Long rolId;     // ID del rol (1 = ADMIN, 2 = DEVELOPER)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id")
    @JsonBackReference
    private Proyecto proyecto;
}