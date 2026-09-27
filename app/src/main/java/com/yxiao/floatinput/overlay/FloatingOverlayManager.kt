package com.yxiao.floatinput.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.yxiao.floatinput.R
import com.yxiao.floatinput.util.HapticHelper
import com.yxiao.floatinput.util.PreferencesHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    private val prefs = PreferencesHelper.getInstance(context)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Views
    private var capsuleView: View? = null
    private var cardView: View? = null

    // Layout Params
    private lateinit var capsuleParams: WindowManager.LayoutParams
    private lateinit var cardParams: WindowManager.LayoutParams

    private var isExpanded = false
    private var isAttached = false

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
            statusBarHeight = (24 * context.resources.displayMetrics.density).toInt()
            navBarHeight = (48 * context.resources.displayMetrics.density).toInt()
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
        val inflater = LayoutInflater.from(context)
        capsuleView = inflater.inflate(R.layout.view_sidebar_capsule, null)

        val capsuleWidthPx = (32 * context.resources.displayMetrics.density).toInt()
        val capsuleHeightPx = (64 * context.resources.displayMetrics.density).toInt()

        capsuleParams = WindowManager.LayoutParams(
            capsuleWidthPx,
            capsuleHeightPx,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (prefs.isDockedLeft) 0 else (screenWidth - capsuleWidthPx)
            y = prefs.dockedY.coerceIn(statusBarHeight + 50, screenHeight - navBarHeight - capsuleHeightPx - 50)
        }

        setupCapsuleTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCapsuleTouchListener() {
        capsuleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = capsuleParams.x
                        initialY = capsuleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        v.alpha = 1.0f
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            isDragging = true
                            HapticHelper.performClick(v)
                        }
                        if (isDragging) {
                            capsuleParams.x = (initialX + dx).toInt().coerceIn(0, screenWidth - capsuleParams.width)
                            capsuleParams.y = (initialY + dy).toInt().coerceIn(
                                statusBarHeight,
                                screenHeight - navBarHeight - capsuleParams.height
                            )
                            updateViewLayoutSafe(capsuleView, capsuleParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.alpha = 0.85f
                        if (!isDragging) {
                            // Tap event -> expand to floating card
                            HapticHelper.performClick(v)
                            expandCard()
                        } else {
                            // Snap to edge
                            snapCapsuleToEdge()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun snapCapsuleToEdge() {
        val currentX = capsuleParams.x
        val snapLeft = (currentX + capsuleParams.width / 2) < (screenWidth / 2)
        val targetX = if (snapLeft) 0 else (screenWidth - capsuleParams.width)

        prefs.isDockedLeft = snapLeft
        prefs.dockedY = capsuleParams.y

        val animator = ValueAnimator.ofInt(currentX, targetX)
        animator.duration = 220
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { va ->
            capsuleParams.x = va.animatedValue as Int
            updateViewLayoutSafe(capsuleView, capsuleParams)
        }
        animator.start()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun initCard() {
        val inflater = LayoutInflater.from(context)
        cardView = inflater.inflate(R.layout.view_floating_card, null)

        val cardWidthPx = min(
            (310 * context.resources.displayMetrics.density).toInt(),
            (screenWidth * 0.90f).toInt()
        )

        cardParams = WindowManager.LayoutParams(
            cardWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            x = prefs.cardX.coerceIn(20, max(20, screenWidth - cardWidthPx - 20))
            y = prefs.cardY.coerceIn(statusBarHeight + 50, screenHeight - navBarHeight - 350)
        }

        bindCardViews()
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
        tvCharCount.text = context.getString(R.string.char_count_format, etInput.text.length)

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length ?: 0
                tvCharCount.text = context.getString(R.string.char_count_format, len)
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
            val clip = ClipData.newPlainText("FloatInput", content)
            clipboardManager.setPrimaryClip(clip)

            HapticHelper.performConfirm(btnCopy)
            HapticHelper.vibrateShort(context)

            // Micro-animation on copy button
            ivCopyIcon.setImageResource(R.drawable.ic_launcher_foreground)
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

        // Collapse to Sidebar
        btnCollapse.setOnClickListener {
            HapticHelper.performClick(btnCollapse)
            collapseToSidebar()
        }

        // Close Floating Window
        btnClose.setOnClickListener {
            HapticHelper.performClick(btnClose)
            onCloseRequested?.invoke()
        }

        // Dragging Card
        setupCardDragListener(layoutHeader)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCardDragListener(dragHandle: View) {
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = cardParams.x
                        initialY = cardParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
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
                        if (isDragging) {
                            prefs.cardX = cardParams.x
                            prefs.cardY = cardParams.y

                            // If dragged very close to screen edge, dock to sidebar automatically
                            if (cardParams.x < 30 || cardParams.x > (screenWidth - cardParams.width - 30)) {
                                prefs.isDockedLeft = cardParams.x < screenWidth / 2
                                prefs.dockedY = cardParams.y
                                collapseToSidebar()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Tap outside card -> hide soft keyboard
        cardView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideSoftKeyboard()
                return@setOnTouchListener true
            }
            false
        }
    }

    fun show() {
        if (isAttached) return
        isAttached = true
        isExpanded = prefs.isExpanded

        if (isExpanded) {
            attachCard()
        } else {
            attachCapsule()
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

    private fun expandCard() {
        if (isExpanded) return
        isExpanded = true
        prefs.isExpanded = true

        // Position card near capsule docked position
        val targetX = if (prefs.isDockedLeft) {
            20
        } else {
            max(20, screenWidth - cardParams.width - 20)
        }
        cardParams.x = targetX
        cardParams.y = capsuleParams.y.coerceIn(statusBarHeight + 50, screenHeight - navBarHeight - 400)

        // Detach capsule, attach card with smooth fade in
        detachViewSafe(capsuleView)
        attachCard()

        cardView?.alpha = 0f
        cardView?.scaleX = 0.85f
        cardView?.scaleY = 0.85f
        cardView?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(220)
            ?.setInterpolator(OvershootInterpolator(1.2f))
            ?.start()
    }

    private fun collapseToSidebar() {
        if (!isExpanded) return
        hideSoftKeyboard()

        val card = cardView ?: return
        val dockLeft = (cardParams.x + cardParams.width / 2) < (screenWidth / 2)
        prefs.isDockedLeft = dockLeft
        prefs.dockedY = cardParams.y

        val capsuleWidth = capsuleParams.width
        capsuleParams.x = if (dockLeft) 0 else (screenWidth - capsuleWidth)
        capsuleParams.y = cardParams.y.coerceIn(
            statusBarHeight + 50,
            screenHeight - navBarHeight - capsuleParams.height - 50
        )

        // Fade out card then attach capsule
        card.animate()
            .alpha(0f)
            .scaleX(0.75f)
            .scaleY(0.75f)
            .setDuration(160)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    detachViewSafe(cardView)
                    isExpanded = false
                    prefs.isExpanded = false
                    attachCapsule()

                    capsuleView?.alpha = 0f
                    capsuleView?.animate()
                        ?.alpha(0.85f)
                        ?.setDuration(160)
                        ?.start()
                }
            })
            .start()
    }

    private fun attachCapsule() {
        if (capsuleView?.windowToken == null) {
            try {
                windowManager.addView(capsuleView, capsuleParams)
            } catch (_: Exception) {}
        }
    }

    private fun attachCard() {
        if (cardView?.windowToken == null) {
            try {
                windowManager.addView(cardView, cardParams)
            } catch (_: Exception) {}
        }
    }

    private fun detachViewSafe(view: View?) {
        if (view != null && view.windowToken != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
        }
    }

    private fun updateViewLayoutSafe(view: View?, params: ViewGroup.LayoutParams) {
        if (view != null && view.windowToken != null) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    private fun hideSoftKeyboard() {
        val input = cardView?.findViewById<EditText>(R.id.etInput)
        if (input != null && input.windowToken != null) {
            imm?.hideSoftInputFromWindow(input.windowToken, 0)
        }
    }

    fun onConfigurationChanged() {
        updateScreenDimensions()
        if (isExpanded) {
            cardParams.x = cardParams.x.coerceIn(10, max(10, screenWidth - cardParams.width - 10))
            cardParams.y = cardParams.y.coerceIn(statusBarHeight, max(statusBarHeight, screenHeight - navBarHeight - 200))
            updateViewLayoutSafe(cardView, cardParams)
        } else {
            capsuleParams.x = if (prefs.isDockedLeft) 0 else (screenWidth - capsuleParams.width)
            capsuleParams.y = capsuleParams.y.coerceIn(statusBarHeight, max(statusBarHeight, screenHeight - navBarHeight - capsuleParams.height))
            updateViewLayoutSafe(capsuleView, capsuleParams)
        }
    }
}
