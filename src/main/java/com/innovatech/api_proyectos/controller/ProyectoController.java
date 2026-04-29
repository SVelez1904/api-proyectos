package com.innovatech.api_proyectos.controller;

import com.innovatech.api_proyectos.entity.Proyecto;
import com.innovatech.api_proyectos.repository.ProyectoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoController {

    @Autowired
    private ProyectoRepository proyectoRepository;

    // 1. Obtener todos los proyectos
    @GetMapping
    public List<Proyecto> listarTodos() {
        return proyectoRepository.findAll();
    }

    // 2. Crear un nuevo proyecto
    @PostMapping
    public Proyecto crear(@RequestBody Proyecto proyecto) {
        return proyectoRepository.save(proyecto);
    }

    // 3. Obtener un proyecto por ID
    @GetMapping("/{id}")
    public ResponseEntity<Proyecto> obtenerPorId(@PathVariable Long id) {
        return proyectoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
                    Proyecto actualizado = proyectoRepository.save(proyecto);
                    return ResponseEntity.ok(actualizado);
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
}