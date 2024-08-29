import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpContext;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElementIterator;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CmsRestConnectionConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmsRestConnectionConfig.class);

    @Value("${cms.connection.request.timeout}")
    private int cmsconnectionRequestTimeout;

    @Value("${cms.connect.timeout}")
    private int cmsconnectTimeout;

    @Value("${cms.socket.timeout}")
    private int cmssocketTimeout;

    @Value("${cms.max.total.connection}")
    private int cmsmaxTotalConnections;

    @Value("${cms.default.keep.alive.time.millis}")
    private int cmsdefaultKeepAlive;

    @Value("${cms.default.max.per.route}")
    private int cmsdefaultMaxPerRoute;

    @Value("${cms.ssl.certificate.path}")
    private String sslCertifcatePath;

    @Bean("cmsPoolingHttpClientConnectionManager")
    public PoolingHttpClientConnectionManager poolingConnectionManager() {
        PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager();
        poolingConnectionManager.setMaxTotal(cmsmaxTotalConnections);
        poolingConnectionManager.setDefaultMaxPerRoute(cmsdefaultMaxPerRoute);
        return poolingConnectionManager;
    }

    public ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
        return (response, context) -> {
            HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator("Keep-Alive"));
            while (it.hasNext()) {
                HeaderElement he = it.nextElement();
                String param = he.getName();
                String value = he.getValue();
                if (value != null && "timeout".equalsIgnoreCase(param)) {
                    return TimeValue.ofSeconds(Long.parseLong(value));
                }
            }
            return TimeValue.ofMillis(cmsdefaultKeepAlive);
        };
    }

    @Bean("cmsCloseableHttpClient")
    public CloseableHttpClient httpClient(
            @Qualifier("cmsPoolingHttpClientConnectionManager") PoolingHttpClientConnectionManager poolingConnectionManager) {
        CloseableHttpClientBuilder clientBuilder = HttpClients.custom();
        clientBuilder.setConnectionManager(poolingConnectionManager);
        clientBuilder.setKeepAliveStrategy(connectionKeepAliveStrategy());

        try {
            // Load your SSL certificate
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(new FileInputStream(sslCertifcatePath));

            // Create an SSL context with the custom TrustManager that trusts your certificate
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((TrustStrategy) (chain, authType) -> true)
                    .loadKeyMaterial(KeyStore.getInstance(KeyStore.getDefaultType()), null, (aliases, socket) -> certificate)
                    .build();

            clientBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
        } catch (Exception e) {
            LOGGER.error("Error loading SSL certificate", e);
        }

        return clientBuilder.build();
    }
}
