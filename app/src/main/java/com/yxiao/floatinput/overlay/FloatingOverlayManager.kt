package com.yxiao.floatinput.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.yxiao.floatinput.R
import com.yxiao.floatinput.util.HapticHelper
import com.yxiao.floatinput.util.PreferencesHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingOverlayManager(private val context: Context) {

    private val TAG = "FloatingOverlayManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    private val prefs = PreferencesHelper.getInstance(context)

    // Themed context ensures Material/AppCompat attributes inflate safely in a Service
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_FloatInput)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = context.resources.displayMetrics.density

    // Views
    private var capsuleView: View? = null
    private var cardView: View? = null

    // Layout Params
    private lateinit var capsuleParams: WindowManager.LayoutParams
    private lateinit var cardParams: WindowManager.LayoutParams

    private var isExpanded = false
    private var isAttached = false
    private var isTransitioning = false

    // Screen dimensions
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var statusBarHeight = 0
    private var navBarHeight = 0

    // Callback for when user clicks close on the card
    var onCloseRequested: (() -> Unit)? = null

    init {
        updateScreenDimensions()
        initCapsule()
        initCard()
    }

    private fun updateScreenDimensions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = windowManager.currentWindowMetrics
                val bounds = metrics.bounds
                screenWidth = bounds.width()
                screenHeight = bounds.height()

                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
                )
                statusBarHeight = insets.top
                navBarHeight = insets.bottom
            } else {
                val display = windowManager.defaultDisplay
                val size = Point()
                @Suppress("DEPRECATION")
                display.getRealSize(size)
                screenWidth = size.x
                screenHeight = size.y
                statusBarHeight = (24 * density).toInt()
                navBarHeight = (48 * density).toInt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateScreenDimensions error", e)
            val dm = context.resources.displayMetrics
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
            statusBarHeight = (24 * dm.density).toInt()
            navBarHeight = (48 * dm.density).toInt()
        }
    }

    private fun getOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    @SuppressLint("InflateParams")
    private fun initCapsule() {
        try {
            val inflater = LayoutInflater.from(themedContext)
            capsuleView = inflater.inflate(R.layout.view_sidebar_capsule, null)

            // Touch target runway width: 48dp (Google Material minimum touch target)
            val handleTouchWidthPx = (48 * density).toInt()
            val handleHeightPx = (80 * density).toInt()

            capsuleParams = WindowManager.LayoutParams(
                handleTouchWidthPx,
                handleHeightPx,
                getOverlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (prefs.isDockedLeft) 0 else (screenWidth - handleTouchWidthPx)
                y = prefs.dockedY.coerceIn(statusBarHeight + 60, screenHeight - navBarHeight - handleHeightPx - 60)
            }

            updateCapsuleBackground()
            setupCapsuleTouchListener()
        } catch (e: Exception) {
            Log.e(TAG, "initCapsule error", e)
        }
    }

    private fun updateCapsuleBackground() {
        val capsule = capsuleView ?: return
        val vVisualHandle = capsule.findViewById<View>(R.id.vVisualHandle) ?: return
        val lp = vVisualHandle.layoutParams as? FrameLayout.LayoutParams ?: return

        if (prefs.isDockedLeft) {
            lp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            vVisualHandle.layoutParams = lp
            vVisualHandle.setBackgroundResource(R.drawable.bg_sidebar_handle_left)
        } else {
            lp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            vVisualHandle.layoutParams = lp
            vVisualHandle.setBackgroundResource(R.drawable.bg_sidebar_handle_right)
        }
    }

    /**
     * Plays a luminous beacon pulse glow when the floating window tucks into the border
     */
    private fun playLuminousBeaconAnimation() {
        val capsule = capsuleView ?: return
        val vVisualHandle = capsule.findViewById<View>(R.id.vVisualHandle) ?: return
        val pulseGlow = capsule.findViewById<View>(R.id.vPulseGlow)
        val indicator = capsule.findViewById<View>(R.id.vAccentIndicator)

        vVisualHandle.alpha = 1.0f
        pulseGlow?.alpha = 1.0f
        indicator?.scaleY = 1.35f

        // Soft haptic tick to announce docking
        HapticHelper.vibrateShort(context)

        // Pulse animation
        pulseGlow?.animate()
            ?.alpha(0f)
            ?.setDuration(550)
            ?.start()

        indicator?.animate()
            ?.scaleY(1.0f)
            ?.setDuration(400)
            ?.start()

        // Settle into dormant semi-transparent state after 750ms
        vVisualHandle.postDelayed({
            if (!isExpanded && capsule.isAttachedToWindow) {
                vVisualHandle.animate()
                    .alpha(0.40f)
                    .setDuration(300)
                    .start()
            }
        }, 750)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCapsuleTouchListener() {
        capsuleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDraggingY = false
            private var hasSwipedInward = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isTransitioning) return true

                val vVisualHandle = v.findViewById<View>(R.id.vVisualHandle)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = capsuleParams.x
                        initialY = capsuleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDraggingY = false
                        hasSwipedInward = false

                        // Light up handle immediately on touch
                        vVisualHandle?.animate()?.alpha(1.0f)?.setDuration(60)?.start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY

                        // 1. INWARD SWIPE GESTURE (向屏幕内划动)
                        // Trigger threshold is low (8dp ~ 24px) for instantaneous response!
                        val swipeThreshold = 8 * density
                        if (!hasSwipedInward) {
                            if (prefs.isDockedLeft && dx > swipeThreshold) {
                                // Docked on left, swiped right into screen -> OPEN!
                                hasSwipedInward = true
                                HapticHelper.performClick(v)
                                expandCard()
                                return true
                            } else if (!prefs.isDockedLeft && dx < -swipeThreshold) {
                                // Docked on right, swiped left into screen -> OPEN!
                                hasSwipedInward = true
                                HapticHelper.performClick(v)
                                expandCard()
                                return true
                            }
                        }

                        // 2. VERTICAL DRAG (上下滑动调整把手位置)
                        if (!hasSwipedInward && abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                            isDraggingY = true
                            capsuleParams.y = (initialY + dy).toInt().coerceIn(
                                statusBarHeight + 50,
                                screenHeight - navBarHeight - capsuleParams.height - 50
                            )
                            updateViewLayoutSafe(capsuleView, capsuleParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (hasSwipedInward) {
                            return true
                        }

                        if (!isDraggingY) {
                            // Tap event -> Expand!
                            HapticHelper.performClick(v)
                            expandCard()
                        } else {
                            // User finished vertical repositioning
                            prefs.dockedY = capsuleParams.y
                            // Fade back to dormant state
                            vVisualHandle?.postDelayed({
                                if (!isExpanded && v.isAttachedToWindow) {
                                    vVisualHandle.animate().alpha(0.40f).setDuration(250).start()
                                }
                            }, 600)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun initCard() {
        try {
            val inflater = LayoutInflater.from(themedContext)
            cardView = inflater.inflate(R.layout.view_floating_card, null)

            val cardWidthPx = min(
                (320 * density).toInt(),
                (screenWidth * 0.92f).toInt()
            )

            var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            // Native Android 12+ (API 31+) Frosted Glass Window Blur!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            }

            cardParams = WindowManager.LayoutParams(
                cardWidthPx,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayWindowType(),
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                x = calculateExpandedCardX(cardWidthPx)
                y = prefs.cardY.coerceIn(statusBarHeight + 50, screenHeight - navBarHeight - 380)

                // 40px GPU-accelerated Gaussian Frosted Glass Blur (Highly optimized for low resource usage)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    blurBehindRadius = 40
                }
            }

            bindCardViews()
        } catch (e: Exception) {
            Log.e(TAG, "initCard error", e)
        }
    }

    private fun calculateExpandedCardX(cardWidth: Int): Int {
        val margin = (20 * density).toInt()
        return if (prefs.isDockedLeft) {
            margin
        } else {
            max(margin, screenWidth - cardWidth - margin)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindCardViews() {
        val card = cardView ?: return

        val etInput = card.findViewById<EditText>(R.id.etInput)
        val tvCharCount = card.findViewById<TextView>(R.id.tvCharCount)
        val btnCopy = card.findViewById<View>(R.id.btnCopy)
        val btnClear = card.findViewById<View>(R.id.btnClear)
        val btnCollapse = card.findViewById<ImageView>(R.id.btnCollapse)
        val btnClose = card.findViewById<ImageView>(R.id.btnClose)
        val layoutHeader = card.findViewById<View>(R.id.layoutHeader)
        val ivCopyIcon = card.findViewById<ImageView>(R.id.ivCopyIcon)
        val tvCopyText = card.findViewById<TextView>(R.id.tvCopyText)

        // 1. Text input settings: standard multiline text, no secure/password keyboard
        etInput.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        etInput.setText(prefs.savedDraftText)
        tvCharCount.text = "${etInput.text.length} 字符"

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length ?: 0
                tvCharCount.text = "$len 字符"
                prefs.savedDraftText = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Request focus and open IME on tap
        etInput.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.requestFocus()
                imm?.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
            }
            false
        }

        // 2. Copy Function
        btnCopy.setOnClickListener {
            val content = etInput.text?.toString() ?: ""
            if (content.isEmpty()) {
                HapticHelper.vibrateClear(context)
                Toast.makeText(context, R.string.clipboard_empty_warning, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Put in clipboard
            try {
                val clip = ClipData.newPlainText("FloatInput", content)
                clipboardManager.setPrimaryClip(clip)

                HapticHelper.performConfirm(btnCopy)
                HapticHelper.vibrateShort(context)

                // Micro-animation on copy button
                ivCopyIcon.setImageResource(R.drawable.ic_check)
                tvCopyText.text = "已复制"
                btnCopy.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(100)
                    .withEndAction {
                        btnCopy.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .setInterpolator(OvershootInterpolator())
                            .start()
                    }.start()

                btnCopy.postDelayed({
                    ivCopyIcon.setImageResource(R.drawable.ic_copy)
                    tvCopyText.text = context.getString(R.string.action_copy)
                }, 1200)

                // Android 13+ has native clipboard toast, show manual Toast for 12 and below
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "copy failed", e)
            }
        }

        // 3. Clear Function
        btnClear.setOnClickListener {
            if (etInput.text.isNotEmpty()) {
                etInput.text?.clear()
                prefs.savedDraftText = ""
                HapticHelper.performClick(btnClear)
                HapticHelper.vibrateClear(context)
                Toast.makeText(context, R.string.cleared_content, Toast.LENGTH_SHORT).show()
            }
        }

        // Collapse to Sidebar (Method 1: Click button)
        btnCollapse.setOnClickListener {
            HapticHelper.performClick(btnCollapse)
            collapseToSidebar()
        }

        // Close Floating Window
        btnClose.setOnClickListener {
            HapticHelper.performClick(btnClose)
            onCloseRequested?.invoke()
        }

        // Dragging Card & Edge-Snapping (Method 2: Drag to edge to collapse)
        setupCardDragListener(layoutHeader)

        // Tap outside card -> Collapse to sidebar! (Method 3: Click outside to collapse)
        cardView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideSoftKeyboard()
                collapseToSidebar()
                return@setOnTouchListener true
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCardDragListener(dragHandle: View) {
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private var velocityTracker: VelocityTracker? = null

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isTransitioning) return true

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(event)

                        initialX = cardParams.x
                        initialY = cardParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)

                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            isDragging = true
                            hideSoftKeyboard()
                        }
                        if (isDragging) {
                            cardParams.x = (initialX + dx).toInt().coerceIn(
                                10,
                                screenWidth - cardParams.width - 10
                            )
                            cardParams.y = (initialY + dy).toInt().coerceIn(
                                statusBarHeight,
                                screenHeight - navBarHeight - 200
                            )
                            updateViewLayoutSafe(cardView, cardParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        velocityTracker?.addMovement(event)
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        velocityTracker?.recycle()
                        velocityTracker = null

                        if (isDragging) {
                            prefs.cardX = cardParams.x
                            prefs.cardY = cardParams.y

                            val edgeMargin = 28 * density
                            // Pushed near left edge or flung left -> Collapse to left edge!
                            if (cardParams.x < edgeMargin || vx < -1200f) {
                                prefs.isDockedLeft = true
                                collapseToSidebar()
                                return true
                            }
                            // Pushed near right edge or flung right -> Collapse to right edge!
                            if (cardParams.x + cardParams.width > screenWidth - edgeMargin || vx > 1200f) {
                                prefs.isDockedLeft = false
                                collapseToSidebar()
                                return true
                            }

                            // FIXED DOCKING SIDE:
                            // Do NOT change sides on minor drags!
                            // Only switch sides if user intentionally dragged past the opposite third of screen!
                            if (prefs.isDockedLeft) {
                                if (cardParams.x > screenWidth / 3) {
                                    prefs.isDockedLeft = false
                                }
                            } else {
                                if ((cardParams.x + cardParams.width) < screenWidth * 2 / 3) {
                                    prefs.isDockedLeft = true
                                }
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        velocityTracker?.recycle()
                        velocityTracker = null
                    }
                }
                return false
            }
        })
    }

    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }
        if (isAttached) return
        isAttached = true
        isExpanded = prefs.isExpanded

        if (isExpanded) {
            attachCard()
        } else {
            attachCapsule()
            playLuminousBeaconAnimation()
        }
    }

    fun hide() {
        if (!isAttached) return
        hideSoftKeyboard()
        detachViewSafe(cardView)
        detachViewSafe(capsuleView)
        isAttached = false
    }

    fun toggleExpandCollapse() {
        if (!isAttached) return
        if (isExpanded) {
            collapseToSidebar()
        } else {
            expandCard()
        }
    }

    /**
     * Seamlessly expands the card from the docked sidebar handle with zero flicker
     */
    private fun expandCard() {
        if (isExpanded || isTransitioning) return
        isTransitioning = true
        isExpanded = true
        prefs.isExpanded = true

        val card = cardView ?: run {
            isTransitioning = false
            return
        }

        // Position card comfortably inside screen
        val cardWidth = cardParams.width
        cardParams.x = calculateExpandedCardX(cardWidth)
        cardParams.y = capsuleParams.y.coerceIn(statusBarHeight + 50, screenHeight - navBarHeight - 420)

        // Make visible and set initial slide offset BEFORE attaching
        card.visibility = View.VISIBLE
        val slideOffset = if (prefs.isDockedLeft) -120f else 120f
        card.translationX = slideOffset
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        attachCard()

        // Fade out handle
        val vVisualHandle = capsuleView?.findViewById<View>(R.id.vVisualHandle)
        vVisualHandle?.animate()
            ?.alpha(0f)
            ?.setDuration(140)
            ?.start()

        // Smooth elastic slide-in
        card.animate()
            .translationX(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(1.15f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    card.setLayerType(View.LAYER_TYPE_NONE, null)
                    detachViewSafe(capsuleView)
                    isTransitioning = false
                }
            })
            .start()
    }

    /**
     * Seamlessly collapses the card towards the docked edge with zero flicker
     */
    private fun collapseToSidebar() {
        if (!isExpanded || isTransitioning) return
        isTransitioning = true
        hideSoftKeyboard()

        val card = cardView ?: run {
            isTransitioning = false
            return
        }

        // FIXED DOCKING SIDE:
        // Always stick to current docked side unless user intentionally dragged it to the other half!
        val dockLeft = prefs.isDockedLeft
        val handleTouchWidth = capsuleParams.width
        capsuleParams.x = if (dockLeft) 0 else (screenWidth - handleTouchWidth)
        capsuleParams.y = cardParams.y.coerceIn(
            statusBarHeight + 50,
            screenHeight - navBarHeight - capsuleParams.height - 50
        )
        updateCapsuleBackground()

        // 1. Prepare capsule view: attached with alpha = 0 so its window surface is warm
        val vVisualHandle = capsuleView?.findViewById<View>(R.id.vVisualHandle)
        vVisualHandle?.alpha = 0f
        attachCapsule()

        // 2. Hardware acceleration layer during animation
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val slideTarget = if (dockLeft) -150f else 150f
        card.animate()
            .translationX(slideTarget)
            .alpha(0f)
            .scaleX(0.70f)
            .scaleY(0.70f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // CRUCIAL FIX: Set GONE immediately so SurfaceFlinger NEVER draws it again
                    // Do NOT reset translationX to 0 here! That was what caused the 1-frame pop-back glitch!
                    card.visibility = View.GONE
                    card.setLayerType(View.LAYER_TYPE_NONE, null)
                    detachViewSafe(cardView)

                    isExpanded = false
                    prefs.isExpanded = false
                    isTransitioning = false

                    // Play luminous beacon on the handle
                    playLuminousBeaconAnimation()
                }
            })
            .start()
    }

    private fun attachCapsule() {
        if (!Settings.canDrawOverlays(context)) return
        val capsule = capsuleView ?: return
        updateCapsuleBackground()
        if (!capsule.isAttachedToWindow) {
            try {
                windowManager.addView(capsule, capsuleParams)
            } catch (e: Exception) {
                Log.e(TAG, "attachCapsule error", e)
            }
        }
    }

    private fun attachCard() {
        if (!Settings.canDrawOverlays(context)) return
        val card = cardView ?: return
        if (!card.isAttachedToWindow) {
            try {
                windowManager.addView(card, cardParams)
            } catch (e: Exception) {
                Log.e(TAG, "attachCard error", e)
            }
        }
    }

    private fun detachViewSafe(view: View?) {
        if (view != null && view.isAttachedToWindow) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "detachViewSafe error", e)
            }
        }
    }

    private fun updateViewLayoutSafe(view: View?, params: ViewGroup.LayoutParams) {
        if (view != null && view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "updateViewLayoutSafe error", e)
            }
        }
    }

    private fun hideSoftKeyboard() {
        try {
            val input = cardView?.findViewById<EditText>(R.id.etInput)
            if (input != null && input.windowToken != null) {
                imm?.hideSoftInputFromWindow(input.windowToken, 0)
            }
        } catch (_: Exception) {}
    }

    fun onConfigurationChanged() {
        updateScreenDimensions()
        if (isExpanded) {
            cardParams.x = cardParams.x.coerceIn(10, max(10, screenWidth - cardParams.width - 10))
            cardParams.y = cardParams.y.coerceIn(statusBarHeight, max(statusBarHeight, screenHeight - navBarHeight - 200))
            updateViewLayoutSafe(cardView, cardParams)
        } else {
            val handleTouchWidth = capsuleParams.width
            capsuleParams.x = if (prefs.isDockedLeft) 0 else (screenWidth - handleTouchWidth)
            capsuleParams.y = capsuleParams.y.coerceIn(statusBarHeight, max(statusBarHeight, screenHeight - navBarHeight - capsuleParams.height))
            updateCapsuleBackground()
            updateViewLayoutSafe(capsuleView, capsuleParams)
        }
    }
}
