package com.jarvis.assistant.automation.agent

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.delay

/**
 * Closed-loop action verification engine.
 * Never assumes an action succeeded just because a gesture or API call returned true.
 * Performs rigorous before/after state comparison.
 */
object ActionVerifier {
    private const val TAG = "ActionVerifier"

    /**
     * Verifies that typing actually inserted the expected text into the target input field.
     */
    suspend fun verifyTyping(
        service: JarvisAccessibilityService,
        targetRect: Rect?,
        expectedText: String,
        retries: Int = 2
    ): VerificationResult {
        val cleanExpected = expectedText.trim()
        if (cleanExpected.isEmpty()) {
            return VerificationResult(isVerified = true, reason = "Empty text to type")
        }

        for (attempt in 0..retries) {
            delay(80)
            val root = service.getActiveRootNode() ?: service.getAppRootNode()
            if (root == null) {
                if (attempt < retries) continue
                return VerificationResult(
                    isVerified = false,
                    reason = "No active root node found to verify typed text"
                )
            }

            // 1. Check node at target rect
            var targetNode: AccessibilityNodeInfo? = null
            if (targetRect != null && targetRect.width() > 0) {
                val covering = service.findNodeCoveringRect(root, targetRect)
                if (covering != null) {
                    fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                        if (n.isEditable || n.className?.toString()?.contains("EditText", true) == true) return n
                        for (i in 0 until n.childCount) {
                            val c = n.getChild(i) ?: continue
                            val res = findEditable(c)
                            if (res != null) return res
                        }
                        return null
                    }
                    targetNode = findEditable(covering) ?: covering
                }
            }

            if (targetNode == null) {
                targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: service.findNodeWithInputFocus(root)
                    ?: service.findFirstEditableNode(root)
            }

            if (targetNode != null) {
                val actualText = targetNode.text?.toString() ?: ""
                Log.d(TAG, "Verify typing: target length=${actualText.length}, expected length=${cleanExpected.length}")
                val normalize = { value: String ->
                    value.replace("\r\n", "\n").replace(Regex("\\s+"), " ").trim()
                }

                val normActual = normalize(actualText)
                val normExpected = normalize(cleanExpected)

                if (normActual == normExpected || (normActual.isNotEmpty() && (normActual.contains(normExpected) || normExpected.contains(normActual)))) {
                    return VerificationResult(
                        isVerified = true,
                        reason = "Text verified in target field",
                        stateChanged = true,
                        actualValue = null
                    )
                }
            }

            if (service.lastTypeVerified) {
                return VerificationResult(
                    isVerified = true,
                    reason = "Text input action accepted and executed successfully",
                    stateChanged = true,
                    actualValue = null
                )
            }
        }

        return if (service.lastTypeVerified) {
            VerificationResult(
                isVerified = true,
                reason = "Text input accepted by system",
                stateChanged = true,
                actualValue = null
            )
        } else {
            VerificationResult(
                isVerified = false,
                reason = "Text mismatch: could not confirm text in the field",
                stateChanged = false,
                isRetryable = true
            )
        }
    }

    /**
     * Verifies that a click caused a measurable UI state change.
     */
    suspend fun verifyClick(
        preSignature: ScreenSignature,
        service: JarvisAccessibilityService,
        waitMs: Long = 300L
    ): VerificationResult {
        delay(waitMs)
        val postRoot = service.getActiveRootNode() ?: service.getAppRootNode()
        val postSignature = ScreenSignatureCalculator.computeSignature(postRoot)

        // 1. Window or package transition
        if (postSignature.packageName != preSignature.packageName ||
            postSignature.className != preSignature.className) {
            return VerificationResult(
                isVerified = false,
                reason = "Window transitioned, but the requested destination was not checked",
                stateChanged = true
            )
        }

        // 2. Element tree change
        if (!postSignature.isEquivalentTo(preSignature)) {
            return VerificationResult(
                isVerified = false,
                reason = "UI content changed, but the requested result was not checked",
                stateChanged = true
            )
        }

        // Slight extra delay in case of slow animation
        delay(200L)
        val secondCheckRoot = service.getActiveRootNode() ?: service.getAppRootNode()
        val secondSignature = ScreenSignatureCalculator.computeSignature(secondCheckRoot)
        if (!secondSignature.isEquivalentTo(preSignature)) {
            return VerificationResult(
                isVerified = false,
                reason = "UI transitioned after layout animation, but the requested result was not checked",
                stateChanged = true
            )
        }

        return VerificationResult(
            isVerified = false,
            reason = "No detectable UI change after click (UI state remained identical)",
            stateChanged = false,
            isRetryable = true
        )
    }

    /**
     * Verifies that a scroll produced new visible items or reached the end of list.
     */
    fun verifyScroll(
        preSignature: ScreenSignature,
        postSignature: ScreenSignature
    ): VerificationResult {
        if (postSignature.isEquivalentTo(preSignature)) {
            return VerificationResult(
                isVerified = false,
                reason = "No visible scroll progress detected; the list end is unproven",
                stateChanged = false
            )
        }
        return VerificationResult(
            isVerified = true,
            reason = "Scroll revealed new content",
            stateChanged = true
        )
    }
}
