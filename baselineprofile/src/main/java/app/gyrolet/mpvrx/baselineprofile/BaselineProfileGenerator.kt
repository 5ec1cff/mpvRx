/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule val rule = BaselineProfileRule()

  @Before fun prepare() = prepareLibrary()

  @Test fun startup() = rule.collect(
    packageName = TARGET_PACKAGE,
    includeInStartupProfile = true,
  ) {
    pressHome()
    startActivityAndWait()
    device.waitForLibrary()
  }

  @Test fun libraryScrolling() = rule.collect(
    packageName = TARGET_PACKAGE,
    includeInStartupProfile = false,
  ) {
    pressHome()
    startActivityAndWait()
    device.browseLibrary()
  }
}
