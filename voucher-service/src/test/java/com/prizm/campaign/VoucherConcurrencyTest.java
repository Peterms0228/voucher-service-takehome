package com.prizm.campaign;

import com.prizm.campaign.model.Campaign;
import com.prizm.campaign.model.Voucher;
import com.prizm.campaign.repository.CampaignRepository;
import com.prizm.campaign.repository.RedemptionRepository;
import com.prizm.campaign.repository.VoucherRepository;
import com.prizm.campaign.service.VoucherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class VoucherConcurrencyTest {

    @Autowired private VoucherService voucherService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private VoucherRepository voucherRepository;
    @Autowired private RedemptionRepository redemptionRepository;

    private static final Long CAMPAIGN_ID = 1L;    // seeded "Raya Flash Deal"
    private static final String USER = "user-99";  // seeded with 1 REDEEMED voucher already (RAYA-0004)

    // The campaign's remaining ACTIVE vouchers from data.sql. RAYA-0004 is
    // already REDEEMED and RAYA-0005 is VOID, so these four are the only
    // ones actually available to race for.
    private static final String[] TARGET_CODES = {"RAYA-0001", "RAYA-0002", "RAYA-0003", "RAYA-0006"};

    // No @Transactional: each redeem() must commit independently on its own
    // thread, or there's no genuine race to test.
    @Test
    void concurrentRedeemsByUserAlreadyNearLimitNeverExceedIt() throws Exception {
        int limit = campaignRepository.findById(CAMPAIGN_ID).orElseThrow().getMaxRedemptionsPerUser();
        int alreadyRedeemed = voucherRepository.countByCampaignIdAndRedeemedByAndStatus(CAMPAIGN_ID, USER, "REDEEMED");
        int expectedSuccesses = limit - alreadyRedeemed;

        assertTrue(expectedSuccesses > 0 && expectedSuccesses < TARGET_CODES.length,
                "seed data assumption broken: need user-99 partway to the limit, "
                        + "with more ACTIVE vouchers on hand than remaining allowance");

        ExecutorService pool = Executors.newFixedThreadPool(TARGET_CODES.length);
        CountDownLatch ready = new CountDownLatch(TARGET_CODES.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (String code : TARGET_CODES) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return voucherService.redeem(code, USER).getResult();
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "threads didn't line up in time");
        go.countDown(); // release all threads at once to maximise contention

        long succeeded = 0;
        for (Future<String> f : futures) {
            if ("OK".equals(f.get(10, TimeUnit.SECONDS))) succeeded++;
        }
        pool.shutdownNow();

        assertEquals(expectedSuccesses, succeeded, "limit must hold even when redemptions race");
    }

    // Restores data.sql's original seed state so this test doesn't leak
    // permanent mutations into other tests or a second run.
    @AfterEach
    void restoreSeedState() {
        Campaign campaign = campaignRepository.findById(CAMPAIGN_ID).orElseThrow();
        int restoredStock = 0;

        for (String code : TARGET_CODES) {
            Voucher v = voucherRepository.findByCode(code);
            if (!"ACTIVE".equals(v.getStatus())) {
                redemptionRepository.findByVoucherId(v.getId())
                        .forEach(r -> redemptionRepository.deleteById(r.getId()));
                v.setStatus("ACTIVE");
                v.setRedeemedBy(null);
                v.setRedeemedAt(null);
                voucherRepository.save(v);
                restoredStock++;
            }
        }

        if (restoredStock > 0) {
            campaign.setRemainingStock(campaign.getRemainingStock() + restoredStock);
            campaignRepository.save(campaign);
        }
    }
}