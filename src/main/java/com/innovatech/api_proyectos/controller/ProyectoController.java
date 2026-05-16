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
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}) // 👈 ¡AÑADE ESTO!
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
            // Si el usuarioId viene vacío, aseguramos que viaje como null nativo en Hibernate
            if (proyecto.getUsuarioId() != null && proyecto.getUsuarioId() == 0) {
                proyecto.setUsuarioId(null);
            }
            Proyecto nuevoProyecto = proyectoService.guardarProyecto(proyecto);
            return ResponseEntity.ok(nuevoProyecto);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al crear el proyecto: " + e.getMessage());
        }
    }

    // 4. Actualizar un proyecto existente (Sincronizado completamente con tu JSON de React)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Proyecto detalles) {
        return proyectoRepository.findById(id)
                .map(proyectoExistente -> {
                    // 1. Actualizamos los datos propios del proyecto
                    proyectoExistente.setNombre(detalles.getNombre());
                    proyectoExistente.setDescripcion(detalles.getDescripcion());
                    proyectoExistente.setFechaInicio(detalles.getFechaInicio());
                    proyectoExistente.setFechaEntrega(detalles.getFechaEntrega());
                    proyectoExistente.setPrioridad(detalles.getPrioridad());
                    proyectoExistente.setProgresoPorcentaje(detalles.getProgresoPorcentaje());

                    // 🔥 CRÍTICO: Sincronizamos el usuarioId raíz modificado en el modal de React
                    if (detalles.getUsuarioId() != null && detalles.getUsuarioId() != 0) {
                        proyectoExistente.setUsuarioId(detalles.getUsuarioId());
                    } else {
                        proyectoExistente.setUsuarioId(null); // Si se desasignó el responsable principal
                    }

                    // 2. EVITAMOS SOBREESCRIBIR LAS ASIGNACIONES CON NULL
                    Proyecto proyectoActualizado = proyectoService.guardarProyecto(proyectoExistente);

                    return ResponseEntity.ok(proyectoActualizado);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. Eliminar un proyecto
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (proyectoRepository.existsById(id)) {
            proyectoRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // 6. Asignar un usuario traduciendo su rol (String) del microservicio a un ID numérico
    @PostMapping("/{proyectoId}/usuarios/{usuarioId}")
    public ResponseEntity<?> asignarUsuario(
            @PathVariable Long proyectoId,
            @PathVariable Long usuarioId) {

        return proyectoRepository.findById(proyectoId).map(proyecto -> {
            var usuario = proyectoService.obtenerDetallesUsuario(usuarioId);

            if (usuario.getUsername().contains("no disponible")) {
                return ResponseEntity.status(503).body("No se pudo validar el usuario. API Usuarios caída.");
            }

            // Solución ultra-segura: Leemos el campo directamente o usamos un fallback directo
            Long rolIdCalculado = 2L;

            try {
                // Esto evita problemas si Lombok se marea con los getters en el entorno de Docker
                if (usuario.getRole() != null && usuario.getRole().toUpperCase().contains("ADMIN")) {
                    rolIdCalculado = 1L;
                }
            } catch (Exception e) {
                // Si por alguna razón falla el mapeo, se queda con el rol de Developer (2L) de forma segura
                rolIdCalculado = 2L;
            }

            Asignacion nuevaAsignacion = new Asignacion();
            nuevaAsignacion.setUsuarioId(usuarioId);
            nuevaAsignacion.setRolId(rolIdCalculado);
            nuevaAsignacion.setProyecto(proyecto);

            proyecto.getAsignaciones().add(nuevaAsignacion);
            proyectoRepository.save(proyecto);

            return ResponseEntity.ok("Usuario " + usuario.getUsername() + " asignado con Rol ID: " + rolIdCalculado);
        }).orElse(ResponseEntity.notFound().build());
    }

    // 7. Cambiar el rol usando directamente el "rolId" que tú envíes en el JSON
    @PutMapping("/{proyectoId}/asignaciones/{asignacionId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> actualizarAsignacion(
            @PathVariable Long proyectoId,
            @PathVariable Long asignacionId,
            @RequestBody Map<String, Object> body) { // 👈 Cambiado a <String, Object> para soportar números y cadenas

        return asignacionRepository.findById(asignacionId).map(asignacionExistente -> {

            if (!asignacionExistente.getProyecto().getId().equals(proyectoId)) {
                return ResponseEntity.badRequest()
                        .body("Error: La asignación ID " + asignacionId + " no pertenece al proyecto " + proyectoId);
            }

            // Extraemos el "rolId" de forma segura del JSON de Postman/React
            if (!body.containsKey("rolId") || body.get("rolId") == null) {
                return ResponseEntity.badRequest().body("Error: El campo 'rolId' es obligatorio en el JSON.");
            }

            try {
                // Parseo genérico por si Jackson lo interpreta como Integer o String
                Long nuevoRolId = Long.parseLong(body.get("rolId").toString());

                // Aplicamos el cambio numérico directo sobre la entidad
                asignacionExistente.setRolId(nuevoRolId);

                // Opcional: Permitir también reasignar el id de usuario en la misma petición
                if (body.containsKey("usuarioId") && body.get("usuarioId") != null) {
                    asignacionExistente.setUsuarioId(Long.parseLong(body.get("usuarioId").toString()));
                }

                // Persistencia inmediata y limpieza forzada de la caché de Hibernate
                asignacionRepository.saveAndFlush(asignacionExistente);

                return ResponseEntity.ok("Sincronizado con éxito. La asignación " + asignacionId + " ahora tiene asignado el Rol ID: " + asignacionExistente.getRolId());

            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body("Error: El campo 'rolId' o 'usuarioId' debe ser un número válido.");
            }

        }).orElse(ResponseEntity.notFound().build());
    }
    //8 endpoint de eliminar asignacion
    @DeleteMapping("/{proyectoId}/asignaciones/{asignacionId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> desasignarUsuario(
            @PathVariable Long proyectoId,
            @PathVariable Long asignacionId) {

        return asignacionRepository.findById(asignacionId).map(asignacion -> {

            // Verificamos de forma segura que la asignación corresponda al proyecto enviado por URL
            if (!asignacion.getProyecto().getId().equals(proyectoId)) {
                return ResponseEntity.badRequest()
                        .body("Error de consistencia: La asignación no pertenece al proyecto especificado.");
            }

            // Removemos de la colección del proyecto para que Hibernate mantenga sincronizada la relación bidireccional
            Proyecto proyecto = asignacion.getProyecto();
            proyecto.getAsignaciones().remove(asignacion);

            // Eliminamos físicamente el registro de la tabla de asignaciones
            asignacionRepository.delete(asignacion);

            return ResponseEntity.ok().body("Usuario desasignado correctamente del proyecto.");
        }).orElse(ResponseEntity.notFound().build());
    }
}