/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.client.ribbon;

import static java.util.Arrays.asList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;

public class Main {
    private static Logger log = LoggerFactory.getLogger(Main.class);
    private final RestTemplate restTemplate;

    public Main() {
        ILoadBalancer loadBalancer  = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(asList(new Server("localhost:8080"), new Server("localhost:8070")));
        restTemplate = new RestTemplate(new SimpleRibbonClientHttpRequestFactoryWrapper(new SimpleClientHttpRequestFactory(), loadBalancer));
    }

    public void run() throws InterruptedException {
        log.info("------------------------------");
        log.info("RestTemplate Example");

        for (int i = 0; i < 50; i++) {
            try {
                log.info(getMessage());
            } catch (RuntimeException e) {
                log.error("Exception in run {}. -> {}", i, e.getMessage());
            }
            Thread.sleep(1000);
        }
    }

    private String getMessage() {
        ResponseEntity<String> exchange = this.restTemplate.exchange(
                "http://test/message", HttpMethod.GET, null, String.class);
        log.info(String.valueOf(exchange.getStatusCode()));
        return exchange.getBody();
    }

    public static void main(String[] args) throws InterruptedException {
        new Main().run();
    }
}
