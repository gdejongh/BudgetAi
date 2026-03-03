package com.budget.budgetai.service;

import com.budget.budgetai.dto.AiAdviceDTO;
import com.budget.budgetai.model.AiAdviceCache;
import com.budget.budgetai.repository.AiAdviceCacheRepository;
import com.budget.budgetai.repository.AppUserRepository;
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
    private static final int MAX_GENERATIONS_PER_DAY = 3;

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
            int remaining = getRefreshesRemaining(cache);
            return new AiAdviceDTO(cache.getAdviceText(), cache.getCreatedAt(), cache.getExpiresAt(), remaining);
        }

        // Check rate limit before generating
        AiAdviceCache cacheEntry = cached.orElseGet(() -> {
            AiAdviceCache newCache = new AiAdviceCache();
            newCache.setAppUser(appUserRepository.getReferenceById(userId));
            newCache.setGenerationCount(0);
            newCache.setGenerationResetAt(ZonedDateTime.now());
            return newCache;
        });
        resetDailyCountIfNeeded(cacheEntry);

        if (cacheEntry.getGenerationCount() >= MAX_GENERATIONS_PER_DAY) {
            throw new RateLimitExceededException("Daily AI advice limit reached. Try again tomorrow.");
        }

        // Generate fresh advice
        String financialSummary = financialSummaryBuilder.buildSummary(userId);

        log.info("Generating AI advice for user {} (generation {}/{})", userId,
                cacheEntry.getGenerationCount() + 1, MAX_GENERATIONS_PER_DAY);
        String advice = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(financialSummary)
                .call()
                .content();

        // Cache the response and increment counter
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime expiresAt = now.plusHours(CACHE_HOURS);

        cacheEntry.setAdviceText(advice);
        cacheEntry.setCreatedAt(now);
        cacheEntry.setExpiresAt(expiresAt);
        cacheEntry.setGenerationCount(cacheEntry.getGenerationCount() + 1);
        cacheRepository.save(cacheEntry);

        int remaining = MAX_GENERATIONS_PER_DAY - cacheEntry.getGenerationCount();
        return new AiAdviceDTO(advice, now, expiresAt, remaining);
    }

    /**
     * Clear the cached advice for a user, forcing fresh generation on next request.
     * The rate limit counter is preserved — only the cached text is invalidated.
     */
    public void clearCache(UUID userId) {
        Optional<AiAdviceCache> cached = cacheRepository.findByAppUserId(userId);
        if (cached.isPresent()) {
            AiAdviceCache cache = cached.get();
            cache.setExpiresAt(ZonedDateTime.now().minusSeconds(1));
            cacheRepository.save(cache);
        }
    }

    /**
     * Get remaining refreshes for a user without generating advice.
     */
    public int getRefreshesRemaining(UUID userId) {
        Optional<AiAdviceCache> cached = cacheRepository.findByAppUserId(userId);
        if (cached.isEmpty()) {
            return MAX_GENERATIONS_PER_DAY;
        }
        return getRefreshesRemaining(cached.get());
    }

    private int getRefreshesRemaining(AiAdviceCache cache) {
        resetDailyCountIfNeeded(cache);
        return Math.max(0, MAX_GENERATIONS_PER_DAY - cache.getGenerationCount());
    }

    private void resetDailyCountIfNeeded(AiAdviceCache cache) {
        ZonedDateTime now = ZonedDateTime.now();
        if (cache.getGenerationResetAt() == null
                || cache.getGenerationResetAt().plusHours(24).isBefore(now)) {
            cache.setGenerationCount(0);
            cache.setGenerationResetAt(now);
        }
    }

    /**
     * Thrown when a user exceeds the daily AI advice generation limit.
     */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
