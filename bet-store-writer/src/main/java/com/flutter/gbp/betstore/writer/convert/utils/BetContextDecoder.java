package com.flutter.gbp.betstore.writer.convert.utils;

import com.flutter.gbp.betcontext.BetContextMessageDecoder;
import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.betcontext.exception.InvalidBase64Exception;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class BetContextDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetContextDecoder.class);
    private final BetContextMessageDecoder betContextMessageDecoder;

    public BetContextDecoder(BetContextMessageDecoder betContextMessageDecoder) {
        this.betContextMessageDecoder = betContextMessageDecoder;
    }

    public Optional<BetContext> extractBetContext(BetOuterClass.Bet bet) {
        if (!bet.hasBetContext()) {
            return Optional.empty();
        }

        BetContext betContext = null;
        try {
            betContext = betContextMessageDecoder.decodeFromBase64(bet.getBetContext().getValue());
        } catch (InvalidBase64Exception e) {
            LOGGER.warn("operation=extractBetContext, message='BetContext has invalid base64.', betContext={} ",
                    bet.getBetContext().getValue(), e);
        } catch (Exception e) {
            LOGGER.warn("operation=extractBetContext, message='Invalid bet context.', betContext={} ",
                    bet.getBetContext().getValue(), e);
        }

        return Optional.ofNullable(betContext);
    }
}
