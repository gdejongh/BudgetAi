package com.budget.budgetai.service;

import com.budget.budgetai.dto.AiAdviceDTO;
import com.budget.budgetai.model.AiAdviceCache;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.repository.AiAdviceCacheRepository;
import com.budget.budgetai.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

        when(cacheRepository.findByAppUserId(userId)).thenReturn(Optional.of(cache));

        AiAdviceDTO result = aiAdviceService.getAdvice(userId);

        assertNotNull(result);
        assertEquals("Cached advice text", result.getAdvice());
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
        assertNotNull(result.getGeneratedAt());
        assertNotNull(result.getCachedUntil());
        verify(cacheRepository).save(any(AiAdviceCache.class));
    }

    @Test
    void clearCache_deletesUserCache() {
        aiAdviceService.clearCache(userId);
        verify(cacheRepository).deleteByAppUserId(userId);
    }
}
