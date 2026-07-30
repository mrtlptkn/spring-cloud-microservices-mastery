package com.mertalptekin.orderservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1")
public class HomeController {

    private final String serverName;

    public HomeController(@Value("${ServerName}") String serverName){
        this.serverName = serverName;
    }

    @GetMapping
    public String index(){
        return  "serverName From Config : " + serverName;
    }

}
