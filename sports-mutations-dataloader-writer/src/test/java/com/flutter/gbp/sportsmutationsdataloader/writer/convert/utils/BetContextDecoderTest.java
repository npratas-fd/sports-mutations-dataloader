package com.flutter.gbp.sportsmutationsdataloader.writer.convert.utils;

import com.flutter.gbp.betcontext.BetContextMessageDecoder;
import com.flutter.gbp.betcontext.domain.BetContext;
import com.flutter.gbp.betcontext.exception.InvalidBase64Exception;
import com.google.protobuf.StringValue;
import com.ppb.platform.sb.fbp.proto.bet.BetOuterClass;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class BetContextDecoderTest {

    @Mock
    private BetContextMessageDecoder betContextMessageDecoder;

    @InjectMocks
    private BetContextDecoder victim;

    private AutoCloseable openMocks;

    @BeforeMethod
    public void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
        this.victim = new BetContextDecoder(betContextMessageDecoder);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        openMocks.close();
    }

    @Test
    public void returnsEmptyWhenBetDoesNotHaveBetContext() {
        // Prepare test data
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setBetId("123")
                .build();

        // Perform extraction
        Optional<BetContext> result = victim.extractBetContext(bet);

        // Assertions
        assertTrue(result.isEmpty());
    }

    @Test
    public void returnsBetContextWhenDecodingSucceeds() throws Exception {
        // Prepare test data
        String base64Value = "DUMMY_ENCODED_CONTEXT";
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setBetId("123")
                .setBetContext(StringValue.of(base64Value))
                .build();

        BetContext expectedBetContext = mock(BetContext.class);
        when(betContextMessageDecoder.decodeFromBase64(base64Value))
                .thenReturn(expectedBetContext);

        // Perform extraction
        Optional<BetContext> result = victim.extractBetContext(bet);

        // Assertions
        assertTrue(result.isPresent());
        assertEquals(result.get(), expectedBetContext);
    }

    @Test
    public void returnsEmptyWhenInvalidBase64ExceptionOccurs() throws Exception {
        // Prepare test data
        String invalidBase64Value = "DUMMY_INVALID_ENCODED_CONTEXT";
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setBetId("123")
                .setBetContext(StringValue.of(invalidBase64Value))
                .build();

        InvalidBase64Exception exception = mock(InvalidBase64Exception.class);
        when(betContextMessageDecoder.decodeFromBase64(invalidBase64Value))
                .thenThrow(exception);

        // Perform extraction
        Optional<BetContext> result = victim.extractBetContext(bet);

        // Assertions
        assertFalse(result.isPresent());
    }

    @Test
    public void returnsEmptyWhenExceptionOccurs() throws Exception {
        // Prepare test data
        String base64Value = "DUMMY_ENCODED_CONTEXT";
        BetOuterClass.Bet bet = BetOuterClass.Bet.newBuilder()
                .setBetId("123")
                .setBetContext(StringValue.of(base64Value))
                .build();

        when(betContextMessageDecoder.decodeFromBase64(base64Value))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Perform extraction
        Optional<BetContext> result = victim.extractBetContext(bet);

        // Assertions
        assertFalse(result.isPresent());
    }
}
