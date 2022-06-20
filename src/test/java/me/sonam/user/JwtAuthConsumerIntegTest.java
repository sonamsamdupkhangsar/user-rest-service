package me.sonam.user;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.boot.json.JsonParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This will create a pact using {@link JwtAuthConsumerIntegTest#createPact(PactDslWithProvider)}
 * The pact is then published using `mvn pact:publish command
 */

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "jwt-rest-service")
public class JwtAuthConsumerIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthConsumerIntegTest.class);

    private String jsonString;
    private PactDslJsonBody pactDslJsonBody;

    @Pact(provider="jwt-rest-service", consumer="user-rest-service")
    public RequestResponsePact createPact(PactDslWithProvider builder) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        pactDslJsonBody = new PactDslJsonBody()
                .stringType("subject")
                .stringType("audience")
                .uuid("id")
                .stringType("issuer", "sonam.cloud");

        return builder
                .uponReceiving("validate jwt header")
                .path("/validate")
                .matchHeader("Authorization", "Bearer .*",  "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ")
                .method("GET")
                .willRespondWith()
                .matchHeader("Content-Type", "application/json")
                .status(200)
                .body(pactDslJsonBody)
                .toPact();
    }

    /**
     * The following will send a Authorization header to the mock server.
     * This will then assert that we get 200 http response, and assert the
     * body response matches audience, subject and so on.
     * @param mockServer
     * @throws IOException
     */
    @Test
    public void TestAndSecretAreSet(MockServer mockServer) throws IOException {
        LOG.info("starting mock server");
        HttpResponse httpResponse = Request.Get(mockServer.getUrl() + "/validate")
                  .addHeader("Authorization", "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ")
                .execute()
                .returnResponse();

        LOG.info("asserting 200 for success from mock server response");
        assertThat(httpResponse.getStatusLine().getStatusCode()).isEqualTo(200);
        LOG.info("assert json body contains valid");
        String gotBody = IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8");
        LOG.info("body: {}", gotBody);
        LOG.info("pactDslJsonBody.body: {}", pactDslJsonBody.getBody().toString());
        JSONObject jsonObject = new JSONObject();
        JsonParser jsonParser = new JacksonJsonParser();
        Map<String, Object> map = jsonParser.parseMap(gotBody);

        assertThat(map.get("subject")).isNotNull();
        assertThat(map.get("audience")).isNotNull();
        assertThat(map.get("id")).isNotNull();
        assertThat(map.get("issuer")).isEqualTo("sonam.cloud");
    }
}
