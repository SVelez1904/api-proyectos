package com.innovatech.api_proyectos.controller;

import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.AsignacionRepository;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import com.innovatech.api_proyectos.service.ProyectoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProyectoController.class)
class ProyectoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Satisfacemos todas las dependencias del controlador usando @MockitoBean
    @MockitoBean
    private ProyectoService proyectoService;

    @MockitoBean
    private ProyectoRepository proyectoRepository;

    @MockitoBean
    private AsignacionRepository asignacionRepository;

    @Test
    @DisplayName("Debe devolver 404 cuando el proyecto buscado por ID no existe en la Base de Datos")
    void obtenerPorId_CuandoNoExiste_DebeDevolver404() throws Exception {
        // Arrange
        Long proyectoId = 99L;

        // Simulamos el comportamiento del repositorio real que usa el endpoint
        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.empty());

        // Act & Assert
        // Nota: Tu @RequestMapping base es "/proyectos", no "/api/proyectos" 👈 ¡Ojo aquí!
        mockMvc.perform(get("/proyectos/" + proyectoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // Verifica que devuelva el 404 Not Found
    }

    @Test
    @DisplayName("Debe devolver 503 cuando la API de Usuarios está caída o no disponible")
    void asignarUsuario_CuandoApiUsuariosCaida_DebeDevolver503() throws Exception {
        // Arrange
        Long proyectoId = 1L;
        Long usuarioId = 5L;

        // Simbolizamos que el proyecto sí existe en la base de datos
        Proyecto proyectoFalso = new Proyecto();
        proyectoFalso.setId(proyectoId);
        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyectoFalso));

        // Simulamos que el servicio responde con el usuario mockeado como "no disponible"
        com.innovatech.api_proyectos.dto.UsuarioDTO usuarioCaido = new com.innovatech.api_proyectos.dto.UsuarioDTO();
        usuarioCaido.setUsername("Servicio no disponible temporalmente");
        when(proyectoService.obtenerDetallesUsuario(usuarioId)).thenReturn(usuarioCaido);

        // Act & Assert
        mockMvc.perform(post("/proyectos/" + proyectoId + "/usuarios/" + usuarioId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable()); // 👈 Verifica que responda HTTP 503
    }

    @Test
    @DisplayName("Debe devolver 400 Bad Request cuando el rolId en el JSON no es un número válido")
    void actualizarAsignacion_CuandoRolIdNoEsNumero_DebeDevolver400() throws Exception {
        // Arrange
        Long proyectoId = 1L;
        Long asignacionId = 10L;

        // Simulamos que la asignación existe para que pase el primer filtro
        com.innovatech.api_proyectos.entity.Asignacion asignacionFalsa = new com.innovatech.api_proyectos.entity.Asignacion();
        Proyecto proyectoFalso = new Proyecto();
        proyectoFalso.setId(proyectoId);
        asignacionFalsa.setProyecto(proyectoFalso);

        when(asignacionRepository.findById(asignacionId)).thenReturn(Optional.of(asignacionFalsa));

        // Creamos el JSON malicioso con una cadena de texto en lugar de un número
        String jsonMalformado = "{\"rolId\": \"TEXTO_INVALIDO\", \"usuarioId\": 5}";

        // Act & Assert
        mockMvc.perform(put("/proyectos/" + proyectoId + "/asignaciones/" + asignacionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMalformado))
                .andExpect(status().isBadRequest()); // 👈 Verifica que capture el NumberFormatException y responda 400
    }

}