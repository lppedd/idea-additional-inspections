package com.github.lppedd.inspections

import com.intellij.BundleBase
import com.intellij.reference.SoftReference
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.util.*
import java.lang.ref.SoftReference as JavaSoftReference

/**
 * @author Edoardo Luppi
 */
object InspectionsBundle {
  private const val BUNDLE = "messages.AdditionalInspections"

  private var bundleReference: Reference<ResourceBundle>? = null
  private val bundle: ResourceBundle by lazy {
    SoftReference.dereference(bundleReference)
      ?: ResourceBundle.getBundle(BUNDLE).also {
        bundleReference = JavaSoftReference(it)
      }
  }

  @JvmStatic
  operator fun get(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    BundleBase.message(bundle, key, *params)
}
