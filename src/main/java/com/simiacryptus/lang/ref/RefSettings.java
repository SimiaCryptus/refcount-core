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

import com.simiacryptus.lang.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Cuda settings.
 */
public class RefSettings implements Settings {

  private static final Logger logger = LoggerFactory.getLogger(RefSettings.class);
  private static transient RefSettings INSTANCE = null;

  private final boolean lifecycleDebug;
  private final PersistanceMode doubleCacheMode;

  protected RefSettings() {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(Settings.get("THREADS", 64)));
    this.lifecycleDebug = Settings.get("DEBUG_LIFECYCLE", false);
    this.doubleCacheMode = Settings.get("DOUBLE_CACHE_MODE", PersistanceMode.WEAK);
  }

  /**
   * The constant INSTANCE.
   *
   * @return the core settings
   */
  public static RefSettings INSTANCE() {
    if (null == INSTANCE) {
      synchronized (RefSettings.class) {
        if (null == INSTANCE) {
          INSTANCE = new RefSettings();
          logger.info(String.format("Initialized %s = %s", INSTANCE.getClass().getSimpleName(), Settings.toJson(INSTANCE)));
        }
      }
    }
    return INSTANCE;
  }

  /**
   * Is lifecycle debug boolean.
   *
   * @param obj
   * @return the boolean
   */
  public boolean isLifecycleDebug(ReferenceCountingBase obj) {
//    if (obj.getClass().getName().endsWith("DeltaSet")) return true;
    return lifecycleDebug;
  }

  /**
   * Gets double cacheLocal mode.
   *
   * @return the double cacheLocal mode
   */
  public PersistanceMode getDoubleCacheMode() {
    return doubleCacheMode;
  }

}
