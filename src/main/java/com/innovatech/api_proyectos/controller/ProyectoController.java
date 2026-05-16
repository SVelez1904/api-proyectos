package com.innovatech.api_proyectos.controller;

import com.innovatech.api_proyectos.entity.Asignacion;
import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import com.innovatech.api_proyectos.service.ProyectoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proyectos")
public class ProyectoController {

    @Autowired
    private ProyectoRepository proyectoRepository;

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
    public Proyecto crear(@RequestBody Proyecto proyecto) {
        // CAMBIO: Usar el service en lugar del repository
        return proyectoService.guardarProyecto(proyecto);
    }

    // 4. Actualizar un proyecto existente
    @PutMapping("/{id}")
    public ResponseEntity<Proyecto> actualizar(@PathVariable Long id, @RequestBody Proyecto detalles) {
        return proyectoRepository.findById(id)
                .map(proyecto -> {
                    proyecto.setNombre(detalles.getNombre());
                    proyecto.setDescripcion(detalles.getDescripcion());
                    proyecto.setFechaInicio(detalles.getFechaInicio());
                    proyecto.setPrioridad(detalles.getPrioridad());
                    proyecto.setProgresoPorcentaje(detalles.getProgresoPorcentaje());

                    // CAMBIO: Aquí también deberías usar el service si quieres que
                    // Analytics se entere de las actualizaciones.
                    return ResponseEntity.ok(proyectoService.guardarProyecto(proyecto));
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

    // 6. Asignar un usuario a un proyecto con un rol específico
    @PostMapping("/{proyectoId}/usuarios/{usuarioId}")
    public ResponseEntity<?> asignarUsuario(
            @PathVariable Long proyectoId,
            @PathVariable Long usuarioId,
            @RequestParam String rol) {

        return proyectoRepository.findById(proyectoId).map(proyecto -> {
            // VALIDACIÓN: Usamos Feign + Circuit Breaker para ver si el usuario existe
            var usuario = proyectoService.obtenerDetallesUsuario(usuarioId);

            if (usuario.getUsername().contains("no disponible")) {
                return ResponseEntity.status(503).body("No se pudo validar el usuario. API Usuarios caída.");
            }

            Asignacion nuevaAsignacion = new Asignacion();
            nuevaAsignacion.setUsuarioId(usuarioId);
            nuevaAsignacion.setRol(rol.toUpperCase());
            nuevaAsignacion.setProyecto(proyecto);

            proyecto.getAsignaciones().add(nuevaAsignacion);
            proyectoRepository.save(proyecto);

            return ResponseEntity.ok("Usuario " + usuario.getUsername() + " asignado como " + rol);
        }).orElse(ResponseEntity.notFound().build());
    }

    // 7. Cambiar el rol o el usuario de una asignación específica
    @PutMapping("/{proyectoId}/asignaciones/{asignacionId}")
    public ResponseEntity<?> actualizarAsignacion(
            @PathVariable Long proyectoId,
            @PathVariable Long asignacionId,
            @RequestBody Asignacion detalles) {

        // Aquí buscarías la asignación específica y actualizarías su usuarioId o Rol
        // Es útil para cuando quieres "cambiar" al Developer de un proyecto
        return ResponseEntity.ok("Asignación actualizada");
    }
}