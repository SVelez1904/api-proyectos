package com.innovatech.api_proyectos.service; // Cambiado o verificado según tu package actual

import com.innovatech.api_proyectos.entity.Asignacion;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.AsignacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UsuarioEliminadoListener {

    private final AsignacionRepository asignacionRepository;
    private final ProyectoService proyectoService;

    // 🔥 ADAPTADO: Forzamos el StringDeserializer para evitar conflictos con el JSON global de Analytics
    @KafkaListener(
            topics = "usuarios-events",
            groupId = "proyectos-cleanup-group",
            properties = {
                    "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    @Transactional
    public void procesarUsuarioEliminado(String usuarioIdStr) {
        try {
            Long usuarioId = Long.parseLong(usuarioIdStr.trim()); // trim() por si viaja algún espacio oculto
            System.out.println("📥 Kafka: Detectada eliminación de Usuario ID: " + usuarioId);

            // 1. Buscamos todas las relaciones de este usuario en los proyectos
            List<Asignacion> asignacionesAfectadas = asignacionRepository.findByUsuarioId(usuarioId);

            if (asignacionesAfectadas.isEmpty()) {
                System.out.println("ℹ️ El usuario con ID " + usuarioId + " no tenía asignaciones activas.");
                return;
            }

            // 2. Eliminamos los registros huérfanos de la tabla intermedia
            for (Asignacion asignacion : asignacionesAfectadas) {
                Proyecto proyecto = asignacion.getProyecto();
                proyecto.getAsignaciones().remove(asignacion);
                asignacionRepository.delete(asignacion);

                // 3. Sincronizamos con Analytics pasando el nuevo estado del proyecto
                if (proyecto.getAsignaciones().isEmpty()) {
                    proyectoService.notificarEliminacionKafka(proyecto.getId());
                } else {
                    proyectoService.asignarUsuarioYNotificarKafka(proyecto);
                }
            }

            asignacionRepository.flush();
            System.out.println("✨ Éxito: Base de datos limpia y Analytics actualizado.");

        } catch (Exception e) {
            System.err.println("❌ Error procesando limpieza de usuario: " + e.getMessage());
        }
    }
}