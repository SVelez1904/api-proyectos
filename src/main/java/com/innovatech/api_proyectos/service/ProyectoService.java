package com.innovatech.api_proyectos.service;

import com.innovatech.api_proyectos.client.UsuarioFeignClient;
import com.innovatech.api_proyectos.dto.ProyectoEvent;
import com.innovatech.api_proyectos.dto.UsuarioDTO;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProyectoService {

    private final UsuarioFeignClient usuarioFeignClient;
    private final KafkaTemplate<String, ProyectoEvent> kafkaTemplate;
    private final ProyectoRepository proyectoRepository; // Inyectado vía constructor

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "fallbackGetUsuario")
    public UsuarioDTO obtenerDetallesUsuario(Long usuarioId) {
        return usuarioFeignClient.getUsuarioById(usuarioId);
    }

    public UsuarioDTO fallbackGetUsuario(Long usuarioId, Throwable t) {
        // Imprimir el error real en los logs de Docker ayudará a saber por qué saltó el Circuit Breaker
        System.err.println("🚨 Circuit Breaker activado para usuario ID " + usuarioId + ". Causa: " + t.getMessage());

        // Le agregamos el cuarto parámetro exigido por el nuevo constructor de Lombok ("N/A" para el rol)
        return new UsuarioDTO(usuarioId, "Servicio temporalmente no disponible", "N/A", "N/A");
    }

    @Transactional
    public Proyecto guardarProyecto(Proyecto p) {
        // 1. Guardar en la base de datos local
        Proyecto proyectoGuardado = proyectoRepository.save(p);
        System.out.println("Proyecto guardado en DB, intentando enviar a Kafka...");

        // 2. Preparar el evento para Analytics
        ProyectoEvent evento = new ProyectoEvent(
                proyectoGuardado.getId(),
                proyectoGuardado.getNombre(),
                proyectoGuardado.getProgresoPorcentaje(),
                proyectoGuardado.getEstadoCalculado(),
                "UPDATE"
        );

        // 3. Enviar a Kafka de forma asíncrona
        kafkaTemplate.send("proyectos-topic", evento).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("FATAL: No se pudo enviar a Kafka: " + ex.getMessage());
            } else {
                System.out.println("EXITO: Mensaje en tópico proyectos-topic, offset: " + result.getRecordMetadata().offset());
            }
        });

        // 4. ¡IMPORTANTE! Retornar el objeto guardado
        return proyectoGuardado;
    }
}