package com.innovatech.api_proyectos.service;

import com.innovatech.api_proyectos.client.UsuarioFeignClient;
import com.innovatech.api_proyectos.dto.ProyectoEvent;
import com.innovatech.api_proyectos.dto.UsuarioDTO;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProyectoService {

    private final UsuarioFeignClient usuarioFeignClient;
    private final KafkaTemplate<String, ProyectoEvent> kafkaTemplate;
    private final ProyectoRepository proyectoRepository;

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "fallbackGetUsuario")
    public UsuarioDTO obtenerDetallesUsuario(Long usuarioId) {
        return usuarioFeignClient.getUsuarioById(usuarioId);
    }

    public UsuarioDTO fallbackGetUsuario(Long usuarioId, Throwable t) {
        System.err.println("🚨 Circuit Breaker activado para usuario ID " + usuarioId + ". Causa: " + t.getMessage());
        return new UsuarioDTO(usuarioId, "Servicio temporalmente no disponible", "N/A", "N/A");
    }


     // 1. GUARDAR PROYECTO BASE
    @Transactional
    public Proyecto guardarProyecto(Proyecto p) {
        return proyectoRepository.saveAndFlush(p);
    }


    @Transactional
    public Proyecto asignarUsuarioYNotificarKafka(Proyecto proyectoModificado) {
        // 1. Forzamos el guardado y confirmamos los cambios físicos en PostgreSQL de inmediato
        Proyecto proyectoActualizado = proyectoRepository.saveAndFlush(proyectoModificado);
        System.out.println("Asignación e hijo consolidados en DB (Flush). Preparando despacho a Kafka...");
        List<Long> idsAsignados = (proyectoActualizado.getAsignaciones() != null)
                ? proyectoActualizado.getAsignaciones().stream()
                .map(asignacion -> asignacion.getUsuarioId())
                .collect(Collectors.toCollection(ArrayList::new))
                : new ArrayList<>();

        if (idsAsignados.isEmpty() && proyectoActualizado.getUsuarioId() != null) {
            idsAsignados.add(proyectoActualizado.getUsuarioId());
        }

        System.out.println("📦 IDs de usuarios reales recolectados para Kafka: " + idsAsignados);

        // 3. Preparamos el evento DTO con los 6 parámetros requeridos
        ProyectoEvent evento = new ProyectoEvent(
                proyectoActualizado.getId(),
                proyectoActualizado.getNombre(),
                proyectoActualizado.getProgresoPorcentaje(),
                proyectoActualizado.getEstadoCalculado(),
                "UPDATE",
                idsAsignados
        );

        // 4. Enviar al tópico de Kafka de manera asíncrona
        kafkaTemplate.send("proyectos-topic", evento).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("FATAL: No se pudo enviar a Kafka: " + ex.getMessage());
            } else {
                System.out.println("EXITO: Mensaje en tópico proyectos-topic, offset: " + result.getRecordMetadata().offset());
            }
        });

        return proyectoActualizado;
    }

    //3. NOTIFICAR ELIMINACIÓN TOTAL O PARCIAL A KAFKA (DELETE)
    @Transactional
    public void notificarEliminacionKafka(Long proyectoId) {
        System.out.println("Alerta de eliminación detectada para proyecto ID: " + proyectoId + ". Notificando a Kafka...");

        // Enviamos una lista mutable vacía (ArrayList) para máxima compatibilidad con Jackson
        ProyectoEvent evento = new ProyectoEvent(
                proyectoId,
                "Proyecto Eliminado",
                0,
                "ELIMINADO",
                "DELETE",
                new ArrayList<>()
        );

        kafkaTemplate.send("proyectos-topic", evento).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("FATAL: No se pudo enviar el DELETE a Kafka: " + ex.getMessage());
            } else {
                System.out.println("EXITO: Evento DELETE enviado a proyectos-topic");
            }
        });
    }
}