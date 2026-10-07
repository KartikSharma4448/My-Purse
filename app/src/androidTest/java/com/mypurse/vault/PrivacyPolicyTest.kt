package com.mypurse.vault

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mypurse.vault.ui.ImportDialog
import com.mypurse.vault.ui.PrivacyPolicyButton
import org.junit.Rule
import org.junit.Test

class PrivacyPolicyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullPolicyIsAvailableOfflineAndDismisses() {
        compose.setContent { MaterialTheme { PrivacyPolicyButton() } }
        compose.onNodeWithText("Privacy policy").performClick()
        compose.onNodeWithText("My Purse Privacy Policy", substring = true).assertExists()
        compose.onNodeWithText("kartikuma9261@gmail.com", substring = true).assertExists()
        compose.onNodeWithText("Retention and deletion", substring = true).assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Retention and deletion", substring = true).assertDoesNotExist()
    }

    @Test fun documentImportKeepsCategoryAfterViewingPolicy() {
        compose.setContent { MaterialTheme { ImportDialog({}, {}) } }
        compose.onNodeWithText("Certificates").performClick()
        compose.onNodeWithText("Privacy policy").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNode(isSelected() and hasAnySibling(hasText("Certificates")), useUnmergedTree = true).assertExists()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
    }
}
