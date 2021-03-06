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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.concurrent.Executors.newFixedThreadPool;

public abstract class ReferenceCountingBase implements ReferenceCounting {

  private static final Logger logger = LoggerFactory.getLogger(ReferenceCountingBase.class);
  private static final long LOAD_TIME = System.nanoTime();
  private static final UUID jvmId = UUID.randomUUID();
  private static final ExecutorService gcPool = newFixedThreadPool(1, new ThreadFactoryBuilder()
      .setDaemon(true).build());
  private static final ThreadLocal<Boolean> inFinalizer = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return false;
    }
  };
  public static boolean supressLog = false;

  static {
    if (RefSettings.INSTANCE() == null) throw new RuntimeException();
  }

  private transient final UUID objectId = RefSettings.INSTANCE().isLifecycleDebug(this) ? UUID.randomUUID() : jvmId;
  private transient final AtomicInteger references = new AtomicInteger(1);
  private transient final AtomicBoolean isFreed = new AtomicBoolean(false);
  @Nullable
  private transient final StackTraceElement[] refCreatedBy = RefSettings.INSTANCE().isLifecycleDebug(this) ? Thread.currentThread().getStackTrace() : null;
  private transient final LinkedList<StackTraceElement[]> addRefs = new LinkedList<>();
  private transient final LinkedList<StackTraceElement[]> freeRefs = new LinkedList<>();
  private transient volatile boolean isFinalized = false;
  private transient boolean detached = false;

  @Nonnull
  private static String getString(@Nullable StackTraceElement[] trace) {
    return null == trace ? "" : Arrays.stream(trace).map(x -> "at " + x).skip(2).reduce((a, b) -> a + "\n" + b).orElse("");
  }

  public static CharSequence referenceReport(@Nonnull ReferenceCountingBase obj, boolean includeCaller) {
    return obj.referenceReport(includeCaller, obj.isFinalized());
  }

  public static StackTraceElement[] removeSuffix(final StackTraceElement[] stack, final Collection<StackTraceElement> prefix) {
    return Arrays.stream(stack).limit(stack.length - prefix.size()).toArray(i -> new StackTraceElement[i]);
  }

  public static List<StackTraceElement> findCommonPrefix(final List<List<StackTraceElement>> reversedStacks) {
    if (0 == reversedStacks.size()) return null;
    List<StackTraceElement> protoprefix = reversedStacks.get(0);
    for (int i = 0; i < protoprefix.size(); i++) {
      final int finalI = i;
      if (!reversedStacks.stream().allMatch(x -> x.size() > finalI && x.get(finalI).equals(protoprefix.get(finalI)))) {
        return protoprefix.subList(0, i);
      }
    }
    return protoprefix;
  }

  public static <T> List<T> reverseCopy(final List<T> x) {
    if (null == x) return Arrays.asList();
    return IntStream.range(0, x.size()).map(i -> (x.size() - 1) - i).mapToObj(i -> x.get(i)).collect(Collectors.toList());
  }

  public static <T> List<T> reverseCopy(final T[] x) {
    return IntStream.range(0, x.length).map(i -> (x.length - 1) - i).mapToObj(i -> x[i]).collect(Collectors.toList());
  }

  protected final Object readResolve() throws ObjectStreamException {
    return detach();
  }

  @Override
  public int currentRefCount() {
    return references.get();
  }

  @Override
  public boolean tryAddRef() {
    if (references.updateAndGet(i -> i > 0 ? i + 1 : 0) == 0) {
      return false;
    }
    addRefs.add(RefSettings.INSTANCE().isLifecycleDebug(this) ? Thread.currentThread().getStackTrace() : new StackTraceElement[]{});
    return true;
  }

  @Override
  public ReferenceCounting addRef() {
    if (references.updateAndGet(i -> i > 0 ? i + 1 : 0) == 0) throw new IllegalStateException(referenceReport(true, isFinalized()));
    addRefs.add(RefSettings.INSTANCE().isLifecycleDebug(this) ? Thread.currentThread().getStackTrace() : new StackTraceElement[]{});
    return this;
  }

  public final boolean isFinalized() {
    return isFreed.get();
  }

  public String referenceReport(boolean includeCaller, boolean isFinalized) {
    @Nonnull ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    @Nonnull PrintStream out = new PrintStream(buffer);
    out.print(String.format("Object %s %s (%d refs, %d frees) ",
        getClass().getName(), getObjectId().toString(), 1 + addRefs.size(), freeRefs.size()));
    List<StackTraceElement> prefix = reverseCopy(findCommonPrefix(Stream.concat(
        Stream.<StackTraceElement[]>of(refCreatedBy),
        Stream.concat(
            addRefs.stream(),
            freeRefs.stream()
        )
    ).filter(x -> x != null).map(x -> reverseCopy(x)).collect(Collectors.toList())));

    if (null != refCreatedBy) {
      StackTraceElement[] trace = this.refCreatedBy;
      //trace = removeSuffix(trace, prefix);
      out.println(String.format("created by \n\t%s",
          getString(trace).replaceAll("\n", "\n\t")));
    }
    synchronized (addRefs) {

      for (int i = 0; i < addRefs.size(); i++) {
        StackTraceElement[] stack = i < addRefs.size() ? addRefs.get(i) : new StackTraceElement[]{};
        stack = removeSuffix(stack, prefix);
        final String string = getString(stack);
        if (!string.trim().isEmpty()) out.println(String.format("reference added by %s\n\t%s", "",
            string.replaceAll("\n", "\n\t")));
      }
    }
    synchronized (freeRefs) {
      for (int i = 0; i < freeRefs.size() - (isFinalized ? 1 : 0); i++) {
        StackTraceElement[] stack = i < freeRefs.size() ? freeRefs.get(i) : new StackTraceElement[]{};
        stack = removeSuffix(stack, prefix);
        final String string = getString(stack);
        if (!string.trim().isEmpty()) out.println(String.format("reference removed by %s\n\t%s", "",
            string.replaceAll("\n", "\n\t")));
      }
      if (isFinalized && 0 < freeRefs.size()) {
        StackTraceElement[] stack = freeRefs.get(freeRefs.size() - 1);
        stack = removeSuffix(stack, prefix);
        final String string = 0 == freeRefs.size() ? "" : getString(stack);
        if (!string.trim().isEmpty()) out.println(String.format("freed by %s\n\t%s", "",
            string.replaceAll("\n", "\n\t")));
      }
    }
    if (includeCaller) out.println(String.format("apply current stack \n\t%s",
        getString(Thread.currentThread().getStackTrace()).replaceAll("\n", "\n\t")));
    out.close();
    return buffer.toString();
  }

  public boolean assertAlive() {
    if (isFinalized) {
      throw new LifecycleException(this);
    }
    if (isFinalized() && !inFinalizer.get()) {
      logger.warn(String.format("Using freed reference for %s", getClass().getSimpleName()));
      logger.warn(referenceReport(true, isFinalized()));
      throw new LifecycleException(this);
    }
    return true;
  }

  @Override
  public void freeRefAsync() {
    gcPool.submit((Runnable) this::freeRef);
  }

  @Override
  public int freeRef() {
    if (isFinalized) {
      //logger.debug("Object has been finalized");
      return 0;
    }
    int refs = references.decrementAndGet();
    if (refs < 0 && !detached) {
      boolean isInFinalizer = Arrays.stream(Thread.currentThread().getStackTrace()).filter(x -> x.getClassName().equals("java.lang.ref.Finalizer")).findAny().isPresent();
      if (!isInFinalizer) {
        logger.warn(String.format("Error freeing reference for %s", getClass().getSimpleName()));
        logger.warn(referenceReport(true, isFinalized()));
        throw new LifecycleException(this);
      } else {
        return refs;
      }
    }

    synchronized (freeRefs) {
      freeRefs.add(RefSettings.INSTANCE().isLifecycleDebug(this) ? Thread.currentThread().getStackTrace() : new StackTraceElement[]{});
    }
    if (refs == 0 && !detached) {
      if (!isFreed.getAndSet(true)) {
        try {
          _free();
        } catch (LifecycleException e) {
          if (!inFinalizer.get()) logger.info("Error freeing resources: " + referenceReport(true, isFinalized()));
          throw e;
        }
      }
    }
    return refs;
  }

  protected void _free() {
  }

  @Override
  protected final void finalize() {
    isFinalized = true;
    if (!isFreed.getAndSet(true)) {
      if (!isDetached() && !supressLog) {
        if (logger.isDebugEnabled()) {
          logger.debug(String.format("Instance Reclaimed by GC at %.9f: %s", (System.nanoTime() - LOAD_TIME) / 1e9, referenceReport(false, false)));
        }
      }
      synchronized (freeRefs) {
        freeRefs.add(RefSettings.INSTANCE().isLifecycleDebug(this) ? Thread.currentThread().getStackTrace() : new StackTraceElement[]{});
      }
      inFinalizer.set(true);
      try {
        _free();
      } finally {
        inFinalizer.set(false);
      }
    }
  }

  public boolean isDetached() {
    return detached;
  }

  public ReferenceCountingBase detach() {
    this.detached = true;
    return this;
  }

  @Override
  public UUID getObjectId() {
    return objectId;
  }
}
