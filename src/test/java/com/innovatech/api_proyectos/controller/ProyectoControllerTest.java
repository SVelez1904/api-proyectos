package com.innovatech.api_proyectos.controller;

import com.innovatech.api_proyectos.entity.Asignacion;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.entity.Task;
import com.innovatech.api_proyectos.dto.UsuarioDTO;
import com.innovatech.api_proyectos.repository.AsignacionRepository;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import com.innovatech.api_proyectos.service.ProyectoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProyectoController.class)
class ProyectoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Para serializar objetos a JSON de forma limpia

    @MockitoBean
    private ProyectoService proyectoService;

    @MockitoBean
    private ProyectoRepository proyectoRepository;

    @MockitoBean
    private AsignacionRepository asignacionRepository;

    // --- TESTS EXISTENTES REFACTORIZADOS/MANTENIDOS ---

    @Test
    @DisplayName("Debe devolver 404 cuando el proyecto buscado por ID no existe")
    void obtenerPorId_CuandoNoExiste_DebeDevolver404() throws Exception {
        Long proyectoId = 99L;
        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/proyectos/" + proyectoId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Debe devolver 503 cuando la API de Usuarios está caída")
    void asignarUsuario_CuandoApiUsuariosCaida_DebeDevolver503() throws Exception {
        Long proyectoId = 1L;
        Long usuarioId = 5L;

        Proyecto proyectoFalso = new Proyecto();
        proyectoFalso.setId(proyectoId);
        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyectoFalso));

        UsuarioDTO usuarioCaido = new UsuarioDTO();
        usuarioCaido.setUsername("Servicio no disponible temporalmente");
        when(proyectoService.obtenerDetallesUsuario(usuarioId)).thenReturn(usuarioCaido);

        mockMvc.perform(post("/proyectos/" + proyectoId + "/usuarios/" + usuarioId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Debe devolver 400 Bad Request cuando el rolId en el JSON no es válido")
    void actualizarAsignacion_CuandoRolIdNoEsNumero_DebeDevolver400() throws Exception {
        Long proyectoId = 1L;
        Long asignacionId = 10L;

        Asignacion asignacionFalsa = new Asignacion();
        Proyecto proyectoFalso = new Proyecto();
        proyectoFalso.setId(proyectoId);
        asignacionFalsa.setProyecto(proyectoFalso);

        when(asignacionRepository.findById(asignacionId)).thenReturn(Optional.of(asignacionFalsa));

        String jsonMalformado = "{\"rolId\": \"TEXTO_INVALIDO\", \"usuarioId\": 5}";

        mockMvc.perform(put("/proyectos/" + proyectoId + "/asignaciones/" + asignacionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMalformado))
                .andExpect(status().isBadRequest());
    }


    @Test
    @DisplayName("Debe listar todos los proyectos con éxito")
    void listarTodos_DebeDevolverListaDeProyectos() throws Exception {
        Proyecto p1 = new Proyecto();
        p1.setId(1L);
        p1.setNombre("Proyecto Alfa");

        Proyecto p2 = new Proyecto();
        p2.setId(2L);
        p2.setNombre("Proyecto Beta");

        when(proyectoRepository.findAll()).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/proyectos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Proyecto Alfa"))
                .andExpect(jsonPath("$[1].nombre").value("Proyecto Beta"));
    }

    @Test
    @DisplayName("Debe crear un proyecto exitosamente y normalizar usuarioId si viene en 0")
    void crear_CuandoProyectoEsValido_DebeRetornar200YProyectoGuardado() throws Exception {
        Proyecto proyectoInput = new Proyecto();
        proyectoInput.setNombre("Nuevo Proyecto");
        proyectoInput.setUsuarioId(0L); // Caso frontera en tu endpoint

        Proyecto proyectoPersistido = new Proyecto();
        proyectoPersistido.setId(100L);
        proyectoPersistido.setNombre("Nuevo Proyecto");
        proyectoPersistido.setUsuarioId(null); // Verificamos que pase a null

        when(proyectoService.guardarProyecto(any(Proyecto.class))).thenReturn(proyectoPersistido);

        mockMvc.perform(post("/proyectos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proyectoInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.usuarioId").isEmpty());
    }

    @Test
    @DisplayName("Debe retornar 500 cuando ocurre una excepción al crear un proyecto")
    void crear_CuandoOcurreExcepcion_DebeRetornar500() throws Exception {
        Proyecto proyectoInput = new Proyecto();
        when(proyectoService.guardarProyecto(any(Proyecto.class))).thenThrow(new RuntimeException("Database down"));

        mockMvc.perform(post("/proyectos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proyectoInput)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Error al crear el proyecto")));
    }

    @Test
    @DisplayName("Debe eliminar un proyecto, notificar a Kafka y retornar 204")
    void eliminar_CuandoExiste_DebeNotificarKafkaYBorrar() throws Exception {
        Long proyectoId = 1L;
        when(proyectoRepository.existsById(proyectoId)).thenReturn(true);

        mockMvc.perform(delete("/proyectos/" + proyectoId))
                .andExpect(status().isNoContent());

        verify(proyectoService, times(1)).notificarEliminacionKafka(proyectoId);
        verify(proyectoRepository, times(1)).deleteById(proyectoId);
    }

    @Test
    @DisplayName("Debe asignar un usuario con Rol ADMIN (Rol ID 1) si el DTO contiene ADMIN")
    void asignarUsuario_CuandoEsAdmin_DebeAsignarRolIdUno() throws Exception {
        Long proyectoId = 1L;
        Long usuarioId = 2L;

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setAsignaciones(new ArrayList<>());

        UsuarioDTO usuarioAdmin = new UsuarioDTO();
        usuarioAdmin.setUsername("sebav")	;
        usuarioAdmin.setRole("ROLE_ADMIN"); // Forzará el bloque condicional

        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(proyectoService.obtenerDetallesUsuario(usuarioId)).thenReturn(usuarioAdmin);

        mockMvc.perform(post("/proyectos/" + proyectoId + "/usuarios/" + usuarioId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("asignado con Rol ID: 1")));

        verify(proyectoService, times(1)).asignarUsuarioYNotificarKafka(proyecto);
    }

    @Test
    @DisplayName("Debe crear una tarea dentro de un proyecto, forzar flush y actualizar progreso en Kafka")
    void crearTarea_CuandoProyectoExiste_DebeRetornar201YTarea() throws Exception {
        Long proyectoId = 1L;
        Task nuevaTarea = new Task();
        nuevaTarea.setTitulo("Diseñar DB");

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setTasks(new ArrayList<>());

        // Al guardar el proyecto, simulamos que se le asigna el ID a la tarea en la colección
        Proyecto proyectoGuardado = new Proyecto();
        proyectoGuardado.setId(proyectoId);
        Task tareaGuardada = new Task();
        tareaGuardada.setId(500L);
        tareaGuardada.setTitulo("Diseñar DB");
        tareaGuardada.setEstado("Pendiente");
        proyectoGuardado.setTasks(List.of(tareaGuardada));

        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(proyectoRepository.saveAndFlush(any(Proyecto.class))).thenReturn(proyectoGuardado);

        mockMvc.perform(post("/proyectos/" + proyectoId + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevaTarea)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500L))
                .andExpect(jsonPath("$.estado").value("Pendiente"));

        verify(proyectoService, times(1)).actualizarProgresoYNotificarKafka(proyectoId);
    }


    @Test
    @DisplayName("Debe actualizar un proyecto existente y setear usuarioId en null si viene en 0")
    void actualizar_CuandoProyectoExiste_DebeActualizarYRetornar200() throws Exception {
        Long proyectoId = 1L;
        Proyecto detallesInput = new Proyecto();
        detallesInput.setNombre("Nombre Editado");
        detallesInput.setUsuarioId(0L); // Forzamos el else de tu controlador

        Proyecto proyectoExistente = new Proyecto();
        proyectoExistente.setId(proyectoId);
        proyectoExistente.setNombre("Nombre Viejo");

        Proyecto proyectoActualizado = new Proyecto();
        proyectoActualizado.setId(proyectoId);
        proyectoActualizado.setNombre("Nombre Editado");
        proyectoActualizado.setUsuarioId(null);

        // Mockeamos el flujo del map del Optional
        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyectoExistente));
        when(proyectoService.guardarProyecto(any(Proyecto.class))).thenReturn(proyectoActualizado);

        mockMvc.perform(put("/proyectos/" + proyectoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(detallesInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Editado"))
                .andExpect(jsonPath("$.usuarioId").isEmpty());
    }

    @Test
    @DisplayName("Debe retornar 404 en actualizar proyecto si el ID no existe")
    void actualizar_CuandoProyectoNoExiste_DebeRetornar404() throws Exception {
        Long proyectoId = 99L;
        Proyecto detallesInput = new Proyecto();

        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/proyectos/" + proyectoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(detallesInput)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Debe actualizar una tarea exitosamente dentro de un proyecto")
    void actualizarTarea_CuandoExiste_DebeActualizarYRetornar200() throws Exception {
        Long proyectoId = 1L;
        Long taskId = 5L;

        Task detallesTarea = new Task();
        detallesTarea.setTitulo("Titulo Nuevo");
        detallesTarea.setEstado("Completado");

        Task tareaExistente = new Task();
        tareaExistente.setId(taskId);
        tareaExistente.setTitulo("Titulo Viejo");
        tareaExistente.setEstado("Pendiente");

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        // Agregamos la tarea a la lista mutable para que el .stream().filter() la encuentre
        proyecto.setTasks(new java.util.ArrayList<>(List.of(tareaExistente)));

        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        mockMvc.perform(put("/proyectos/" + proyectoId + "/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(detallesTarea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Titulo Nuevo"))
                .andExpect(jsonPath("$.estado").value("Completado"));

        verify(proyectoRepository, times(1)).saveAndFlush(proyecto);
        verify(proyectoService, times(1)).actualizarProgresoYNotificarKafka(proyectoId);
    }

    @Test
    @DisplayName("Debe retornar 404 si la tarea no pertenece al proyecto al intentar actualizar")
    void actualizarTarea_CuandoTareaNoExisteEnProyecto_DebeRetornar404() throws Exception {
        Long proyectoId = 1L;
        Long taskId = 99L; // ID que no va a estar en la lista
        Task detallesTarea = new Task();

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setTasks(new java.util.ArrayList<>()); // Lista vacía

        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        mockMvc.perform(put("/proyectos/" + proyectoId + "/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(detallesTarea)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Tarea no encontrada en este proyecto"));
    }

    @Test
    @DisplayName("Debe eliminar una tarea correctamente y actualizar barra en Kafka")
    void eliminarTarea_CuandoExiste_DebeRetornar200() throws Exception {
        Long proyectoId = 1L;
        Long taskId = 5L;

        Task tarea = new Task();
        tarea.setId(taskId);

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setTasks(new java.util.ArrayList<>(List.of(tarea)));

        when(proyectoRepository.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        mockMvc.perform(delete("/proyectos/" + proyectoId + "/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(content().string("Tarea eliminada correctamente"));

        verify(proyectoRepository, times(1)).saveAndFlush(proyecto);
        verify(proyectoService, times(1)).actualizarProgresoYNotificarKafka(proyectoId);
    }

    @Test
    @DisplayName("Debe retornar 400 en desasignarUsuario si el proyectoId no coincide con el de la asignación")
    void desasignarUsuario_CuandoInconsistenciaDeProyecto_DebeRetornar400() throws Exception {
        Long proyectoIdIdInput = 1L;
        Long asignacionId = 10L;

        Proyecto proyectoRealDeLaAsignacion = new Proyecto();
        proyectoRealDeLaAsignacion.setId(2L); // 👈 ID diferente (2 != 1) provocará la inconsistencia

        Asignacion asignacion = new Asignacion();
        asignacion.setId(asignacionId);
        asignacion.setProyecto(proyectoRealDeLaAsignacion);

        when(asignacionRepository.findById(asignacionId)).thenReturn(Optional.of(asignacion));

        mockMvc.perform(delete("/proyectos/" + proyectoIdIdInput + "/asignaciones/" + asignacionId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Error de consistency")));
    }

    @Test
    @DisplayName("Debe desasignar usuario, borrar asignación y notificar DELETE a Kafka si el proyecto se queda vacío")
    void desasignarUsuario_CuandoProyectoQuedaVacio_DebeNotificarEliminacionKafka() throws Exception {
        Long proyectoId = 1L;
        Long asignacionId = 10L;

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);

        Asignacion asignacion = new Asignacion();
        asignacion.setId(asignacionId);
        asignacion.setProyecto(proyecto);

        // El proyecto inicialmente tiene la asignación que vamos a borrar
        proyecto.setAsignaciones(new java.util.ArrayList<>(List.of(asignacion)));

        when(asignacionRepository.findById(asignacionId)).thenReturn(Optional.of(asignacion));

        mockMvc.perform(delete("/proyectos/" + proyectoId + "/asignaciones/" + asignacionId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Usuario desasignado correctamente")));

        // Verificaciones críticas de tu lógica de negocio
        verify(asignacionRepository, times(1)).delete(asignacion);
        verify(proyectoService, times(1)).notificarEliminacionKafka(proyectoId); // Se quedó vacío, va DELETE a Kafka
    }
}