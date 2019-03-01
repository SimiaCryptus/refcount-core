/*
 * Copyright (c) 2019 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.lang;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * The type Settings.
 */
public interface Settings {
  /**
   * The constant logger.
   */
  Logger logger = LoggerFactory.getLogger(Settings.class);

  /**
   * Gets boolean.
   *
   * @param key          the key
   * @param defaultValue the default value
   * @return the boolean
   */
  static boolean get(final String key, final boolean defaultValue) {
    boolean value = Boolean.parseBoolean(System.getProperty(key, Boolean.toString(defaultValue)));
    logger.info(String.format("%s = %s", key, value));
    return value;
  }

  /**
   * Get t.
   *
   * @param <T>          the type parameter
   * @param key          the key
   * @param defaultValue the default value
   * @return the t
   */
  static <T extends Enum<T>> T get(final String key, @Nonnull final T defaultValue) {
    T value = Enum.valueOf((Class<T>) defaultValue.getClass().getSuperclass(), System.getProperty(key, defaultValue.toString().toUpperCase()));
    logger.info(String.format("%s = %s", key, value));
    return value;
  }

  /**
   * Gets int.
   *
   * @param key          the key
   * @param defaultValue the default value
   * @return the int
   */
  static String get(final String key, final String defaultValue) {
    String value = System.getProperty(key, defaultValue);
    logger.info(String.format("%s = %s", key, value));
    return value;
  }

  /**
   * Gets int.
   *
   * @param key          the key
   * @param defaultValue the default value
   * @return the int
   */
  static int get(final String key, final int defaultValue) {
    int value = Integer.parseInt(System.getProperty(key, Integer.toString(defaultValue)));
    logger.info(String.format("%s = %s", key, value));
    return value;
  }

  /**
   * Gets double.
   *
   * @param key          the key
   * @param defaultValue the default value
   * @return the double
   */
  static double get(final String key, final double defaultValue) {
    double value = Double.parseDouble(System.getProperty(key, Double.toString(defaultValue)));
    logger.info(String.format("%s = %s", key, value));
    return value;
  }

  /**
   * Gets long.
   *
   * @param key          the key
   * @param defaultValue the default value
   * @return the long
   */
  static long get(final String key, final long defaultValue) {
    long value = Long.parseLong(System.getProperty(key, Long.toString(defaultValue)));
    logger.info(String.format("%s = %s", key, value));
    return value;
  }


  /**
   * Write json.
   *
   * @param obj the obj
   * @return the char sequence
   */
  public static CharSequence toJson(final Object obj) {
    return toJson(obj, getMapper());
  }

  /**
   * To json char sequence.
   *
   * @param obj          the obj
   * @param objectMapper the object mapper
   * @return the char sequence
   */
  @Nonnull
  public static CharSequence toJson(final Object obj, final ObjectMapper objectMapper) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      objectMapper.writeValue(outputStream, obj);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new String(outputStream.toByteArray(), Charset.forName("UTF-8"));
  }

  /**
   * Gets mapper.
   *
   * @return the mapper
   */
  public static ObjectMapper getMapper() {
    ObjectMapper enable = new ObjectMapper()
        //.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL)
        .enable(SerializationFeature.INDENT_OUTPUT);
    return enable;
  }
}
