package com.prizm.campaign;

import com.prizm.campaign.dto.RedeemResponse;
import com.prizm.campaign.service.VoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class VoucherServiceTest {

    @Autowired
    private VoucherService voucherService;

    @Test
    void redeemActiveVoucherSucceeds() {
        RedeemResponse res = voucherService.redeem("RAYA-0001", "user-1");
        assertEquals("OK", res.getResult());
    }

    @Test
    void redeemAlreadyRedeemedVoucherFails() {
        RedeemResponse res = voucherService.redeem("RAYA-0004", "user-2");
        assertEquals("FAILED", res.getResult());
    }

    @Test
    void redeemUnknownCodeFails() {
        RedeemResponse res = voucherService.redeem("NOPE-9999", "user-3");
        assertEquals("FAILED", res.getResult());
    }

    // --- per-user redemption limit ---
    // Seed data: campaign 1 defaults to a limit of 2. user-99 already has
    // RAYA-0004 (REDEEMED) and RAYA-0005 (VOID) on campaign 1.

    @Test
    void voidedVoucherDoesNotCountTowardLimit() {
        // If VOID counted, user-99 would already be at 2/2 and this would fail.
        // It should succeed, proving only REDEEMED vouchers count.
        RedeemResponse res = voucherService.redeem("RAYA-0001", "user-99");
        assertEquals("OK", res.getResult());
    }

    @Test
    void userIsBlockedOnceLimitReached() {
        // user-99 has 1 REDEEMED so far (RAYA-0004). This redemption becomes their 2nd.
        RedeemResponse second = voucherService.redeem("RAYA-0001", "user-99");
        assertEquals("OK", second.getResult());

        // Their 3rd attempt on the same campaign should be rejected.
        RedeemResponse third = voucherService.redeem("RAYA-0002", "user-99");
        assertEquals("FAILED", third.getResult());
        assertEquals("Per-user redemption limit reached", third.getMessage());
    }

    @Test
    void limitIsPerUserNotPerCampaign() {
        // user-99 is at their limit after this redemption...
        voucherService.redeem("RAYA-0001", "user-99");

        // ...but a different user redeeming on the same campaign is unaffected.
        RedeemResponse res = voucherService.redeem("RAYA-0002", "brand-new-user");
        assertEquals("OK", res.getResult());
    }
}
