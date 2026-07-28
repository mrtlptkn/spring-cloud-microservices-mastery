package com.mertalptekin.productservice.controller;

import com.netflix.appinfo.InstanceInfo;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("api/v1")
public class HomeController {

    private final   DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public HomeController(DiscoveryClient discoveryClient, RestClient.Builder builder){
        restClient = builder.build();
        this.discoveryClient = discoveryClient;
    }


    // http://localhost:5002/api/v1/order-service-invocation-with-service-discovery

    @GetMapping("order-service-invocation-with-service-discovery")
    public String getOrderServiceInvocationResult(){

      ServiceInstance instance =  this.discoveryClient.getInstances("order-service").get(0);

      if(instance != null){
          String response = restClient
                  .get()
                  .uri(instance.getUri() + "/api/v1")
                  .retrieve().body(String.class);
          return  "Response From OrderService " + response;
      }


      return "Discovery Client bulunamadı";
    }
}
