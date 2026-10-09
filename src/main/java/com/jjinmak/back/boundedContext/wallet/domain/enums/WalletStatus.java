package com.jjinmak.back.boundedContext.wallet.domain;



public enum WalletStatus {

    ACTIVE("정상", true, true, true),
    FROZEN("동결", false, false, true),
    CLOSED("해지", false, false, false);

    private final String description;
    private final boolean chargeable;  // 사용자 충전 (토스페이먼츠)
    private final boolean spendable;  // 결제, 출금 (잔액 감소)
    private final boolean depositable;  // 환불, 정산 입금 (잔액 증가)

    WalletStatus(String description, boolean chargeable, boolean spendable, boolean depositable) {
        this.description = description;
        this.chargeable = chargeable;
        this.spendable = spendable;
        this.depositable = depositable;
    }

    public boolean canTransitionTo(WalletStatus next) {
        return switch (this) {
            case ACTIVE -> next == FROZEN || next == CLOSED;
            case FROZEN -> next == ACTIVE || next == CLOSED;
            case CLOSED -> false;
        };
    }

    public String getDescription() {
        return description;
    }

    public boolean isChargeable() {
        return chargeable;
    }

    public boolean isSpendable() {
        return spendable;
    }

    public boolean isDepositable() {
        return depositable;
    }
}
