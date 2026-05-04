package com.innovatech.api_proyectos.service;

import com.innovatech.api_proyectos.client.UsuarioFeignClient;
import com.innovatech.api_proyectos.dto.UsuarioDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProyectoService {

    private final UsuarioFeignClient usuarioFeignClient;

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "fallbackGetUsuario")
    public UsuarioDTO obtenerDetallesUsuario(Long usuarioId) {
        return usuarioFeignClient.getUsuarioById(usuarioId);
    }

    // Método de respaldo (Fallback) si la API de Usuarios falla
    public UsuarioDTO fallbackGetUsuario(Long usuarioId, Throwable t) {
        System.err.println("Causa del fallo en Circuit Breaker: " + t.getMessage());
        t.printStackTrace();
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(usuarioId);
        dto.setUsername("Usuario no disponible (Servicio en mantenimiento)");
        dto.setEmail("N/A");
        return dto;
    }
}
