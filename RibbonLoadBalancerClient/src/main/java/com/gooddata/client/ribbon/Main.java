/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.client.ribbon;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.netflix.client.ClientFactory;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;

public class Main {
    private static Logger log = LoggerFactory.getLogger(Main.class);
    private final RestTemplate restTemplate;

    public Main() throws IOException {
        SimpleClientHttpRequestFactory wrappedFactory = new SimpleClientHttpRequestFactory();
        ConfigurationManager.loadPropertiesFromResources("ribbon.properties");
        BaseLoadBalancer loadBalancer = (BaseLoadBalancer) ClientFactory.getNamedLoadBalancer("upstreamServerClient");
        loadBalancer.setPing(new RestTemplatePing(wrappedFactory));

//        ILoadBalancer loadBalancer  = LoadBalancerBuilder.newBuilder()
//                .withPing(new RestTemplatePing(wrappedFactory))
//                .buildFixedServerListLoadBalancer(asList(new Server("localhost:8080"), new Server("localhost:8070")));

        restTemplate = new RestTemplate(new SimpleRibbonClientHttpRequestFactoryWrapper(wrappedFactory, loadBalancer));
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

    public static void main(String[] args) throws InterruptedException, IOException {
        new Main().run();
    }

    public static class RestTemplatePing implements IPing {
        private final String pingPath = "/message";

        private final RestTemplate restTemplate;

        public RestTemplatePing(SimpleClientHttpRequestFactory wrappedFactory) {
            this.restTemplate = new RestTemplate(wrappedFactory);
        }

        @Override
        public boolean isAlive(Server server) {
            URI uri = UriComponentsBuilder
                    .fromPath(pingPath)
                    .host(server.getHost())
                    .port(server.getPort())
                    .scheme("http")
                    .build().toUri();
            try {
                log.info("Ping {}:{}", server.getHost(), server.getPort());
                restTemplate.getForEntity(uri, Void.class);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
