package com.github.lppedd.inspections;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.util.ObjectUtils;

/**
 * @author Edoardo Luppi
 */
public final class Utils {
  public static @NotNull <T> T castOrFail(
      final @Nullable Object obj,
      final @NotNull Class<T> clazz) {
    final var errorMessage = "Could not cast object to " + clazz.getSimpleName();
    return Objects.requireNonNull(ObjectUtils.tryCast(obj, clazz), errorMessage);
  }
}
