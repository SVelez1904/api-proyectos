package com.innovatech.api_proyectos.client;

import com.innovatech.api_proyectos.config.FeignConfig;
import com.innovatech.api_proyectos.dto.UsuarioDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "api-usuarios", url = "http://api-usuarios:8080", configuration = FeignConfig.class)
public interface UsuarioFeignClient {

    @GetMapping("/usuarios/{id}")
    UsuarioDTO getUsuarioById(@PathVariable("id") Long id);
}
