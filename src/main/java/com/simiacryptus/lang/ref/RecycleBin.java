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

package com.simiacryptus.lang.ref;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.simiacryptus.lang.StackCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.simiacryptus.lang.ref.PersistanceMode.WEAK;

public abstract class RecycleBin<T> {

  public static final RecycleBin<double[]> DOUBLES = new RecycleBin<double[]>() {
    @Override
    protected void free(double[] obj) {
    }

    @Override
    public double[] create(final long length) {
      return new double[(int) length];
    }

    @Override
    public void reset(@Nonnull final double[] data, long size) {
      assert data.length == size;
      Arrays.fill(data, 0);
    }
  }.setPersistanceMode(RefSettings.INSTANCE().getDoubleCacheMode());
  public static final RecycleBin<float[]> FLOATS = new RecycleBin<float[]>() {
    @Override
    protected void free(float[] obj) {
    }

    @Override
    public float[] create(final long length) {
      return new float[(int) length];
    }

    @Override
    public void reset(@Nonnull final float[] data, long size) {
      assert data.length == size;
      Arrays.fill(data, 0);
    }
  }.setPersistanceMode(RefSettings.INSTANCE().getDoubleCacheMode());
  protected static final Logger logger = LoggerFactory.getLogger(RecycleBin.class);
  private static volatile ScheduledExecutorService garbageTruck;
  private final Map<Long, ConcurrentLinkedDeque<ObjectWrapper>> buckets = new ConcurrentHashMap<>();
  private final StackCounter allocations = new StackCounter();
  private final StackCounter frees = new StackCounter();
  private final StackCounter recycle_put = new StackCounter();
  private final StackCounter recycle_get = new StackCounter();
  private int purgeFreq;
  private int profilingThreshold = Integer.MAX_VALUE;
  private PersistanceMode persistanceMode = WEAK;
  private int minLengthPerBuffer = 16;
  private double maxLengthPerBuffer = 1e9;
  private int maxItemsPerBuffer = 100;

  protected RecycleBin() {
    super();
    purgeFreq = 10;
    RecycleBin.getGarbageTruck().scheduleAtFixedRate(() -> {
      buckets.forEach((k, v) -> {
        ObjectWrapper poll;
        ArrayList<ObjectWrapper> young = new ArrayList<>();
        while (null != (poll = v.poll())) {
          if (poll.age() > purgeFreq) {
            T obj = poll.obj.get();
            if (obj != null) {
              freeItem(obj, k);
            }
          } else {
            young.add(poll);
          }
        }
        v.addAll(young);
      });
    }, purgeFreq, purgeFreq, TimeUnit.SECONDS);
  }

  public static ScheduledExecutorService getGarbageTruck() {
    if (null == RecycleBin.garbageTruck) {
      synchronized (RecycleBin.class) {
        if (null == RecycleBin.garbageTruck) {
          RecycleBin.garbageTruck = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setDaemon(true).build());
        }
      }
    }
    return RecycleBin.garbageTruck;
  }

  public static boolean equals(@Nullable Object a, @Nullable Object b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return a.equals(b);
  }

  public long clear() {
    Map<Long, ConcurrentLinkedDeque<ObjectWrapper>> buckets = this.buckets;
    if (null == buckets) return 0;
    return buckets.keySet().stream().mapToLong(length -> {
      ConcurrentLinkedDeque<ObjectWrapper> remove = buckets.remove(length);
      if (null == remove || remove.isEmpty()) return 0;
      return remove.stream().mapToLong(ref -> {
        return freeItem(ref.obj.get(), length);
      }).sum();
    }).sum();
  }

  protected long freeItem(T obj, long size) {
    @Nullable StackCounter stackCounter = getFrees(size);
    if (null != stackCounter) {
      stackCounter.increment(size);
    }
    if (null != obj) free(obj);
    return size;
  }

  protected abstract void free(T obj);

  public T obtain(final long length) {
    final ConcurrentLinkedDeque<ObjectWrapper> bin = buckets.get(length);
    @Nullable StackCounter stackCounter = getRecycle_get(length);
    if (null != stackCounter) {
      stackCounter.increment(length);
    }
    if (null != bin) {
      final ObjectWrapper ref = bin.poll();
      if (null != ref) {
        final T data = ref.obj.get();
        if (null != data) {
          reset(data, length);
          return data;
        }
      }
    }
    return create(length, 1);
  }

  @Nullable
  public T copyOf(@Nullable final T original, long size) {
    if (null == original) return null;
    final T copy = obtain(size);
    System.arraycopy(original, 0, copy, 0, (int) size);
    return copy;
  }

  @Nonnull
  public abstract T create(long length);

  @Nullable
  public StackCounter getAllocations(final long length) {
    if (!isProfiling(length)) return null;
    return allocations;
  }

  @Nullable
  public StackCounter getFrees(final long length) {
    if (!isProfiling(length)) return null;
    return frees;
  }

  @Nullable
  public StackCounter getRecycle_put(final long length) {
    if (!isProfiling(length)) return null;
    return recycle_put;
  }

  @Nullable
  public StackCounter getRecycle_get(final long length) {
    if (!isProfiling(length)) return null;
    return recycle_get;
  }

  public boolean isProfiling(final long length) {
    return length > profilingThreshold;
  }

  public void printAllProfiling(@Nonnull final PrintStream out) {
    printDetailedProfiling(out);
    printNetProfiling(out);
  }

  public void printDetailedProfiling(@Nonnull final PrintStream out) {
    if (null != allocations) {
      out.println("Memory Allocation Profiling:\n\t" + allocations.toString().replaceAll("\n", "\n\t"));
    }
    if (null != frees) {
      out.println("Freed Memory Profiling:\n\t" + frees.toString().replaceAll("\n", "\n\t"));
    }
    if (null != recycle_put) {
      out.println("Recycle Bin (Put) Profiling:\n\t" + recycle_put.toString().replaceAll("\n", "\n\t"));
    }
    if (null != recycle_get) {
      out.println("Recycle Bin (Get) Profiling:\n\t" + recycle_get.toString().replaceAll("\n", "\n\t"));
    }
  }

  public void printNetProfiling(@Nullable final PrintStream out) {
    if (null != out && null != recycle_put && null != recycle_get) {
      out.println("Recycle Bin (Net) Profiling:\n\t" +
          StackCounter.toString(recycle_put, recycle_get, (a, b) -> a.getSum() - b.getSum())
              .replaceAll("\n", "\n\t"));
    }
  }

  public void recycle(@Nullable final T data, long size) {
    if (null != data && size >= getMinLengthPerBuffer() && size <= getMaxLengthPerBuffer()) {
      @Nullable StackCounter stackCounter = getRecycle_put(size);
      if (null != stackCounter) {
        stackCounter.increment(size);
      }
      ConcurrentLinkedDeque<ObjectWrapper> bin = getBin(size);
      if (bin.size() < Math.min(Math.max(1, (int) (getMaxLengthPerBuffer() / size)), getMaxItemsPerBuffer())) {
//        synchronized (bin) {
//        }
        boolean present = bin.stream().filter(x -> equals(x.obj.get(), data)).findAny().isPresent();
        if (present) throw new IllegalStateException();
        else {
          bin.add(new ObjectWrapper(wrap(data)));
          return;
        }
      }
    }
    freeItem(data, size);
  }

  @Nonnull
  public T create(long length, int retries) {
    try {
      @Nonnull T result = create(length);
      @Nullable StackCounter stackCounter = getAllocations(length);
      if (null != stackCounter) {
        stackCounter.increment(length);
      }
      return result;
    } catch (@Nonnull final Throwable e) {
      if (retries <= 0) throw new RuntimeException(String.format("Could not allocate %d bytes", length), e);
    }
    clearMemory(length);
    return create(length, retries - 1);
  }

  private void clearMemory(long length) {
    long max = Runtime.getRuntime().maxMemory();
    long previous = Runtime.getRuntime().freeMemory();
    long size = getSize();
    logger.warn(String.format("Allocation of length %d failed; %s/%s used memory; %s items in recycle buffer; Clearing memory", length, previous, max, size));
    clear();
    System.gc();
    long after = Runtime.getRuntime().freeMemory();
    logger.warn(String.format("Clearing memory freed %s/%s bytes", previous - after, max));
  }

  public long getSize() {
    return this.buckets.entrySet().stream().mapToLong(e -> e.getKey() * e.getValue().size()).sum();
  }

  public boolean want(long size) {
    if (size < getMinLengthPerBuffer()) return false;
    if (size > getMaxLengthPerBuffer()) return false;
    @Nullable StackCounter stackCounter = getRecycle_put(size);
    if (null != stackCounter) {
      stackCounter.increment(size);
    }
    ConcurrentLinkedDeque<ObjectWrapper> bin = getBin(size);
    return bin.size() < Math.min(Math.max(1, (int) (getMaxLengthPerBuffer() / size)), getMaxItemsPerBuffer());
  }

  protected ConcurrentLinkedDeque<ObjectWrapper> getBin(long size) {
    return buckets.computeIfAbsent(size, x -> new ConcurrentLinkedDeque<>());
  }

  public int getPurgeFreq() {
    return purgeFreq;
  }

  public RecycleBin<T> setPurgeFreq(int purgeFreq) {
    this.purgeFreq = purgeFreq;
    return this;
  }

  public PersistanceMode getPersistanceMode() {
    return persistanceMode;
  }

  @Nonnull
  public RecycleBin<T> setPersistanceMode(PersistanceMode persistanceMode) {
    this.persistanceMode = persistanceMode;
    return this;
  }

  @Nullable
  protected Supplier<T> wrap(T data) {
    return persistanceMode.wrap(data);
  }

  public abstract void reset(T data, long size);

  @Nonnull
  public RecycleBin<T> setProfiling(final int threshold) {
    this.profilingThreshold = threshold;
    return this;
  }

  public int getMinLengthPerBuffer() {
    return minLengthPerBuffer;
  }

  @Nonnull
  public RecycleBin<T> setMinLengthPerBuffer(int minLengthPerBuffer) {
    this.minLengthPerBuffer = minLengthPerBuffer;
    return this;
  }

  public double getMaxLengthPerBuffer() {
    return maxLengthPerBuffer;
  }

  @Nonnull
  public RecycleBin<T> setMaxLengthPerBuffer(double maxLengthPerBuffer) {
    this.maxLengthPerBuffer = maxLengthPerBuffer;
    return this;
  }

  public int getMaxItemsPerBuffer() {
    return maxItemsPerBuffer;
  }

  @Nonnull
  public RecycleBin<T> setMaxItemsPerBuffer(int maxItemsPerBuffer) {
    this.maxItemsPerBuffer = maxItemsPerBuffer;
    return this;
  }

  private class ObjectWrapper {
    public final Supplier<T> obj;
    public final long createdAt = System.nanoTime();

    private ObjectWrapper(final Supplier<T> obj) {
      this.obj = obj;
    }

    public final double age() {
      return (System.nanoTime() - createdAt) / 1e9;
    }
  }
}
