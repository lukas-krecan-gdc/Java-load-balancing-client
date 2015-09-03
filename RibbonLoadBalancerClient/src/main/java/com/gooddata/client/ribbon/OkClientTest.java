/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.client.ribbon;

import static java.util.Arrays.asList;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.Internal;
import com.squareup.okhttp.internal.Network;

public class OkClientTest {
    private static final Logger log = getLogger(OkClientTest.class);
    private final OkHttpClient client;
    public List<String> servers = asList("lk-102i.na.intgdc.com", "lk-102h.na.intgdc.com");

    public OkClientTest() throws Exception {

        client = new OkHttpClient();
        Internal.instance.setNetwork(client, new Network() {
            @Override
            public InetAddress[] resolveInetAddresses(String host) throws UnknownHostException {
                List<InetAddress> result = new ArrayList<>();
                for (String server : servers) {
                    result.addAll(asList(InetAddress.getAllByName(server)));
                }
                return result.toArray(new InetAddress[result.size()]);
            }
        });
        client.networkInterceptors().add(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Response response = chain.proceed(chain.request());
                // Want to fail-over on 5xx error
                if (response.code() >=500) {
                    throw new IOException("Error");
                }
                return response;
            }
        });
    }

    public void run() throws InterruptedException, IOException {
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

    private String getMessage() throws IOException {
        Request request = new Request.Builder()
                .url("http://test:8080/gdcwebapp/gdc/ping")
                .addHeader("Accept", "application/json")
                .build();

        Response response = client.newCall(request).execute();
        return response.message();
    }

    public static void main(String[] args) throws Exception {
        new OkClientTest().run();
    }
}
