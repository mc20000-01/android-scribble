package com.example.androidscribble.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

class TextInjector(private val service: AccessibilityService) {
    fun insertText(text: String): Boolean {
        val target = focusedEditable() ?: return false
        val current = target.text?.toString().orEmpty()
        val from = target.textSelectionStart.coerceAtLeast(0)
        val to = target.textSelectionEnd.coerceAtLeast(from)
        val updated = current.replaceRange(from, to, text)
        return target.setText(updated, from + text.length)
    }

    fun insertSpace(): Boolean = insertText(" ")

    fun scratchDeleteWord(): Boolean {
        val target = focusedEditable() ?: return sendDeleteKey()
        val current = target.text?.toString().orEmpty()
        val cursor = target.textSelectionStart.coerceAtLeast(0).coerceAtMost(current.length)
        val start = current.lastIndexOf(' ', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = current.indexOf(' ', cursor).let { if (it < 0) current.length else it }
        if (start >= end) return sendDeleteKey()
        return target.setText(current.removeRange(start, end), start)
    }

    fun selectWordNearCursor(): Boolean {
        val target = focusedEditable() ?: return false
        val text = target.text?.toString().orEmpty()
        val cursor = target.textSelectionStart.coerceAtLeast(0).coerceAtMost(text.length)
        val start = text.lastIndexOf(' ', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf(' ', cursor).let { if (it < 0) text.length else it }
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: root.findAccessibilityNodeInfosByText("").firstOrNull { it.isEditable && it.isFocused }
    }

    private fun AccessibilityNodeInfo.setText(value: String, cursor: Int): Boolean {
        val setText = performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        })
        val setCursor = performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        })
        return setText && setCursor
    }

    private fun sendDeleteKey(): Boolean = false
}
