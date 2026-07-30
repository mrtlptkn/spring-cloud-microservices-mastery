package com.mertalptekin.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1")
@Tag(name = "Home", description = "Servis bilgi endpoint'leri")
public class HomeController {

    private final String serverName;

    public HomeController(@Value("${ServerName:order-service}") String serverName){
        this.serverName = serverName;
    }

    @GetMapping
    @Operation(summary = "Servis adini getir", description = "Config uzerinden gelen ServerName bilgisini dondurur")
    public String index(){
        return  "serverName From Config : " + serverName;
    }

}
