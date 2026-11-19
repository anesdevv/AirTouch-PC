package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AirTouch PC", appName)
  }

  @Test
  fun `trackpad shows left middle right and switches to presentation controls`() {
    composeRule.onNodeWithTag("left_click_button").assertIsDisplayed()
    composeRule.onNodeWithTag("middle_click_button").assertIsDisplayed()
    composeRule.onNodeWithTag("right_click_button").assertIsDisplayed()

    composeRule.onNodeWithTag("mode_presentation_tab").performClick()
    composeRule.onNodeWithTag("pres_next_button").assertIsDisplayed()
    composeRule.onNodeWithTag("pres_prev_button").assertIsDisplayed()
    composeRule.onNodeWithTag("pres_start_button").assertIsDisplayed()
    composeRule.onNodeWithTag("pres_end_button").assertIsDisplayed()
    composeRule.onNodeWithTag("pres_black_screen_button").assertIsDisplayed()
    composeRule.onNodeWithTag("pres_laser_pad").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Hold for laser pointer").assertIsDisplayed()
  }

  @Test
  fun `settings displays about section with version creator and github`() {
    composeRule.onNodeWithTag("preferences_button").performClick()
    composeRule.onNodeWithTag("settings_about_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("0.0.1").assertIsDisplayed()
    composeRule.onNodeWithText("Sebai Anes").assertIsDisplayed()
    composeRule.onNodeWithText("https://github.com/anesdevv").assertIsDisplayed()
  }
}
