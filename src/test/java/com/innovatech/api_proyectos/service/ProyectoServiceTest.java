package com.innovatech.api_proyectos.service;

import com.innovatech.api_proyectos.client.UsuarioFeignClient;
import com.innovatech.api_proyectos.dto.ProyectoEvent;
import com.innovatech.api_proyectos.dto.UsuarioDTO;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProyectoServiceTest {

    @Mock //Con MOCK vamos a simular eventos para poder probar las funcionalidades en lugar de hacer llamadas reales a la DB o a otra API
    private UsuarioFeignClient usuarioFeignClient;

    @Mock //Se simula la llamada a la base de datos
    private ProyectoRepository proyectoRepository;

    @Mock //Se simula tamboién el kafka
    private KafkaTemplate<String, ProyectoEvent> kafkaTemplate;

    @InjectMocks
    private ProyectoService proyectoService;

    // --- PRUEBAS PARA CAPA RESILIENCIA (FEIGN / CIRCUIT BREAKER) ---

    @Test
    @DisplayName("Debe retornar los datos del usuario cuando Feign responde exitosamente")
    void obtenerDetallesUsuario_HappyEnding() {
        // Arrange
        Long usuarioId = 1L; // inicializo la variable de id para tenber datos con los cuales trabajar
        UsuarioDTO mockUsuario = new UsuarioDTO(usuarioId, "sebastian_v", "seba@innovatech.com", "ROLE_DEVELOPER"); //genero un usuario para simulación
        when(usuarioFeignClient.getUsuarioById(usuarioId)).thenReturn(mockUsuario);

        // Act
        UsuarioDTO resultado = proyectoService.obtenerDetallesUsuario(usuarioId);

        // Assert
        assertNotNull(resultado);
        assertEquals("sebastian_v", resultado.getUsername());
        verify(usuarioFeignClient, times(1)).getUsuarioById(usuarioId);
    }

    @Test
    @DisplayName("Debe retornar el DTO de fallback cuando el método fallback es invocado por falla")
    void fallbackGetUsuario_DebeRetornarUsuarioTemporal() {
        // Arrange
        Long usuarioId = 99L;
        Throwable excepcionSimulada = new RuntimeException("Error de red de Feign");

        // Act (Llamamos directamente al método de fallback para testear su lógica interna)
        UsuarioDTO resultado = proyectoService.fallbackGetUsuario(usuarioId, excepcionSimulada);

        // Assert
        assertNotNull(resultado);
        assertEquals(usuarioId, resultado.getId());
        assertEquals("Servicio temporalmente no disponible", resultado.getUsername());
        assertEquals("N/A", resultado.getEmail());
    }


    // --- PRUEBAS PARA PERSISTENCIA BASE ---

    @Test
    @DisplayName("Debe guardar y hacer flush del proyecto en el repositorio")
    void guardarProyecto_DebePersistirExitosamente() {
        // Arrange
        Proyecto proyectoInput = new Proyecto();
        proyectoInput.setNombre("Innovatech Portafolio");

        when(proyectoRepository.saveAndFlush(proyectoInput)).thenReturn(proyectoInput);

        // Act
        Proyecto resultado = proyectoService.guardarProyecto(proyectoInput);

        // Assert
        assertNotNull(resultado);
        verify(proyectoRepository, times(1)).saveAndFlush(proyectoInput);
    }


    // --- PRUEBAS PARA INTERACCIÓN CON KAFKA ---

    @Test
    @DisplayName("Debe asignar usuario, consolidar en DB y despachar el evento UPDATE a Kafka exitosamente")
    void asignarUsuarioYNotificarKafka_CaminoFeliz() { // declaración del test, será void pq no devolvera nada
        // Arrange
        Proyecto proyectoModificado = new Proyecto();
        proyectoModificado.setId(10L);
        proyectoModificado.setNombre("GranuFactory E-commerce");
        proyectoModificado.setProgresoPorcentaje(45);
        proyectoModificado.setUsuarioId(1L);
        // Simulamos que el método interno de tu Entity devuelve "EN_PROGRESO"
        // Nota: Asegúrate de que tu entidad maneje getEstadoCalculado() o mockealo si es necesario.

        when(proyectoRepository.saveAndFlush(any(Proyecto.class))).thenReturn(proyectoModificado);

        // Mockear la naturaleza asíncrona de KafkaTemplate.send()
        // Retornamos un CompletableFuture ya completado con éxito para que entre al bloque .whenComplete sin arrojar error
        CompletableFuture<SendResult<String, ProyectoEvent>> futureExitoso = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("proyectos-topic"), any(ProyectoEvent.class))).thenReturn(futureExitoso);

        // Act
        Proyecto resultado = proyectoService.asignarUsuarioYNotificarKafka(proyectoModificado);

        // Assert
        assertNotNull(resultado);
        assertEquals(10L, resultado.getId());

        // Verificamos que se haya enviado a Kafka con los datos mapeados
        verify(proyectoRepository, times(1)).saveAndFlush(any(Proyecto.class));
        verify(kafkaTemplate, times(1)).send(eq("proyectos-topic"), any(ProyectoEvent.class));
    }

    @Test
    @DisplayName("Debe enviar el evento DELETE a Kafka al notificar una eliminación")
    void notificarEliminacionKafka_DebeEnviarEventoDelete() {
        // Arrange
        Long proyectoId = 25L;

        // Simulamos un CompletableFuture completado exitosamente
        CompletableFuture<SendResult<String, ProyectoEvent>> futureExitoso = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("proyectos-topic"), any(ProyectoEvent.class))).thenReturn(futureExitoso);

        // Act
        proyectoService.notificarEliminacionKafka(proyectoId);

        // Assert
        // Verificamos que se construyó y envió el evento correcto con la acción "DELETE"
        verify(kafkaTemplate, times(1)).send(eq("proyectos-topic"), argThat(evento ->
                evento.getId().equals(proyectoId) &&
                        "DELETE".equals(evento.getAction()) &&
                        "ELIMINADO".equals(evento.getEstado())
        ));
    }



    @Test
    @DisplayName("Debe actualizar el progreso basado en tareas y despachar el evento a Kafka exitosamente")
    void actualizarProgresoYNotificarKafka_CaminoFeliz() {
        // Arrange
        Long proyectoId = 10L;
        Proyecto proyectoMock = new Proyecto();
        proyectoMock.setId(proyectoId);
        proyectoMock.setNombre("GranuFactory E-commerce");
        proyectoMock.setProgresoPorcentaje(0); // Inicia en cero
        proyectoMock.setUsuarioId(1L);
        proyectoMock.setTasks(new ArrayList<>()); // Lista vacía para que calcule

        // Simulamos que findById lo encuentra y que saveAndFlush guarda el cambio
        when(proyectoRepository.findById(proyectoId)).thenReturn(java.util.Optional.of(proyectoMock));
        when(proyectoRepository.saveAndFlush(any(Proyecto.class))).thenReturn(proyectoMock);

        CompletableFuture<SendResult<String, ProyectoEvent>> futureExitoso = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("proyectos-topic"), any(ProyectoEvent.class))).thenReturn(futureExitoso);

        // Act
        Proyecto resultado = proyectoService.actualizarProgresoYNotificarKafka(proyectoId);

        // Assert
        assertNotNull(resultado);
        verify(proyectoRepository, times(1)).findById(proyectoId);
        verify(proyectoRepository, times(1)).saveAndFlush(any(Proyecto.class));
        verify(kafkaTemplate, times(1)).send(eq("proyectos-topic"), any(ProyectoEvent.class));
    }

    @Test
    @DisplayName("Debe lanzar RuntimeException si el proyecto no existe al intentar actualizar progreso")
    void actualizarProgresoYNotificarKafka_ProyectoNoEncontrado() {
        // Arrange
        Long proyectoId = 999L;
        when(proyectoRepository.findById(proyectoId)).thenReturn(java.util.Optional.empty());

        // Act & Assert
        RuntimeException excepcion = assertThrows(RuntimeException.class, () -> {
            proyectoService.actualizarProgresoYNotificarKafka(proyectoId);
        });

        assertEquals("Proyecto no encontrado con ID: " + proyectoId, excepcion.getMessage());
        verify(proyectoRepository, times(1)).findById(proyectoId);
        verify(proyectoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Debe cubrir la rama de error de la lambda en despacharUpdateKafka cuando falla el envío")
    void despacharUpdateKafka_RamaErrorLambda() {
        // Arrange
        Proyecto proyectoModificado = new Proyecto();
        proyectoModificado.setId(10L);
        proyectoModificado.setUsuarioId(1L);

        when(proyectoRepository.saveAndFlush(any(Proyecto.class))).thenReturn(proyectoModificado);

        // Simulamos que el CompletableFuture falla de forma asíncrona
        CompletableFuture<SendResult<String, ProyectoEvent>> futureFallido = new CompletableFuture<>();
        futureFallido.completeExceptionally(new RuntimeException("Kafka Broker Down"));

        when(kafkaTemplate.send(eq("proyectos-topic"), any(ProyectoEvent.class))).thenReturn(futureFallido);

        // Act
        // Ejecutamos a través de asignarUsuarioYNotificarKafka para disparar despacharUpdateKafka de forma indirecta
        Proyecto resultado = proyectoService.asignarUsuarioYNotificarKafka(proyectoModificado);

        // Assert
        assertNotNull(resultado);
        // La ejecución no se detiene porque el callback es asíncrono, pero se ejecuta el System.err.println interno
        verify(kafkaTemplate, times(1)).send(eq("proyectos-topic"), any(ProyectoEvent.class));
    }

    @Test
    @DisplayName("Debe cubrir la rama de error de la lambda en notificarEliminacionKafka cuando falla el envío")
    void notificarEliminacionKafka_RamaErrorLambda() {
        // Arrange
        Long proyectoId = 25L;

        // Simulamos que el CompletableFuture del DELETE falla de forma asíncrona
        CompletableFuture<SendResult<String, ProyectoEvent>> futureFallido = new CompletableFuture<>();
        futureFallido.completeExceptionally(new RuntimeException("Kafka Network Timeout"));

        when(kafkaTemplate.send(eq("proyectos-topic"), any(ProyectoEvent.class))).thenReturn(futureFallido);

        // Act
        proyectoService.notificarEliminacionKafka(proyectoId);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("proyectos-topic"), any(ProyectoEvent.class));
    }

}