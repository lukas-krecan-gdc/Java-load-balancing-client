/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.client.ribbon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;

import rx.Observable;

public class SimpleRibbonClientHttpRequestFactoryWrapper extends AbstractClientHttpRequestFactoryWrapper {
    private final ILoadBalancer loadBalancer;

    public SimpleRibbonClientHttpRequestFactoryWrapper(ClientHttpRequestFactory wrappedFactory, ILoadBalancer loadBalancer) {
        super(wrappedFactory);
        this.loadBalancer = loadBalancer;
    }

    @Override
    protected ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod, ClientHttpRequestFactory requestFactory) throws IOException {
        return new WrappingClientHttpRequest(uri, httpMethod, requestFactory);
    }

    private class WrappingClientHttpRequest extends AbstractClientHttpRequest {
        private final ClientHttpRequestFactory wrappedFactory;
        private final URI originalUri;
        private final HttpMethod httpMethod;
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

        public WrappingClientHttpRequest(URI originalUri, HttpMethod httpMethod, ClientHttpRequestFactory wrappedFactory) {
            this.wrappedFactory = wrappedFactory;
            this.originalUri = originalUri;
            this.httpMethod = httpMethod;
        }

        @Override
        public HttpMethod getMethod() {
            return httpMethod;
        }

        @Override
        public URI getURI() {
            return originalUri;
        }

        @Override
        protected OutputStream getBodyInternal(HttpHeaders headers) throws IOException {
            return baos;
        }

        @Override
        protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
            return LoadBalancerCommand.<ClientHttpResponse>builder()
                    .withLoadBalancer(loadBalancer)
                    .build()
                    .submit(new ServerOperation<ClientHttpResponse>() {
                        @Override
                        public Observable<ClientHttpResponse> call(Server server) {
                            try {
                                URI uri = UriComponentsBuilder.fromUri(originalUri).host(server.getHost()).port(server.getPort()).build().toUri();
                                ClientHttpRequest request = wrappedFactory.createRequest(uri, httpMethod);
                                request.getHeaders().putAll(headers);
                                FileCopyUtils.copy(baos.toByteArray(), request.getBody());
                                ClientHttpResponse response = request.execute();
                                if (response.getStatusCode().is5xxServerError()) {
                                    //FIXME: make it nicer
                                    return Observable.error(new HttpServerErrorException(response.getStatusCode(), response.getStatusText()));
                                }
                                return Observable.just(response);
                            } catch (Exception e) {
                                return Observable.error(e);
                            }
                        }
                    }).toBlocking().first();
        }
    }
}
