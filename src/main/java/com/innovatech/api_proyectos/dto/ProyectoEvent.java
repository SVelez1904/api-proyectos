package com.innovatech.api_proyectos.dto;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor // <--- ESTA ES LA CLAVE: Genera el constructor para los 5 parámetros
@NoArgsConstructor  // <--- NECESARIA: Para que Kafka pueda deserializar el objeto
public class ProyectoEvent {
    private Long id;
    private String nombre;
    private Integer progresoPorcentaje;
    private String estado;
    private String tipoEvento;
}