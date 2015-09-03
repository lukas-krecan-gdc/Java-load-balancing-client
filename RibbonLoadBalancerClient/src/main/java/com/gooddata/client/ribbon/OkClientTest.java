/*
 * Copyright (C) 2007-2015, GoodData(R) Corporation. All rights reserved.
 */
package com.gooddata.client.ribbon;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
        final SSLSocketFactory sslSocketFactory = createTrustingSocketFactory();

        client = new OkHttpClient();
        client.setSslSocketFactory(sslSocketFactory);
        // will not be needed on prod
        client.setHostnameVerifier((hostname, session) -> true);
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
                if (response.code() >=500) {
                    throw new IOException("Error");
                }
                return response;
            }
        });
    }

    /**
     * Just for test, will not be needed in prod.
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    protected SSLSocketFactory createTrustingSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };

        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        // Create an ssl socket factory with our all-trusting manager
        return sslContext.getSocketFactory();
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
                .url("https://test/gdc/ping")
                .addHeader("Accept", "application/json")
                .build();

        Response response = client.newCall(request).execute();
        return response.message();
    }

    public static void main(String[] args) throws Exception {
        new OkClientTest().run();
    }
}
