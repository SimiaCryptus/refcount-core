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

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class StackCounter {

  @Nonnull
  Map<StackFrame, DoubleStatistics> stats = new ConcurrentHashMap<>();

  public static String toString(@Nonnull final StackCounter left, @Nonnull final StackCounter right, @Nonnull final BiFunction<DoubleStatistics, DoubleStatistics, Number> fn) {
    Comparator<StackFrame> comparing = Comparator.comparing(key -> {
      return -fn.apply(left.stats.get(key), right.stats.get(key)).doubleValue();
    });
    comparing = comparing.thenComparing(Comparator.comparing(key -> key.toString()));
    return Stream.concat(left.stats.keySet().stream(), right.stats.keySet().stream())
        .distinct()
        .filter(k -> left.stats.containsKey(k) && right.stats.containsKey(k))
        .sorted(comparing)
        .map(key -> String.format("%s - %s", key.toString(), fn.apply(left.stats.get(key), right.stats.get(key))))
        .limit(100)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  public void increment(final long length) {
    final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    for (@Nonnull final StackTraceElement frame : stackTrace) {
      stats.computeIfAbsent(new StackFrame(frame), f -> new DoubleStatistics()).accept(length);
    }
  }

  @Nonnull
  protected Number summaryStat(@Nonnull final DoubleStatistics value) {
    return (int) value.getSum();
  }

  @Override
  public String toString() {
    return toString(this::summaryStat);
  }

  public String toString(@Nonnull final Function<DoubleStatistics, Number> fn) {
    Comparator<Map.Entry<StackFrame, DoubleStatistics>> comparing = Comparator.comparing(e -> -fn.apply(e.getValue()).doubleValue());
    comparing = comparing.thenComparing(Comparator.comparing(e -> e.getKey().toString()));
    return stats.entrySet().stream()
        .sorted(comparing)
        .map(e -> String.format("%s - %s", e.getKey().toString(), fn.apply(e.getValue())))
        .limit(100).reduce((a, b) -> a + "\n" + b).orElse(super.toString());
  }

  public CharSequence toString(@Nonnull final StackCounter other, @Nonnull final BiFunction<DoubleStatistics, DoubleStatistics, Number> fn) {
    return StackCounter.toString(this, other, fn);
  }

  public static class StackFrame {
    public final String declaringClass;
    public final String fileName;
    public final int lineNumber;
    public final String methodName;

    public StackFrame(@Nonnull final StackTraceElement frame) {
      this(frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber());
    }

    public StackFrame(final String declaringClass, final String methodName, final String fileName, final int lineNumber) {
      this.declaringClass = declaringClass;
      this.methodName = methodName;
      this.fileName = fileName;
      this.lineNumber = lineNumber;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof StackFrame)) return false;

      @Nonnull final StackFrame that = (StackFrame) o;

      if (lineNumber != that.lineNumber) return false;
      if (declaringClass != null ? !declaringClass.equals(that.declaringClass) : that.declaringClass != null) {
        return false;
      }
      if (methodName != null ? !methodName.equals(that.methodName) : that.methodName != null) return false;
      return fileName != null ? fileName.equals(that.fileName) : that.fileName == null;
    }

    @Override
    public int hashCode() {
      int result = declaringClass != null ? declaringClass.hashCode() : 0;
      result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
      result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
      result = 31 * result + lineNumber;
      return result;
    }

    @Override
    public String toString() {
      return String.format("%s.%s(%s:%s)", declaringClass, methodName, fileName, lineNumber);
    }
  }
}
