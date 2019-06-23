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
import javax.annotation.Nullable;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.function.Supplier;

public enum PersistanceMode {
  SOFT {
    @Override
    public <T> Supplier<T> wrap(T obj) {
      return new SoftReference<>(obj)::get;
    }
  },
  WEAK {
    @Override
    public <T> Supplier<T> wrap(T obj) {
      return new WeakReference<>(obj)::get;
    }
  },
  STRONG {
    @Nonnull
    @Override
    public <T> Supplier<T> wrap(@Nonnull T obj) {
      return () -> obj;
    }
  },
  NULL {
    @Nullable
    @Override
    public <T> Supplier<T> wrap(T obj) {
      return () -> null;
    }
  };

  @Nullable
  public abstract <T> Supplier<T> wrap(T obj);
}
