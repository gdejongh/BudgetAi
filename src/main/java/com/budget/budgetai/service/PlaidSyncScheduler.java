package com.budget.budgetai.service;

import com.budget.budgetai.model.PlaidItem;
import com.budget.budgetai.model.PlaidItemStatus;
import com.budget.budgetai.repository.PlaidItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlaidSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlaidSyncScheduler.class);

    private final PlaidItemRepository plaidItemRepository;
    private final PlaidService plaidService;

    @Value("${plaid.sync.cron:0 0 6 * * *}")
    private String plaidSyncCron;

    public PlaidSyncScheduler(PlaidItemRepository plaidItemRepository, PlaidService plaidService) {
        this.plaidItemRepository = plaidItemRepository;
        this.plaidService = plaidService;
    }

    /**
     * Runs daily at 6:00 AM to sync transactions for all active Plaid connections.
     * Balances are calculated from transactions, not refreshed from the Plaid API.
     */
    @Scheduled(cron = "${plaid.sync.cron:0 0 6 * * *}")
    public void dailySync() {
        log.info("Starting daily Plaid sync...");

        List<PlaidItem> activeItems = plaidItemRepository.findByStatus(PlaidItemStatus.ACTIVE);
        log.info("Found {} active Plaid items to sync", activeItems.size());

        int successCount = 0;
        int errorCount = 0;

        for (PlaidItem item : activeItems) {
            try {
                log.debug("Syncing Plaid item {} for user {}", item.getItemId(),
                        item.getAppUser().getId());
                plaidService.syncTransactions(item);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to sync Plaid item {} for user {}: {}",
                        item.getItemId(), item.getAppUser().getId(), e.getMessage(), e);
                // Individual item failures don't block other items
            }
        }

        log.info("Daily Plaid sync completed: {} succeeded, {} failed", successCount, errorCount);
    }
}
