package com.jjinmak.back.boundedContext.wallet.in;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wallet/me")
public class WalletController {

    private final WalletFacade walletFacade;
}
