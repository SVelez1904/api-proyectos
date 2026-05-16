package com.innovatech.api_proyectos.repository;


import com.innovatech.api_proyectos.entity.Asignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {
    // Spring Data JPA generará automáticamente toda la implementación de base aquí (findById, save, delete, etc.)
}