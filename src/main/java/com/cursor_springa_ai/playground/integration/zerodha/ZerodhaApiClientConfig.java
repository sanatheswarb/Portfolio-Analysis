package com.cursor_springa_ai.playground.integration.zerodha;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Uses JDK {@link HttpClient} with HTTP/1.1 and timeouts to reduce
 * "Connection reset" issues behind proxies / TLS middleboxes (common on Windows).
 */
@Configuration
public class ZerodhaApiClientConfig {

    public static final String KITE_REST_CLIENT = "kiteRestClient";

    @Bean
    @Qualifier(KITE_REST_CLIENT)
    public RestClient kiteRestClient(ZerodhaProperties zerodhaProperties) {
        HttpClient.Builder http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(
                        zerodhaProperties.getConnectTimeoutSeconds() != null
                                ? zerodhaProperties.getConnectTimeoutSeconds()
                                : 30))
                .version(HttpClient.Version.HTTP_1_1);

        if (StringUtils.hasText(zerodhaProperties.getProxyHost())
                && zerodhaProperties.getProxyPort() != null
                && zerodhaProperties.getProxyPort() > 0) {
            http.proxy(ProxySelector.of(
                    new InetSocketAddress(
                            zerodhaProperties.getProxyHost().trim(),
                            zerodhaProperties.getProxyPort())));
        }

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        int readSeconds = zerodhaProperties.getReadTimeoutSeconds() != null
                ? zerodhaProperties.getReadTimeoutSeconds()
                : 60;
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(zerodhaProperties.getBaseUrl())
                .defaultHeader("User-Agent", "SpringBoot-KiteConnect/1.0 (+https://kite.trade)")
                .build();
    }
}
