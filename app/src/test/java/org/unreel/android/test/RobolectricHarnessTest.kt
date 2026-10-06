package org.unreel.android.test

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.ArrayDeque

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RobolectricHarnessTest {

    @Test
    fun testMockNodeBuilderCreatesProperties() {
        val node = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/test_view")
            .setText("Sample Text")
            .setContentDescription("Sample Description")
            .setSelected(true)
            .build()

        assertNotNull(node)
        assertEquals("com.instagram.android:id/test_view", node.viewIdResourceName)
        assertEquals("Sample Text", node.text?.toString())
        assertEquals("Sample Description", node.contentDescription?.toString())
        assertTrue(node.isSelected)
    }

    @Test
    fun testParentChildTreeTraversal() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/root_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/child_one")
                    .setText("First Child")
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/child_two")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/grandchild")
                            .setText("Grandchild Node")
                    )
            )
            .build()

        assertEquals(2, root.childCount)
        assertEquals("com.instagram.android:id/child_one", root.getChild(0)?.viewIdResourceName)
        assertEquals("com.instagram.android:id/child_two", root.getChild(1)?.viewIdResourceName)
        assertEquals("com.instagram.android:id/grandchild", root.getChild(1)?.getChild(0)?.viewIdResourceName)

        // Test breadth-first search traversal
        val visitedIds = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            current?.viewIdResourceName?.let { visitedIds.add(it) }
            val count = current?.childCount ?: 0
            for (i in 0 until count) {
                current?.getChild(i)?.let { queue.add(it) }
            }
        }

        assertEquals(
            listOf(
                "com.instagram.android:id/root_layout",
                "com.instagram.android:id/child_one",
                "com.instagram.android:id/child_two",
                "com.instagram.android:id/grandchild"
            ),
            visitedIds
        )
    }

    @Test
    fun testNullAndEmptyAttributes() {
        val node = MockAccessibilityNodeBuilder().build()
        assertNull(node.viewIdResourceName)
        assertNull(node.text)
        assertNull(node.contentDescription)
        assertFalse(node.isSelected)
        assertEquals(0, node.childCount)
    }
}
