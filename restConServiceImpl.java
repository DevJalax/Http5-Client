import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CmsRestConfServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmsRestConfServiceImpl.class);

    @Qualifier("cmsCloseableHttpClient")
    @Autowired
    private CloseableHttpClient cmsCloseableHttpClient;

    public String send(String url) {
        StringBuilder outputSB = new StringBuilder();
        long ts = 0;
        try {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Accept", "application/json");
            post.setHeader("cache-control", "no-cache");

            LOGGER.info("Post Cms req {} ", post);
            ts = System.currentTimeMillis();
            try (CloseableHttpResponse response = cmsCloseableHttpClient.execute(post)) {
                int responseCode = response.getCode();
                LOGGER.info("cms httpStatusCode {} ", responseCode);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try (BufferedReader rd = new BufferedReader(
                            new InputStreamReader(EntityUtils.toInputStream(entity)))) {
                        String line;
                        while ((line = rd.readLine()) != null) {
                            outputSB.append(line);
                        }
                    } catch (IOException | ParseException e) {
                        LOGGER.error("Error reading response entity", e);
                    }
                }
                ts = System.currentTimeMillis() - ts;
                String responseString = outputSB.toString();

                LOGGER.info("middleware response : {}", outputSB.toString());
                return responseString;
            }
        } catch (org.apache.hc.client5.http.ConnectTimeoutException e) {
            ts = System.currentTimeMillis() - ts;
            LOGGER.error("FOR CMS CONNECTION_POOL_TIMEOUT_EXCEPTION :TxnId={} ,error={}", e);
        } catch (org.apache.hc.client5.http.ConnectionClosedException e) {
            ts = System.currentTimeMillis() - ts;
            LOGGER.error("FOR CMS HTTP_HOST_CONNECT_EXCEPTION :TxnId={} ,error={}", e);
        } catch (java.net.SocketTimeoutException e) {
            ts = System.currentTimeMillis() - ts;
            LOGGER.error("FOR CMS SOCKET_TIMEOUT_EXCEPTION :TxnId={} ,error={}", e);
        } catch (Exception e) {
            ts = System.currentTimeMillis() - ts;
            LOGGER.error("FOR CMS CMS_COMMON_EXCEPTION :TxnId={} ,error={}", e);
        }
        return null;
    }
