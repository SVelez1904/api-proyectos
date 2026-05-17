package com.innovatech.api_proyectos.controller;

import com.innovatech.api_proyectos.entity.Asignacion;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import com.innovatech.api_proyectos.repository.AsignacionRepository;
import com.innovatech.api_proyectos.service.ProyectoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/proyectos")
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
public class ProyectoController {

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private AsignacionRepository asignacionRepository;

    @Autowired
    private ProyectoService proyectoService;

    // 1. Obtener todos los proyectos
    @GetMapping
    public List<Proyecto> listarTodos() {
        return proyectoRepository.findAll();
    }

    // 3. Obtener un proyecto por ID
    @GetMapping("/{id}")
    public ResponseEntity<Proyecto> obtenerPorId(@PathVariable Long id) {
        return proyectoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 2. Crear un nuevo proyecto
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Proyecto proyecto) {
        try {
            if (proyecto.getUsuarioId() != null && proyecto.getUsuarioId() == 0) {
                proyecto.setUsuarioId(null);
            }
            Proyecto nuevoProyecto = proyectoService.guardarProyecto(proyecto);
            return ResponseEntity.ok(nuevoProyecto);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al crear el proyecto: " + e.getMessage());
        }
    }

    // 4. Actualizar un proyecto existente
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Proyecto detalles) {
        return proyectoRepository.findById(id)
                .map(proyectoExistente -> {
                    proyectoExistente.setNombre(detalles.getNombre());
                    proyectoExistente.setDescripcion(detalles.getDescripcion());
                    proyectoExistente.setFechaInicio(detalles.getFechaInicio());
                    proyectoExistente.setFechaEntrega(detalles.getFechaEntrega());
                    proyectoExistente.setPrioridad(detalles.getPrioridad());
                    proyectoExistente.setProgresoPorcentaje(detalles.getProgresoPorcentaje());

                    if (detalles.getUsuarioId() != null && detalles.getUsuarioId() != 0) {
                        proyectoExistente.setUsuarioId(detalles.getUsuarioId());
                    } else {
                        proyectoExistente.setUsuarioId(null);
                    }

                    Proyecto proyectoActualizado = proyectoService.guardarProyecto(proyectoExistente);
                    return ResponseEntity.ok(proyectoActualizado);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. Eliminar un proyecto
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (proyectoRepository.existsById(id)) {
            // 🔥 Primero notificamos a Kafka antes de destruir el registro para tener el ID de referencia limpio
            proyectoService.notificarEliminacionKafka(id);

            // Ahora sí lo borramos de la BD local
            proyectoRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // 6. 🔥 ENDPOINT ADAPTADO: Asignar un usuario notificando correctamente a Kafka
    @PostMapping("/{proyectoId}/usuarios/{usuarioId}")
    public ResponseEntity<?> asignarUsuario(
            @PathVariable Long proyectoId,
            @PathVariable Long usuarioId) {

        return proyectoRepository.findById(proyectoId).map(proyecto -> {
            var usuario = proyectoService.obtenerDetallesUsuario(usuarioId);

            if (usuario.getUsername().contains("no disponible")) {
                return ResponseEntity.status(503).body("No se pudo validar el usuario. API Usuarios caída.");
            }

            Long rolIdCalculado = 2L;
            try {
                if (usuario.getRole() != null && usuario.getRole().toUpperCase().contains("ADMIN")) {
                    rolIdCalculado = 1L;
                }
            } catch (Exception e) {
                rolIdCalculado = 2L;
            }

            // Instanciamos el registro relacional
            Asignacion nuevaAsignacion = new Asignacion();
            nuevaAsignacion.setUsuarioId(usuarioId);
            nuevaAsignacion.setRolId(rolIdCalculado);
            nuevaAsignacion.setProyecto(proyecto);

            // Lo vinculamos a la lista del objeto persistente
            proyecto.getAsignaciones().add(nuevaAsignacion);

            // 🔥 DELEGAMOS AL SERVICIO: El método se encarga de persistir con Flush y despachar a Kafka de forma segura
            proyectoService.asignarUsuarioYNotificarKafka(proyecto);

            return ResponseEntity.ok("Usuario " + usuario.getUsername() + " asignado con Rol ID: " + rolIdCalculado + " y notificado a Kafka.");
        }).orElse(ResponseEntity.notFound().build());
    }

    // 7. Cambiar el rol e informar la actualización de métricas
    @PutMapping("/{proyectoId}/asignaciones/{asignacionId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> actualizarAsignacion(
            @PathVariable Long proyectoId,
            @PathVariable Long asignacionId,
            @RequestBody Map<String, Object> body) {

        return asignacionRepository.findById(asignacionId).map(asignacionExistente -> {

            if (!asignacionExistente.getProyecto().getId().equals(proyectoId)) {
                return ResponseEntity.badRequest()
                        .body("Error: La asignación ID " + asignacionId + " no pertenece al proyecto " + proyectoId);
            }

            if (!body.containsKey("rolId") || body.get("rolId") == null) {
                return ResponseEntity.badRequest().body("Error: El campo 'rolId' es obligatorio en el JSON.");
            }

            try {
                Long nuevoRolId = Long.parseLong(body.get("rolId").toString());
                asignacionExistente.setRolId(nuevoRolId);

                if (body.containsKey("usuarioId") && body.get("usuarioId") != null) {
                    asignacionExistente.setUsuarioId(Long.parseLong(body.get("usuarioId").toString()));
                }

                asignacionRepository.saveAndFlush(asignacionExistente);

                // 🔥 ALERTAMOS A KAFKA: Al modificarse un rol/usuario interno de la asignación, forzamos la actualización en analíticas
                proyectoService.asignarUsuarioYNotificarKafka(asignacionExistente.getProyecto());

                return ResponseEntity.ok("Sincronizado con éxito. La asignación " + asignacionId + " ahora tiene asignado el Rol ID: " + asignacionExistente.getRolId());

            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body("Error: El campo 'rolId' o 'usuarioId' debe ser un número válido.");
            }

        }).orElse(ResponseEntity.notFound().build());
    }

    // 8. Endpoint de eliminar asignación sincronizado con Analytics
    @DeleteMapping("/{proyectoId}/asignaciones/{asignacionId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> desasignarUsuario(
            @PathVariable Long proyectoId,
            @PathVariable Long asignacionId) {

        return asignacionRepository.findById(asignacionId).map(asignacion -> {

            if (!asignacion.getProyecto().getId().equals(proyectoId)) {
                return ResponseEntity.badRequest()
                        .body("Error de consistency: La asignación no pertenece al proyecto especificado.");
            }

            Proyecto proyecto = asignacion.getProyecto();

            // Removemos de la colección bidireccional
            proyecto.getAsignaciones().remove(asignacion);
            asignacionRepository.delete(asignacion);
            asignacionRepository.flush();

            // 🔥 Si tras borrar esta asignación el proyecto se quedó sin NINGÚN usuario,
            // mandamos un "DELETE" a Analytics para que limpie la tabla intermedia de inmediato.
            if (proyecto.getAsignaciones().isEmpty()) {
                proyectoService.notificarEliminacionKafka(proyectoId);
            } else {
                // Si aún quedan otros usuarios trabajando en el proyecto, mandamos un update normal con los que quedan
                proyectoService.asignarUsuarioYNotificarKafka(proyecto);
            }

            return ResponseEntity.ok().body("Usuario desasignado correctamente del proyecto.");
        }).orElse(ResponseEntity.notFound().build());
    }
}