package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AppTab
import com.example.ui.WorkbenchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("EFRAIM APK WORKBENCH TOOL M", appName)
  }

  @Test
  fun `verify settings navigation and creator credit context`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = WorkbenchViewModel(context)
    viewModel.selectTab(AppTab.SETTINGS)
    assertEquals(AppTab.SETTINGS, viewModel.selectedTab.value)
    assertNotNull(viewModel.getAiMode())
  }
}
