package com.github.lppedd.inspections;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import com.intellij.BundleBase;
import com.intellij.reference.SoftReference;

/**
 * @author Edoardo Luppi
 */
public final class InspectionsBundle {
  private static final String BUNDLE = "messages.AdditionalInspections";
  private static Reference<ResourceBundle> bundleReference;

  public static @NotNull String get(
      @PropertyKey(resourceBundle = BUNDLE) final @NotNull String key,
      final Object... params) {
    return BundleBase.message(getBundle(), key, params);
  }

  private static @NotNull ResourceBundle getBundle() {
    var bundle = SoftReference.dereference(bundleReference);

    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      bundleReference = new java.lang.ref.SoftReference<>(bundle);
    }

    return bundle;
  }
}
