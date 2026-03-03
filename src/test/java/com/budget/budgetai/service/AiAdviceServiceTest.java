package com.budget.budgetai.service;

import com.budget.budgetai.dto.AiAdviceDTO;
import com.budget.budgetai.model.AiAdviceCache;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.repository.AiAdviceCacheRepository;
import com.budget.budgetai.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAdviceServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;
    @Mock
    private FinancialSummaryBuilder financialSummaryBuilder;
    @Mock
    private AiAdviceCacheRepository cacheRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private AiAdviceService aiAdviceService;

    private UUID userId;
    private AppUser appUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");

        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiAdviceService = new AiAdviceService(chatClientBuilder, financialSummaryBuilder,
                cacheRepository, appUserRepository);
    }

    @Test
    void getAdvice_cachedAndValid_returnsCachedAdvice() {
        AiAdviceCache cache = new AiAdviceCache();
        cache.setAdviceText("Cached advice text");
        cache.setCreatedAt(ZonedDateTime.now().minusHours(1));
        cache.setExpiresAt(ZonedDateTime.now().plusHours(23));
        cache.setGenerationCount(1);
        cache.setGenerationResetAt(ZonedDateTime.now());

        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.of(cache));

        AiAdviceDTO result = aiAdviceService.getAdvice(userId);

        assertNotNull(result);
        assertEquals("Cached advice text", result.getAdvice());
        assertEquals(2, result.getRefreshesRemaining());
        // Should NOT call the LLM
        verify(chatClient, never()).prompt();
        verify(financialSummaryBuilder, never()).buildSummary(any());
    }

    @Test
    void getAdvice_cacheExpired_generatesFreshAdvice() {
        AiAdviceCache expiredCache = new AiAdviceCache();
        expiredCache.setAppUser(appUser);
        expiredCache.setAdviceText("Old advice");
        expiredCache.setCreatedAt(ZonedDateTime.now().minusDays(2));
        expiredCache.setExpiresAt(ZonedDateTime.now().minusDays(1));
        expiredCache.setGenerationCount(1);
        expiredCache.setGenerationResetAt(ZonedDateTime.now());

        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.of(expiredCache));
        when(financialSummaryBuilder.buildSummary(userId)).thenReturn("ACCOUNTS: Checking $1000");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Fresh AI advice");
        when(cacheRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AiAdviceDTO result = aiAdviceService.getAdvice(userId);

        assertNotNull(result);
        assertEquals("Fresh AI advice", result.getAdvice());
        assertEquals(1, result.getRefreshesRemaining()); // 3 - 2 = 1
        verify(financialSummaryBuilder).buildSummary(userId);
        verify(cacheRepository).save(any(AiAdviceCache.class));
    }

    @Test
    void getAdvice_noCache_generatesAndCachesAdvice() {
        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.empty());
        when(financialSummaryBuilder.buildSummary(userId)).thenReturn("ACCOUNTS: Checking $1000");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("New AI advice");
        when(appUserRepository.getReferenceById(userId)).thenReturn(appUser);
        when(cacheRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AiAdviceDTO result = aiAdviceService.getAdvice(userId);

        assertNotNull(result);
        assertEquals("New AI advice", result.getAdvice());
        assertEquals(2, result.getRefreshesRemaining()); // 3 - 1 = 2
        assertNotNull(result.getGeneratedAt());
        assertNotNull(result.getCachedUntil());
        verify(cacheRepository).save(any(AiAdviceCache.class));
    }

    @Test
    void getAdvice_rateLimitExceeded_throwsException() {
        AiAdviceCache cache = new AiAdviceCache();
        cache.setAppUser(appUser);
        cache.setAdviceText("Old advice");
        cache.setCreatedAt(ZonedDateTime.now().minusHours(2));
        cache.setExpiresAt(ZonedDateTime.now().minusHours(1));
        cache.setGenerationCount(3);
        cache.setGenerationResetAt(ZonedDateTime.now());

        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.of(cache));

        assertThrows(AiAdviceService.RateLimitExceededException.class,
                () -> aiAdviceService.getAdvice(userId));

        // Should NOT call the LLM
        verify(chatClient, never()).prompt();
        verify(financialSummaryBuilder, never()).buildSummary(any());
    }

    @Test
    void getAdvice_dailyCountResetsAfter24Hours() {
        AiAdviceCache cache = new AiAdviceCache();
        cache.setAppUser(appUser);
        cache.setAdviceText("Old advice");
        cache.setCreatedAt(ZonedDateTime.now().minusDays(2));
        cache.setExpiresAt(ZonedDateTime.now().minusDays(1));
        cache.setGenerationCount(3);
        // Reset time was over 24 hours ago — count should reset
        cache.setGenerationResetAt(ZonedDateTime.now().minusHours(25));

        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.of(cache));
        when(financialSummaryBuilder.buildSummary(userId)).thenReturn("ACCOUNTS: Checking $1000");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Advice after reset");
        when(cacheRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AiAdviceDTO result = aiAdviceService.getAdvice(userId);

        assertNotNull(result);
        assertEquals("Advice after reset", result.getAdvice());
        assertEquals(2, result.getRefreshesRemaining()); // reset to 0, then +1 = 1, so 3-1=2
    }

    @Test
    void clearCache_invalidatesCacheButPreservesRow() {
        AiAdviceCache cache = new AiAdviceCache();
        cache.setAdviceText("Some advice");
        cache.setExpiresAt(ZonedDateTime.now().plusHours(23));
        cache.setGenerationCount(1);

        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.of(cache));
        when(cacheRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        aiAdviceService.clearCache(userId);

        ArgumentCaptor<AiAdviceCache> captor = ArgumentCaptor.forClass(AiAdviceCache.class);
        verify(cacheRepository).save(captor.capture());
        assertTrue(captor.getValue().getExpiresAt().isBefore(ZonedDateTime.now()));
        // Row is still saved, not deleted
        verify(cacheRepository, never()).deleteByAppUserId(userId);
    }

    @Test
    void getRefreshesRemaining_noCache_returnsMax() {
        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.empty());

        int remaining = aiAdviceService.getRefreshesRemaining(userId);

        assertEquals(3, remaining);
    }
}
