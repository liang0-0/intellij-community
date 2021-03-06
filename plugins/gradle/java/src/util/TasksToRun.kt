// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

abstract class TasksToRun(val tasks: List<String>) : List<String> by tasks {

  abstract val source: VirtualFile

  abstract val module: Module

  abstract val testName: String

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return when (other) {
      is TasksToRun -> other.tasks == tasks
      is List<*> -> other == tasks
      else -> false
    }
  }

  override fun hashCode(): Int {
    return tasks.hashCode()
  }

  override fun toString(): String {
    return tasks.toString()
  }

  class Impl(override val source: VirtualFile, override val module: Module, override val testName: String, tasks: List<String>) : TasksToRun(tasks)

  object Empty : TasksToRun(emptyList()) {
    override val source: VirtualFile
      get() = throw UnsupportedOperationException()

    override val module: Module
      get() = throw UnsupportedOperationException()

    override val testName: String
      get() = throw UnsupportedOperationException()
  }

  companion object {
    @JvmField
    val EMPTY = Empty
  }
}
