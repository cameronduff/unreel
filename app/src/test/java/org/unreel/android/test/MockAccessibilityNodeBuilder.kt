package org.unreel.android.test

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.robolectric.Shadows

class MockAccessibilityNodeBuilder {
    private var viewIdResourceName: String? = null
    private var text: CharSequence? = null
    private var contentDescription: CharSequence? = null
    private var className: CharSequence? = null
    private var isSelected: Boolean = false
    private var isVisibleToUser: Boolean = true
    private var boundsInScreen: android.graphics.Rect? = null
    private val children = mutableListOf<MockAccessibilityNodeBuilder>()

    fun setViewId(id: String?) = apply { this.viewIdResourceName = id }
    fun setText(text: CharSequence?) = apply { this.text = text }
    fun setContentDescription(desc: CharSequence?) = apply { this.contentDescription = desc }
    fun setClassName(className: CharSequence?) = apply { this.className = className }
    fun setSelected(selected: Boolean) = apply { this.isSelected = selected }
    fun setVisibleToUser(visible: Boolean) = apply { this.isVisibleToUser = visible }
    fun setBounds(bounds: android.graphics.Rect) = apply { this.boundsInScreen = bounds }

    fun addChild(child: MockAccessibilityNodeBuilder) = apply {
        children.add(child)
    }

    fun build(): AccessibilityNodeInfoCompat {
        val node = AccessibilityNodeInfo.obtain()
        node.viewIdResourceName = viewIdResourceName
        node.text = text
        node.contentDescription = contentDescription
        node.className = className
        node.isSelected = isSelected
        node.isVisibleToUser = isVisibleToUser
        boundsInScreen?.let { node.setBoundsInScreen(it) }

        val shadow = Shadows.shadowOf(node)
        for (childBuilder in children) {
            val childCompat = childBuilder.build()
            shadow.addChild(childCompat.unwrap() as AccessibilityNodeInfo)
        }

        return AccessibilityNodeInfoCompat.wrap(node)
    }
}
