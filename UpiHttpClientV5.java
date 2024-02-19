package com.npst;

import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;


@Configuration
public class UpiHttpClientV5 {

	private static final Logger LOGGER = LoggerFactory.getLogger(UpiHttpClientV5.class);
	
	@Value("${npci.connection.request.timeout}")
	private int connectionRequestTimeout;
	
	@Value("${npci.connect.timeout}")
	private int connectTimeout;
	
	@Value("${npci.socket.timeout}")
	private int socketTimeout;

	@Value("${npci.max.total.connection}")
	private int maxTotalConnections;

	@Value("${npci.default.keep.alive.time.millis}")
	private int defaultKeepAlive;

	@Value("${npci.default.max.per.route}")
	private int defaultMaxPerRoute;

	@Autowired
	private CollectorRegistry collectorRegistry;

	private AtomicLong startTime = new AtomicLong();
	
	@Bean("npciPoolingHttpClientConnectionManager")
	public PoolingHttpClientConnectionManager poolingConnectionManager() {
		LayeredConnectionSocketFactory sslsf = null;
		try {
			SSLContextBuilder builder = new SSLContextBuilder();
			TrustManager[] trust_mgr = get_trust_mgr();
			builder.build().init(null, trust_mgr, new SecureRandom());
			builder.loadTrustMaterial(null, (chain, authType) -> true);
            sslsf = (LayeredConnectionSocketFactory) SSLConnectionSocketFactory.getSocketFactory();
		}catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
			LOGGER.error("NPCI Pooling Connection Manager creation error {}", e);
		}
		PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(sslsf).build();
		PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager();
		poolingConnectionManager.setMaxTotal(maxTotalConnections);
		poolingConnectionManager.setDefaultMaxPerRoute(defaultMaxPerRoute);
		return poolingConnectionManager;
	}
	
	public ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
		LOGGER.trace("");
		return new ConnectionKeepAliveStrategy() {
			@Override
			public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
				LOGGER.info("inside ConnectionKeepAliveStrategy connectionKeepAliveStrategy()");
				Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderElements.KEEP_ALIVE);	
				while (it.hasNext()) {
					HeaderElement he = it.next();
					String param = he.getName();
					String value = he.getValue();

					if (value != null && param.equalsIgnoreCase("timeout")) {
						return TimeValue.ofSeconds(Long.parseLong(value)*1000);
					}
				}
				LOGGER.info("DEFAULT_KEEP_ALIVE_TIME_MILLIS=" + defaultKeepAlive);
				LOGGER.info("End ConnectionKeepAliveStrategy connectionKeepAliveStrategy()");
				return TimeValue.ofSeconds(defaultKeepAlive);
			}
		};
	}
	
	@Bean("npciCloseableHttpClient")
	public CloseableHttpClient httpClient(
			@Qualifier("npciPoolingHttpClientConnectionManager") PoolingHttpClientConnectionManager poolingConnectionManager) {
		    LOGGER.info("start building npciCloseableHttpClient");
		    RequestConfig requestConfig = requestConfig();
		    ConnectionKeepAliveStrategy connectionKeepAliveStrategy = connectionKeepAliveStrategy();
		    LOGGER.info("npci requestConfig {}", requestConfig);
		    LOGGER.info("npci connectionKeepAliveStrategy {}", connectionKeepAliveStrategy);
		    LOGGER.info("npci PoolingHttpClientConnectionManager {}", poolingConnectionManager);
		    
		    Counter requestCounter = Counter.build().name("http_npci_client_requests_total")
					.help("Total number of HTTP client NPCI requests").labelNames("endpoint", "status_code")
					.register(collectorRegistry);
		    
		    Histogram requestDuration = Histogram.build().name("http_npci_client_request_duration_seconds")
					.help("Duration of HTTP client NPCI requests").labelNames("endpoint").register(collectorRegistry);
		    
		    CloseableHttpClient closeableHttpClient_ = HttpClients.custom()
		    	    .setDefaultRequestConfig(requestConfig)
		    	    .setConnectionManager(poolingConnectionManager)
		    	    .setKeepAliveStrategy(connectionKeepAliveStrategy)
		    	//    .addRequestInterceptorFirst((HttpRequestInterceptor)(request,entityDetails,context) -> {
		    	        // Start a timer for the request
		    	  //      startTime.set(System.currentTimeMillis());
		    	    //})
		    	    //.addResponseInterceptorLast((HttpResponseInterceptor)(response,entityDetails,context) -> {
		    	        // Calculate the duration
		    	      //  double duration = (System.currentTimeMillis() - startTime.get()) / 1000.0;

		    	        // Record the metrics
		    	       // ClassicHttpRequest httpRequest = (ClassicHttpRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
		    	       // HttpHost target = (HttpHost) context.getAttribute(HttpClientContext.HTTP_REQUEST);
		    	       // String uri = target.toURI() + httpRequest.getRequestUri();

		    	      //  requestCounter.labels(uri, String.valueOf(response.getCode())).inc();
		    	      //  requestDuration.labels(uri).observe(duration);
		    	    //})
		    	    .build();
		    LOGGER.info("npci closeableHttpClient_ {}", closeableHttpClient_);
			return closeableHttpClient_;
		}
	
	RequestConfig requestConfig() {
		LOGGER.trace("");
	    return RequestConfig.custom()
	        .setConnectionRequestTimeout(connectionRequestTimeout, TimeUnit.MILLISECONDS)
	        .setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
	        .setResponseTimeout(socketTimeout, TimeUnit.MILLISECONDS)
	        .build();
	}
	
	private static TrustManager[] get_trust_mgr() {
		TrustManager[] certs = new TrustManager[] { new X509ExtendedTrustManager() {
			@Override
			public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
					throws java.security.cert.CertificateException {
				// TODO Auto-generated method stub

			}

			@Override
			public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
					throws java.security.cert.CertificateException {
				// TODO Auto-generated method stub

			}

			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1, Socket arg2)
					throws java.security.cert.CertificateException {
				// TODO Auto-generated method stub

			}

			@Override
			public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1, SSLEngine arg2)
					throws java.security.cert.CertificateException {
				// TODO Auto-generated method stub

			}

			@Override
			public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1, Socket arg2)
					throws java.security.cert.CertificateException {
				// TODO Auto-generated method stub

			}

			@Override
			public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1, SSLEngine arg2)
					throws java.security.cert.CertificateException {
				// TODO Auto-generated method stub

			}

		} };
		return certs;
	}
	
	public static String geturi(String uri) {
		String delimiter = "urn:txnid:";

		String[] parts = uri.split(delimiter, 2);

		if (parts.length == 2) {
			String beforeDelimiter = parts[0];

			return beforeDelimiter;
		} else {
			return uri;
		}
	}
}
