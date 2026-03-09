package com.budget.budgetai.config;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
@ConditionalOnProperty(name = "plaid.enabled", havingValue = "true")
public class PlaidConfig {

    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    @Value("${plaid.env:sandbox}")
    private String environment;

    @Bean
    public PlaidApi plaidApi() {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Plaid client ID is not configured. Set the PLAID_CLIENT_ID environment variable.");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Plaid secret is not configured. Set the PLAID_SECRET environment variable.");
        }

        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", clientId);
        apiKeys.put("secret", secret);

        ApiClient apiClient = new ApiClient(apiKeys);
        apiClient.setPlaidAdapter(mapEnvironment(environment));

        return apiClient.createService(PlaidApi.class);
    }

    private String mapEnvironment(String env) {
        return switch (env.toLowerCase()) {
            case "production" -> ApiClient.Production;
            default -> ApiClient.Sandbox;
        };
    }
}
