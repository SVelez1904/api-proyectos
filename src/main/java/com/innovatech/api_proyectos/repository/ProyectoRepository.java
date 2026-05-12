package com.innovatech.api_proyectos.repository;

import com.innovatech.api_proyectos.entity.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
    // Listar proyectos donde la fecha de entrega ya pasó y no están al 100%
    @Query("SELECT p FROM Proyecto p WHERE p.fechaEntrega < CURRENT_DATE AND p.progresoPorcentaje < 100")
    List<Proyecto> findAtrasados();

    // Listar proyectos que vencen en los próximos 7 días
    @Query("SELECT p FROM Proyecto p WHERE p.fechaEntrega BETWEEN CURRENT_DATE AND :proximaSemana")
    List<Proyecto> findPorVencer(@Param("proximaSemana") LocalDate proximaSemana);
}