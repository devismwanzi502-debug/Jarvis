package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set

        const val ACTION_ACCESSIBILITY_COMMAND = "com.example.JARVIS_ACCESSIBILITY_COMMAND"
        const val EXTRA_COMMAND_TYPE = "extra_command_type"
        const val COMMAND_GLOBAL_BACK = "global_back"
        const val COMMAND_GLOBAL_HOME = "global_home"
        const val COMMAND_FIND_AND_CLICK = "find_and_click"
        const val COMMAND_INPUT_TEXT = "input_text"
        const val EXTRA_TARGET_TEXT = "extra_target_text"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("JARVIS_ACCESSIBILITY", "Accessibility Service Connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkgName = it.packageName?.toString() ?: ""
                Log.d("JARVIS_ACCESSIBILITY", "Window changed: $pkgName")
            }
        }
    }

    override fun onInterrupt() {
        Log.w("JARVIS_ACCESSIBILITY", "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    /**
     * Reads and collects all visible text strings from the current active window screen tree.
     */
    fun readCurrentScreenText(): List<String> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val textList = mutableListOf<String>()
        traverseAndCollectText(rootNode, textList)
        return textList
    }

    private fun traverseAndCollectText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        node ?: return
        if (!node.text.isNullOrBlank()) {
            list.add(node.text.toString())
        } else if (!node.contentDescription.isNullOrBlank()) {
            list.add(node.contentDescription.toString())
        }

        for (i in 0 until node.childCount) {
            traverseAndCollectText(node.getChild(i), list)
        }
    }

    /**
     * Finds nodes by text or description and performs a click action.
     */
    fun findAndClickNodeByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                } else {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return true
                        }
                        parent = parent.parent
                    }
                }
            }
        }
        return false
    }

    /**
     * Performs a vertical swipe up gesture (e.g. scrolling TikTok videos / YouTube Shorts).
     */
    fun performSwipeUp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels

            val path = android.graphics.Path().apply {
                moveTo(width / 2f, height * 0.80f)
                lineTo(width / 2f, height * 0.20f)
            }

            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    /**
     * Attempts to find and click a Like button or heart icon on screen.
     */
    fun findAndClickLikeButton(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val likeKeywords = listOf("like", "thumbs up", "favorite", "love", "heart", "like video", "like post")
        for (keyword in likeKeywords) {
            if (findAndClickNodeByText(keyword)) {
                return true
            }
        }
        return false
    }

    /**
     * Finds search bar/icon, clicks it, and enters query text.
     */
    fun searchAndInputInActiveApp(queryText: String): Boolean {
        val searchKeywords = listOf("search", "search youtube", "search videos", "type to search", "search or type url")
        for (kw in searchKeywords) {
            findAndClickNodeByText(kw)
        }
        return inputTextIntoFocusedField(queryText)
    }

    /**
     * Inputs text into the currently focused or first available editable field.
     */
    fun inputTextIntoFocusedField(textToInput: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToInput)
            return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        return false
    }

    fun performGlobalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performGlobalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performGlobalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
}


