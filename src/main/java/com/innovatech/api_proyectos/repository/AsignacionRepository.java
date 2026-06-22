package com.innovatech.api_proyectos.repository;


import com.innovatech.api_proyectos.entity.Asignacion;
import com.innovatech.api_proyectos.entity.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

        List<Asignacion> findByUsuarioId(Long usuarioId);

}