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
        // Loggear el error es fundamental para monitoreo
        return new UsuarioDTO(usuarioId, "Servicio temporalmente no disponible", "N/A");
    }

    @Transactional
    public void guardarProyecto(Proyecto p) {
        Proyecto proyectoGuardado = proyectoRepository.save(p);

        // Creamos el evento usando los nombres correctos de la entidad
        ProyectoEvent evento = new ProyectoEvent(
                proyectoGuardado.getId(),
                proyectoGuardado.getNombre(),
                proyectoGuardado.getProgresoPorcentaje(),
                proyectoGuardado.getEstadoCalculado(),
                "UPDATE"
        );

        kafkaTemplate.send("proyectos-topic", evento);
    }
}