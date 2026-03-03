package com.budget.budgetai.service;

import com.budget.budgetai.dto.AiAdviceDTO;
import com.budget.budgetai.model.AiAdviceCache;
import com.budget.budgetai.repository.AiAdviceCacheRepository;
import com.budget.budgetai.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AiAdviceService {

    private static final Logger log = LoggerFactory.getLogger(AiAdviceService.class);

    private static final int CACHE_HOURS = 24;

    private static final String SYSTEM_PROMPT = """
            You are a concise financial advisor analyzing an envelope-based budget. \
            The user assigns money to "envelopes" (budget categories) and tracks spending against them.

            Based on the financial data provided, give 3-5 actionable insights covering:
            1. **Spending Patterns**: Key observations about where money is going
            2. **Budget Adjustments**: Specific envelope allocation suggestions
            3. **Goal Progress**: Tips to reach savings goals faster (if any goals exist)
            4. **Quick Win**: One simple actionable tip they can do today

            Rules:
            - Use specific dollar amounts from the data
            - Keep the total response under 300 words
            - Use markdown with bold section headers
            - Be encouraging but honest
            - Do NOT recommend specific investment products or financial institutions
            - Focus on budgeting, spending habits, and saving strategies
            """;

    private final ChatClient chatClient;
    private final FinancialSummaryBuilder financialSummaryBuilder;
    private final AiAdviceCacheRepository cacheRepository;
    private final AppUserRepository appUserRepository;

    public AiAdviceService(ChatClient.Builder chatClientBuilder,
            FinancialSummaryBuilder financialSummaryBuilder,
            AiAdviceCacheRepository cacheRepository,
            AppUserRepository appUserRepository) {
        this.chatClient = chatClientBuilder.build();
        this.financialSummaryBuilder = financialSummaryBuilder;
        this.cacheRepository = cacheRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Get AI financial advice for the user. Returns cached advice if still valid,
     * otherwise generates fresh advice from Claude.
     */
    public AiAdviceDTO getAdvice(UUID userId) {
        // Check for valid cached response
        Optional<AiAdviceCache> cached = cacheRepository.findByAppUserId(userId);
        if (cached.isPresent() && cached.get().getExpiresAt().isAfter(ZonedDateTime.now())) {
            AiAdviceCache cache = cached.get();
            return new AiAdviceDTO(cache.getAdviceText(), cache.getCreatedAt(), cache.getExpiresAt());
        }

        // Generate fresh advice
        String financialSummary = financialSummaryBuilder.buildSummary(userId);

        log.info("Generating AI advice for user {}", userId);
        String advice = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(financialSummary)
                .call()
                .content();

        // Cache the response
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime expiresAt = now.plusHours(CACHE_HOURS);

        AiAdviceCache cache = cached.orElseGet(() -> {
            AiAdviceCache newCache = new AiAdviceCache();
            newCache.setAppUser(appUserRepository.getReferenceById(userId));
            return newCache;
        });
        cache.setAdviceText(advice);
        cache.setCreatedAt(now);
        cache.setExpiresAt(expiresAt);
        cacheRepository.save(cache);

        return new AiAdviceDTO(advice, now, expiresAt);
    }

    /**
     * Clear the cached advice for a user, forcing fresh generation on next request.
     */
    public void clearCache(UUID userId) {
        cacheRepository.deleteByAppUserId(userId);
    }
}
