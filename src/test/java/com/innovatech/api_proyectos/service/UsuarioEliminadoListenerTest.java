package com.innovatech.api_proyectos.service;

import com.innovatech.api_proyectos.entity.Asignacion;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.AsignacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioEliminadoListenerTest {

    @Mock
    private AsignacionRepository asignacionRepository;

    @Mock
    private ProyectoService proyectoService;

    @InjectMocks
    private UsuarioEliminadoListener usuarioEliminadoListener;

    private Proyecto proyectoMock;
    private Asignacion asignacionMock;
    private List<Asignacion> listaAsignaciones;

    @BeforeEach
    void setUp() {
        // Inicializamos objetos de prueba limpios antes de cada test
        proyectoMock = new Proyecto();
        proyectoMock.setId(10L);
        proyectoMock.setAsignaciones(new ArrayList<>()); // Inicializado para evitar NullPointerException

        asignacionMock = new Asignacion();
        asignacionMock.setId(1L);
        asignacionMock.setProyecto(proyectoMock);

        // Agregamos la asignación a la lista simulada del proyecto
        proyectoMock.getAsignaciones().add(asignacionMock);

        listaAsignaciones = new ArrayList<>();
        listaAsignaciones.add(asignacionMock);
    }

    @Test
    @DisplayName("Debería procesar eliminación correctamente y notificar eliminación completa si el proyecto queda vacío")
    void testProcesarUsuarioEliminado_ProyectoQuedaVacio() {
        // GIVEN
        String usuarioIdStr = " 99 "; // Con espacios intencionales para verificar el .trim()
        Long usuarioId = 99L;

        when(asignacionRepository.findByUsuarioId(usuarioId)).thenReturn(listaAsignaciones);

        // WHEN
        usuarioEliminadoListener.procesarUsuarioEliminado(usuarioIdStr);

        // THEN
        // Verificamos que se removió de la lista bidireccional y se eliminó del repositorio
        verify(asignacionRepository, times(1)).delete(asignacionMock);

        // Como el proyecto quedó con 0 asignaciones tras el remove, debe llamar a notificarEliminacionKafka
        verify(proyectoService, times(1)).notificarEliminacionKafka(10L);
        verify(proyectoService, never()).asignarUsuarioYNotificarKafka(any());
        verify(asignacionRepository, times(1)).flush();
    }

    @Test
    @DisplayName("Debería notificar actualización si el proyecto aún conserva otras asignaciones activas")
    void testProcesarUsuarioEliminado_ProyectoAunTieneAsignaciones() {
        // GIVEN
        String usuarioIdStr = "99";
        Long usuarioId = 99L;

        // Añadimos una SEGUNDA asignación extra al proyecto para que no quede vacío tras borrar la primera
        Asignacion otraAsignacion = new Asignacion();
        otraAsignacion.setId(2L);
        otraAsignacion.setProyecto(proyectoMock);
        proyectoMock.getAsignaciones().add(otraAsignacion);

        when(asignacionRepository.findByUsuarioId(usuarioId)).thenReturn(listaAsignaciones);

        // WHEN
        usuarioEliminadoListener.procesarUsuarioEliminado(usuarioIdStr);

        // THEN
        verify(asignacionRepository, times(1)).delete(asignacionMock);
        // Al quedar todavía 'otraAsignacion' en la lista, ejecuta el flujo del 'else'
        verify(proyectoService, times(1)).asignarUsuarioYNotificarKafka(proyectoMock);
        verify(proyectoService, never()).notificarEliminacionKafka(anyLong());
        verify(asignacionRepository, times(1)).flush();
    }

    @Test
    @DisplayName("Debería salir rápido de la función si el usuario no tenía ninguna asignación registrada")
    void testProcesarUsuarioEliminado_SinAsignaciones() {
        // GIVEN
        String usuarioIdStr = "88";
        when(asignacionRepository.findByUsuarioId(88L)).thenReturn(Collections.emptyList());

        // WHEN
        usuarioEliminadoListener.procesarUsuarioEliminado(usuarioIdStr);

        // THEN
        // No debería interactuar con eliminaciones, flushes ni llamados al service de proyectos
        verify(asignacionRepository, never()).delete(any());
        verify(proyectoService, never()).notificarEliminacionKafka(anyLong());
        verify(proyectoService, never()).asignarUsuarioYNotificarKafka(any());
        verify(asignacionRepository, never()).flush();
    }

    @Test
    @DisplayName("Debería capturar cualquier excepción (ej. ID inválido) en el bloque try-catch sin romper la ejecución")
    void testProcesarUsuarioEliminado_ManejoDeExcepciones() {
        // GIVEN
        String idInvalido = "ABC-Fallo-Parseo"; // Esto provocará un NumberFormatException en el Long.parseLong()

        // WHEN & THEN
        // Ejecutamos esperando que el try-catch interno del Listener absorba el error tal como fue diseñado
        assertDoesNotThrow(() -> usuarioEliminadoListener.procesarUsuarioEliminado(idInvalido));

        // Validamos que por el error nunca se llegó a interactuar con la base de datos
        verifyNoInteractions(asignacionRepository);
        verifyNoInteractions(proyectoService);
    }
}