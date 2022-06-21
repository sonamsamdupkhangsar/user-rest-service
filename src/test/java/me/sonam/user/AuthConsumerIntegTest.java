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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.boot.json.JsonParser;



import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This will create a pact using {@link AuthConsumerIntegTest#createPact(PactDslWithProvider)}
 * The pact is then published using `mvn pact:publish command
 */

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "authenticate-rest-service")
public class AuthConsumerIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(AuthConsumerIntegTest.class);

    private String jsonString;
    private PactDslJsonBody pactDslJsonBody;

    @Pact(provider="authenticate-rest-service", consumer="user-rest-service")
    public RequestResponsePact createPact(PactDslWithProvider builder) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        return builder
                .uponReceiving("create authentication")
                .path("/create")
                .method("POST")
                .matchHeader("Content-Type", "application/json")
                .body(new PactDslJsonBody().stringType("authenticationId")
                        .stringType("password")
                        .stringType("apiKey"))
                .willRespondWith()
                .matchHeader("Content-Type", "application/json")
                .status(201)
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
        List<NameValuePair> nameValuePairList = new ArrayList<NameValuePair>();

        nameValuePairList.add(new BasicNameValuePair("authenticationId", "sonam"));
        nameValuePairList.add(new BasicNameValuePair("password", "hello12"));

        final String jsonBody = "{\"authenticationId\": \"sonam\", " +
                "\"password\": \"hello\"," +
                "\"apiKey\": \"dummyApiKey\"}";

        StringEntity stringEntity = new StringEntity(jsonBody);

        HttpResponse httpResponse = Request.Post(mockServer.getUrl() + "/create")
                 .body(stringEntity)
                .setHeader("Content-Type", "application/json")
                .execute()
                .returnResponse();

        LOG.info("asserting 200 for success from mock server response");
        assertThat(httpResponse.getStatusLine().getStatusCode()).isEqualTo(201);
        LOG.info("assert json body contains valid");
        String gotBody = IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8");
        LOG.info("body: {}", gotBody);
    }
}
