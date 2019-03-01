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

import javax.annotation.Nonnull;

/**
 * A runtime exception when performing an invalid operation on a ReferenceCounted object.
 */
public class LifecycleException extends RuntimeException {
  /**
   * The Obj.
   */
  @Nonnull
  public final ReferenceCounting obj;

  /**
   * Instantiates a new Lifecycle exception.
   *
   * @param obj the obj
   */
  public LifecycleException(@Nonnull ReferenceCountingBase obj) {
    super("Lifecycle Exception: " + ReferenceCountingBase.referenceReport(obj, false));
    this.obj = obj;
  }

}