package com.jjinmak.back.boundedContext.wallet.in;

import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 앱이 뜰 때 ESCROW, FEE 시스템 지갑이 없으면 만들어 둔다.
 * 이미 있으면 아무것도 하지 않으므로 여러 번 실행돼도 안전하다(멱등).
 */

@Component
@Order(1)
@RequiredArgsConstructor
public class WalletDataInit implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WalletDataInit.class);

    private static final List<WalletType> SYSTEM_WALLET_TYPE = List.of(
            WalletType.ESCROW,
            WalletType.FEE
    );

    private final WalletRepository walletRepository;


    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        for (WalletType type : SYSTEM_WALLET_TYPE) {
            if (walletRepository.existsByTypeAndHolderIsNull(type)) {
                log.debug("시스템 지갑 이미 존재: {}", type);
                continue;
            }
            Wallet created = walletRepository.save(Wallet.createSystemWallet(type));
            log.info("시스템 지갑 생성: type = {}, walletId = {}", type, created.getId());
        }
    }
}
