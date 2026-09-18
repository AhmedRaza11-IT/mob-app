package com.whatsapp.clone

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End Jetpack Compose UI Instrumentation Test Suite for VibeSync Client
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA
    )

    @Before
    fun setUp() {
        composeTestRule.setContent {
            WhatsAppTheme {
                WhatsAppMainScreen()
            }
        }
    }

    @Test
    fun testTextMessageSendAndAppearance() {
        // 1. Open chat room
        composeTestRule.onNodeWithText("Alice Smith").performClick()

        val testMessage = "Hello automated test"

        // 2. Locate input field by tag and type message
        composeTestRule.onNodeWithTag("chat_input_field")
            .performTextInput(testMessage)

        composeTestRule.waitForIdle()

        // 3. Locate send button by tag and click
        composeTestRule.onNodeWithTag("send_button")
            .performClick()

        composeTestRule.waitForIdle()

        // 4. Verify sent text bubble exists on screen
        composeTestRule.onNodeWithText(testMessage)
            .assertIsDisplayed()
    }

    @Test
    fun testEmojiDrawerSelectionAndFieldInsertion() {
        // 1. Open chat room
        composeTestRule.onNodeWithText("Alice Smith").performClick()

        // 2. Open Emoji Drawer
        composeTestRule.onNode(
            hasContentDescription("Emoji", substring = true, ignoreCase = true)
        ).performClick()

        composeTestRule.waitForIdle()

        // 3. Disambiguate by selecting the first matching emoji from the grid
        composeTestRule.onAllNodesWithText("🔥")
            .onFirst()
            .performClick()

        // 4. Verify input field receives emoji
        composeTestRule.onNodeWithTag("chat_input_field")
            .assertTextContains("🔥")
    }

    @Test
    fun testVoiceNotePermissionAndStateValidation() {
        // 1. Open chat room
        composeTestRule.onNodeWithText("Alice Smith").performClick()

        // 2. Clear input to ensure mic is available
        composeTestRule.onNodeWithTag("chat_input_field")
            .performTextClearance()

        composeTestRule.waitForIdle()

        // 3. Click mic button
        composeTestRule.onNodeWithTag("mic_button")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        // 4. Assert state changed to recording or stop
        composeTestRule.onNode(
            hasTestTag("stop_button") or
            hasContentDescription("Stop", substring = true, ignoreCase = true)
        ).assertExists()
    }
}
