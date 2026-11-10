package com.flutter.gbp.sportsmutationsdataloader.reader.api;

import com.datastax.oss.driver.api.core.DriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errors as {@link ProblemDetail}: Cassandra failures are 503; invalid input (including a non-numeric account id,
 * handled by the base class) is 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DriverException.class)
    ProblemDetail handleDriverException(DriverException e) {
        LOG.warn("Cassandra query failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Sports mutations dataloader temporarily unavailable");
    }
}
