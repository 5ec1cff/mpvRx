/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {
  @get:Rule val rule = MacrobenchmarkRule()

  @Before fun prepare() = prepareLibrary()

  @Test fun coldStartupWithoutProfile() = startup(CompilationMode.None())

  @Test fun coldStartupWithProfile() = startup(
    CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require, warmupIterations = 0),
  )

  private fun startup(compilation: CompilationMode) = rule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(StartupTimingMetric()),
    compilationMode = compilation,
    startupMode = StartupMode.COLD,
    iterations = 10,
    setupBlock = { pressHome() },
  ) {
    startActivityAndWait()
    device.waitForLibrary()
  }

  @Test fun libraryScrolling() = rule.measureRepeated(
    packageName = TARGET_PACKAGE,
    metrics = listOf(FrameTimingMetric()),
    compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    iterations = 5,
    setupBlock = {
      pressHome()
      startActivityAndWait()
      device.waitForLibrary()
    },
  ) {
    device.browseLibrary()
  }
}
