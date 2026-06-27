package com.innovatech.api_proyectos.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProyectoTest {

    private Proyecto proyecto;

    @BeforeEach
    void setUp() {
        proyecto = new Proyecto();
    }

    @Test
    @DisplayName("Debería testear los Getters, Setters y Constructores para asegurar cobertura básica")
    void testGettersSettersYConstructores() {
        LocalDate inicio = LocalDate.now();
        LocalDate entrega = LocalDate.now().plusDays(10);
        List<Task> tasks = new ArrayList<>();
        List<Asignacion> asignaciones = new ArrayList<>();

        Proyecto completo = new Proyecto(1L, "Proyecto Alfa", "Descripción", inicio, entrega, "ALTA", 50, 99L, tasks, asignaciones);

        assertEquals(1L, completo.getId());
        assertEquals("Proyecto Alfa", completo.getNombre());
        assertEquals("Descripción", completo.getDescripcion());
        assertEquals(inicio, completo.getFechaInicio());
        assertEquals(entrega, completo.getFechaEntrega());
        assertEquals("ALTA", completo.getPrioridad());
        assertEquals(50, completo.getProgresoPorcentaje());
        assertEquals(99L, completo.getUsuarioId());
        assertNotNull(completo.getTasks());
        assertNotNull(completo.getAsignaciones());
    }

    @Test
    @DisplayName("getEstadoCalculado: Debería retornar COMPLETADO cuando el progreso es 100% o más")
    void testGetEstadoCalculadoCompletado() {
        proyecto.setProgresoPorcentaje(100);
        assertEquals("COMPLETADO", proyecto.getEstadoCalculado());

        proyecto.setProgresoPorcentaje(120);
        assertEquals("COMPLETADO", proyecto.getEstadoCalculado());
    }

    @Test
    @DisplayName("getEstadoCalculado: Debería retornar ATRASADO cuando la fecha de entrega ya pasó")
    void testGetEstadoCalculadoAtrasado() {
        proyecto.setProgresoPorcentaje(50);
        proyecto.setFechaEntrega(LocalDate.now().minusDays(1));

        assertEquals("ATRASADO", proyecto.getEstadoCalculado());
    }

    @Test
    @DisplayName("getEstadoCalculado: Debería retornar CRÍTICO cuando faltan menos de 7 días para la entrega")
    void testGetEstadoCalculadoCritico() {
        proyecto.setProgresoPorcentaje(20);
        // Colocamos la entrega en 3 días más (dentro del rango de la alerta de 7 días)
        proyecto.setFechaEntrega(LocalDate.now().plusDays(3));

        assertEquals("CRÍTICO (Próximo a vencer)", proyecto.getEstadoCalculado());
    }

    @Test
    @DisplayName("getEstadoCalculado: Debería retornar A TIEMPO cuando no está completado, atrasado ni crítico")
    void testGetEstadoCalculadoATiempo() {
        proyecto.setProgresoPorcentaje(0);
        // Más de una semana de margen
        proyecto.setFechaEntrega(LocalDate.now().plusDays(10));

        assertEquals("A TIEMPO", proyecto.getEstadoCalculado());
    }

    @Test
    @DisplayName("actualizarProgresoSegunTareas: Debería ser 0% si la lista de tareas está vacía o es nula")
    void testActualizarProgresoTareasVacias() {
        proyecto.setTasks(new ArrayList<>());
        proyecto.actualizarProgresoSegunTareas();
        assertEquals(0, proyecto.getProgresoPorcentaje());

        proyecto.setTasks(null);
        proyecto.actualizarProgresoSegunTareas();
        assertEquals(0, proyecto.getProgresoPorcentaje());
    }

    @Test
    @DisplayName("actualizarProgresoSegunTareas: Debería calcular y redondear correctamente el porcentaje basado en tareas completadas")
    void testActualizarProgresoCalculoCorrecto() {
        // Mock de tareas de manera simplificada
        Task t1 = new Task();
        t1.setEstado("Completada");

        Task t2 = new Task();
        t2.setEstado("En Progreso");

        Task t3 = new Task();
        t3.setEstado("Pendiente");

        List<Task> listaTareas = new ArrayList<>();
        listaTareas.add(t1);
        listaTareas.add(t2);
        listaTareas.add(t3);

        proyecto.setTasks(listaTareas);

        // 1 de 3 tareas completadas = 33.333% -> Redondeado a 33
        proyecto.actualizarProgresoSegunTareas();
        assertEquals(33, proyecto.getProgresoPorcentaje());

        // Agregamos otra completada: 2 de 4 = 50%
        Task t4 = new Task();
        t4.setEstado("COMPLETADA"); // Probamos que ignore mayúsculas/minúsculas
        listaTareas.add(t4);

        proyecto.actualizarProgresoSegunTareas();
        assertEquals(50, proyecto.getProgresoPorcentaje());
    }
}