package com.flutter.gbp.betstore.writer.convert;

/**
 * Simple abstraction to define a generalized behaviour to accomplish object conversions
 *
 * @param <S> The source object to be converted
 * @param <T> The target object that was converted
 */
public interface Converter<S, T> {

    T convert(S source);

}
