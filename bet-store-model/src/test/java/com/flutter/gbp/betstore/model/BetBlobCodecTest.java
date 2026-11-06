package com.flutter.gbp.betstore.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.flutter.gbp.fcq.bet.Bet;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class BetBlobCodecTest {

    @Test
    void roundTripPreservesBet() {
        Bet bet = Bet.newBuilder().setBetReceiptId("O/123/456").setCurrency("GBP").setIsSettled(false).build();
        assertThat(BetBlobCodec.deserialize(BetBlobCodec.serialize(bet))).contains(bet);
    }

    @Test
    void corruptBytesYieldEmpty() {
        assertThat(BetBlobCodec.deserialize(ByteBuffer.wrap(new byte[]{(byte) 0xFF, 0x01}))).isEmpty();
    }

    @Test
    void nullYieldsEmpty() {
        assertThat(BetBlobCodec.deserialize(null)).isEmpty();
    }
}
