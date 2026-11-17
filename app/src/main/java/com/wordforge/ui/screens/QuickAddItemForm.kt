package com.wordforge.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.wordforge.data.LearningItemDraft
import com.wordforge.data.LearningItemType
import com.wordforge.data.VerbConjugation
import com.wordforge.ui.components.VerbTenseDropdown
import com.wordforge.ui.components.WordForgeSnackbarHost
import kotlinx.coroutines.launch

private enum class VerbAddStage {
    BASICS,
    CONJUGATION,
}

private enum class QuickAddFocus {
    TERM,
    MEANING,
    TENSE,
    PASTE,
    YO,
    TU,
    EL_ELLA_USTED,
    NOSOTROS,
    VOSOTROS,
    ELLOS_ELLAS_USTEDES,
}

/**
 * Add-only, keyboard-first intake flow. Editing deliberately keeps using the
 * complete type-specific form so every review field remains visible together.
 */
@Composable
fun QuickAddItemForm(
    onSubmit: (LearningItemDraft) -> Unit,
    onNavigateBack: () -> Unit,
    itemWarning: (LearningItemDraft) -> String? = { null },
) {
    var selectedTypeName by rememberSaveable {
        mutableStateOf(LearningItemType.SIMPLE_WORD.name)
    }
    val selectedType = LearningItemType.valueOf(selectedTypeName)
    var verbStageName by rememberSaveable { mutableStateOf(VerbAddStage.BASICS.name) }
    val verbStage = VerbAddStage.valueOf(verbStageName)

    var term by rememberSaveable { mutableStateOf("") }
    var meaning by rememberSaveable { mutableStateOf("") }
    var randomlyFlip by rememberSaveable { mutableStateOf(true) }
    var tense by rememberSaveable { mutableStateOf("") }
    var baselineTense by rememberSaveable { mutableStateOf("") }
    var tenseMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var yo by rememberSaveable { mutableStateOf("") }
    var tu by rememberSaveable { mutableStateOf("") }
    var elEllaUsted by rememberSaveable { mutableStateOf("") }
    var nosotros by rememberSaveable { mutableStateOf("") }
    var vosotros by rememberSaveable { mutableStateOf("") }
    var ellosEllasUstedes by rememberSaveable { mutableStateOf("") }
    var pasteInput by rememberSaveable { mutableStateOf("") }
    var showPasteInput by rememberSaveable { mutableStateOf(false) }
    var pasteApplied by rememberSaveable { mutableStateOf(false) }
    var pasteValidationAttempted by rememberSaveable { mutableStateOf(false) }
    var baseValidationAttempted by rememberSaveable { mutableStateOf(false) }
    var conjugationValidationAttempted by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var addedCount by rememberSaveable { mutableIntStateOf(0) }
    var submitLocked by remember { mutableStateOf(false) }

    val submittedThisSession = remember { mutableStateListOf<LearningItemDraft>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val termFocusRequester = remember { FocusRequester() }
    val meaningFocusRequester = remember { FocusRequester() }
    val tenseFocusRequester = remember { FocusRequester() }
    val pasteFocusRequester = remember { FocusRequester() }
    val yoFocusRequester = remember { FocusRequester() }
    val tuFocusRequester = remember { FocusRequester() }
    val elEllaUstedFocusRequester = remember { FocusRequester() }
    val nosotrosFocusRequester = remember { FocusRequester() }
    val vosotrosFocusRequester = remember { FocusRequester() }
    val ellosEllasUstedesFocusRequester = remember { FocusRequester() }
    var focusTargetName by rememberSaveable { mutableStateOf(QuickAddFocus.TERM.name) }
    var focusRequestId by rememberSaveable { mutableIntStateOf(0) }

    fun requestFocus(target: QuickAddFocus) {
        focusTargetName = target.name
        focusRequestId += 1
    }

    fun focusRequesterFor(target: QuickAddFocus): FocusRequester = when (target) {
        QuickAddFocus.TERM -> termFocusRequester
        QuickAddFocus.MEANING -> meaningFocusRequester
        QuickAddFocus.TENSE -> tenseFocusRequester
        QuickAddFocus.PASTE -> pasteFocusRequester
        QuickAddFocus.YO -> yoFocusRequester
        QuickAddFocus.TU -> tuFocusRequester
        QuickAddFocus.EL_ELLA_USTED -> elEllaUstedFocusRequester
        QuickAddFocus.NOSOTROS -> nosotrosFocusRequester
        QuickAddFocus.VOSOTROS -> vosotrosFocusRequester
        QuickAddFocus.ELLOS_ELLAS_USTEDES -> ellosEllasUstedesFocusRequester
    }

    fun isFocusTargetVisible(target: QuickAddFocus): Boolean = when {
        selectedType == LearningItemType.SIMPLE_WORD -> {
            target == QuickAddFocus.TERM || target == QuickAddFocus.MEANING
        }
        verbStage == VerbAddStage.BASICS -> {
            target == QuickAddFocus.TERM || target == QuickAddFocus.TENSE
        }
        target == QuickAddFocus.PASTE -> showPasteInput
        else -> target !in setOf(
            QuickAddFocus.TERM,
            QuickAddFocus.MEANING,
            QuickAddFocus.TENSE,
            QuickAddFocus.PASTE,
        )
    }

    fun restorableFocusTarget(): QuickAddFocus {
        val remembered = QuickAddFocus.valueOf(focusTargetName)
        if (isFocusTargetVisible(remembered)) return remembered
        return when {
            selectedType == LearningItemType.SIMPLE_WORD -> QuickAddFocus.TERM
            verbStage == VerbAddStage.BASICS -> QuickAddFocus.TERM
            showPasteInput -> QuickAddFocus.PASTE
            else -> QuickAddFocus.YO
        }
    }

    LaunchedEffect(
        focusRequestId,
        selectedTypeName,
        verbStageName,
        showPasteInput,
        showDiscardDialog,
    ) {
        if (!showDiscardDialog) {
            val target = restorableFocusTarget()
            focusRequesterFor(target).requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(selectedTypeName, verbStageName) {
        scrollState.scrollTo(0)
    }

    // The IME can dispatch Done twice before the cleared fields recompose.
    // Releasing this one-frame guard after the count changes prevents two inserts.
    LaunchedEffect(addedCount) {
        submitLocked = false
    }

    fun currentVerbValue() = VerbConjugation(
        tense = tense,
        yo = yo,
        tu = tu,
        elEllaUsted = elEllaUsted,
        nosotros = nosotros,
        vosotros = vosotros,
        ellosEllasUstedes = ellosEllasUstedes,
    )

    fun currentDraftValue(): LearningItemDraft {
        val type = LearningItemType.valueOf(selectedTypeName)
        return LearningItemDraft(
            type = type,
            term = term,
            meaning = if (type == LearningItemType.SIMPLE_WORD) meaning else "",
            randomlyFlip = randomlyFlip,
            verbConjugation = if (type == LearningItemType.VERB_CONJUGATION) {
                currentVerbValue()
            } else {
                null
            },
        )
    }

    val currentDraft = currentDraftValue()
    val duplicateWarning = if (term.isBlank()) {
        null
    } else {
        itemWarning(currentDraft) ?: sessionDuplicateWarning(currentDraft, submittedThisSession)
    }
    val hasVerbSpecificContent = yo.isNotBlank() ||
        tu.isNotBlank() ||
        elEllaUsted.isNotBlank() ||
        nosotros.isNotBlank() ||
        vosotros.isNotBlank() ||
        ellosEllasUstedes.isNotBlank() ||
        pasteInput.isNotBlank()
    val hasUnsavedChanges = term.isNotBlank() ||
        (selectedType == LearningItemType.SIMPLE_WORD && meaning.isNotBlank()) ||
        (selectedType == LearningItemType.VERB_CONJUGATION && hasVerbSpecificContent)

    fun firstMissingBaseFocus(): QuickAddFocus? = when {
        term.isBlank() -> QuickAddFocus.TERM
        tense.isBlank() -> QuickAddFocus.TENSE
        else -> null
    }

    fun openTenseMenu() {
        focusTargetName = QuickAddFocus.TENSE.name
        focusManager.clearFocus()
        keyboardController?.hide()
        tenseMenuExpanded = true
    }

    fun openConjugation(selectedTense: String? = null) {
        if (selectedTense != null) tense = selectedTense
        tenseMenuExpanded = false
        baseValidationAttempted = true
        val missing = firstMissingBaseFocus()
        if (missing != null) {
            if (missing == QuickAddFocus.TENSE) openTenseMenu() else requestFocus(missing)
            return
        }
        verbStageName = VerbAddStage.CONJUGATION.name
        requestFocus(firstMissingConjugationFocus(currentVerbValue()) ?: QuickAddFocus.YO)
    }

    fun fillConjugation(parsed: List<String>) {
        yo = parsed[0]
        tu = parsed[1]
        elEllaUsted = parsed[2]
        nosotros = parsed[3]
        vosotros = parsed[4]
        ellosEllasUstedes = parsed[5]
        pasteInput = ""
        showPasteInput = false
        pasteValidationAttempted = false
        pasteApplied = true
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun applyPastedConjugation() {
        pasteValidationAttempted = true
        val parsed = parseSixConjugationForms(pasteInput)
        if (parsed == null) {
            requestFocus(QuickAddFocus.PASTE)
            return
        }
        fillConjugation(parsed)
    }

    fun openPasteInput() {
        pasteApplied = false
        pasteValidationAttempted = false
        if (pasteInput.isNotBlank()) {
            showPasteInput = true
            requestFocus(QuickAddFocus.PASTE)
            return
        }
        val clipboardText = runCatching {
            clipboardManager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
        }.getOrDefault("")
        val parsed = parseSixConjugationForms(clipboardText)
        if (parsed != null) {
            fillConjugation(parsed)
        } else {
            if (clipboardText.isNotBlank()) pasteInput = clipboardText
            showPasteInput = true
            requestFocus(QuickAddFocus.PASTE)
        }
    }

    fun closePasteInput(clear: Boolean) {
        if (clear) pasteInput = ""
        showPasteInput = false
        pasteValidationAttempted = false
        requestFocus(firstMissingConjugationFocus(currentVerbValue()) ?: QuickAddFocus.YO)
    }

    fun resetAfterSubmit(draft: LearningItemDraft) {
        term = ""
        meaning = ""
        yo = ""
        tu = ""
        elEllaUsted = ""
        nosotros = ""
        vosotros = ""
        ellosEllasUstedes = ""
        pasteInput = ""
        showPasteInput = false
        tenseMenuExpanded = false
        pasteApplied = false
        pasteValidationAttempted = false
        baseValidationAttempted = false
        conjugationValidationAttempted = false
        verbStageName = VerbAddStage.BASICS.name
        if (draft.type == LearningItemType.VERB_CONJUGATION) {
            baselineTense = tense
        }
        requestFocus(QuickAddFocus.TERM)
    }

    fun commit(draft: LearningItemDraft) {
        if (submitLocked) return
        submitLocked = true
        val normalized = draft.normalized()
        submittedThisSession.add(normalized)
        onSubmit(normalized)
        addedCount += 1
        val message = if (addedCount == 1) {
            "Item added — ready for another"
        } else {
            "$addedCount items added"
        }
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
        resetAfterSubmit(normalized)
    }

    fun submitSimpleWord() {
        baseValidationAttempted = true
        when {
            term.isBlank() -> requestFocus(QuickAddFocus.TERM)
            meaning.isBlank() -> requestFocus(QuickAddFocus.MEANING)
            else -> commit(currentDraftValue())
        }
    }

    fun submitVerb() {
        baseValidationAttempted = true
        val missingBase = firstMissingBaseFocus()
        if (missingBase != null) {
            verbStageName = VerbAddStage.BASICS.name
            requestFocus(missingBase)
            return
        }
        conjugationValidationAttempted = true
        val missingConjugation = firstMissingConjugationFocus(currentVerbValue())
        if (missingConjugation != null) {
            verbStageName = VerbAddStage.CONJUGATION.name
            requestFocus(missingConjugation)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("Fill in all six forms")
            }
            return
        }
        commit(currentDraftValue())
    }

    fun requestNavigateBack() {
        if (showPasteInput) {
            closePasteInput(clear = false)
        } else if (
            selectedType == LearningItemType.VERB_CONJUGATION &&
            verbStage == VerbAddStage.CONJUGATION
        ) {
            verbStageName = VerbAddStage.BASICS.name
            openTenseMenu()
        } else if (hasUnsavedChanges) {
            focusManager.clearFocus()
            keyboardController?.hide()
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler { requestNavigateBack() }

    LaunchedEffect(pasteApplied) {
        if (pasteApplied) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::requestNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = when {
                            showPasteInput -> "Close paste editor"
                            selectedType == LearningItemType.VERB_CONJUGATION &&
                                verbStage == VerbAddStage.CONJUGATION -> {
                                "Back to verb details"
                            }
                            else -> "Go back"
                        },
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "QUICK ADD",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (addedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "$addedCount ADDED",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        },
        snackbarHost = { WordForgeSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    selectedType == LearningItemType.SIMPLE_WORD -> "Add a word"
                    verbStage == VerbAddStage.BASICS -> "Set up the verb"
                    else -> "Add the six forms"
                },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    selectedType == LearningItemType.SIMPLE_WORD -> {
                        "Use Next and Done on the keyboard. The next item starts automatically."
                    }
                    verbStage == VerbAddStage.BASICS -> {
                        "Step 1 of 2 · Choose a supported tense from the list."
                    }
                    else -> {
                        "Step 2 of 2 · Use Next to move through every person."
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selectedType == LearningItemType.VERB_CONJUGATION) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (verbStage == VerbAddStage.BASICS) 0.5f else 1f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            if (
                selectedType != LearningItemType.VERB_CONJUGATION ||
                verbStage == VerbAddStage.BASICS
            ) {
                QuickItemTypeSelector(
                    selected = selectedType,
                    onSelect = { type ->
                        selectedTypeName = type.name
                        tenseMenuExpanded = false
                        verbStageName = VerbAddStage.BASICS.name
                        baseValidationAttempted = false
                        conjugationValidationAttempted = false
                        requestFocus(QuickAddFocus.TERM)
                    },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (
                selectedType == LearningItemType.SIMPLE_WORD ||
                verbStage == VerbAddStage.BASICS
            ) {
                QuickTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = if (selectedType == LearningItemType.VERB_CONJUGATION) {
                        "Infinitive"
                    } else {
                        "Word or phrase"
                    },
                    placeholder = if (selectedType == LearningItemType.VERB_CONJUGATION) {
                        "e.g. decir"
                    } else {
                        "e.g. petrichor"
                    },
                    isError = baseValidationAttempted && term.isBlank(),
                    supportingText = when {
                        baseValidationAttempted && term.isBlank() -> {
                            if (selectedType == LearningItemType.VERB_CONJUGATION) {
                                "Add the infinitive"
                            } else {
                                "Add a word or phrase"
                            }
                        }
                        duplicateWarning != null &&
                            selectedType == LearningItemType.SIMPLE_WORD -> duplicateWarning
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            if (selectedType == LearningItemType.SIMPLE_WORD) {
                                requestFocus(QuickAddFocus.MEANING)
                            } else if (tense.isBlank()) {
                                openTenseMenu()
                            } else {
                                openConjugation()
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_term")
                        .focusRequester(termFocusRequester)
                        .onFocusChanged {
                            if (it.isFocused) focusTargetName = QuickAddFocus.TERM.name
                        },
                )

                if (selectedType == LearningItemType.SIMPLE_WORD) {
                    Spacer(modifier = Modifier.height(16.dp))
                    QuickTextField(
                        value = meaning,
                        onValueChange = { meaning = it },
                        label = "Meaning",
                        placeholder = "e.g. a pleasant smell after rain",
                        isError = baseValidationAttempted && meaning.isBlank(),
                        supportingText = if (baseValidationAttempted && meaning.isBlank()) {
                            "Add a meaning"
                        } else {
                            null
                        },
                        minLines = 3,
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitSimpleWord() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_add_meaning")
                            .focusRequester(meaningFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusTargetName = QuickAddFocus.MEANING.name
                                }
                            },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    QuickRandomlyFlipToggle(
                        checked = randomlyFlip,
                        onCheckedChange = { randomlyFlip = it },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { submitSimpleWord() },
                        enabled = !submitLocked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Text("Add word", style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    VerbTenseDropdown(
                        value = tense,
                        expanded = tenseMenuExpanded,
                        onExpandedChange = { tenseMenuExpanded = it },
                        onTenseSelected = { openConjugation(it) },
                        label = "Tense or mood",
                        isError = baseValidationAttempted && tense.isBlank(),
                        supportingText = when {
                            baseValidationAttempted && tense.isBlank() -> "Add the tense or mood"
                            duplicateWarning != null -> duplicateWarning
                            tense.isNotBlank() && tense == baselineTense -> {
                                "Kept from the previous verb"
                            }
                            else -> null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(tenseFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) focusTargetName = QuickAddFocus.TENSE.name
                            },
                        testTag = "quick_add_tense",
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { openConjugation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Text("Continue", style = MaterialTheme.typography.titleLarge)
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = term.trim(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tense.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (duplicateWarning != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = duplicateWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (!showPasteInput) {
                    TextButton(
                        onClick = { openPasteInput() },
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (pasteInput.isBlank()) {
                                "Paste all 6 forms"
                            } else {
                                "Continue pasted forms"
                            }
                        )
                    }
                } else {
                    QuickTextField(
                        value = pasteInput,
                        onValueChange = { pasteInput = it },
                        label = "Six forms in person order",
                        placeholder = "digo, dices, dice, decimos, decís, dicen",
                        isError = pasteValidationAttempted &&
                            pasteInput.isNotBlank() &&
                            parseSixConjugationForms(pasteInput) == null,
                        supportingText = if (
                            pasteValidationAttempted &&
                            pasteInput.isNotBlank() &&
                            parseSixConjugationForms(pasteInput) == null
                        ) {
                            "Found ${conjugationParts(pasteInput).size} of 6 forms"
                        } else {
                            "Separate forms with commas, semicolons, tabs, or new lines."
                        },
                        minLines = 2,
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { applyPastedConjugation() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_add_paste")
                            .focusRequester(pasteFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused) focusTargetName = QuickAddFocus.PASTE.name
                            },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { closePasteInput(clear = true) }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { applyPastedConjugation() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("Fill the six forms")
                        }
                    }
                }

                if (pasteApplied) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Six forms filled — check them, then add the verb.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                QuickConjugationRow(
                    person = "YO",
                    value = yo,
                    onValueChange = {
                        yo = it
                        pasteApplied = false
                    },
                    placeholder = "digo",
                    showError = conjugationValidationAttempted && yo.isBlank(),
                    focusRequester = yoFocusRequester,
                    onFocused = { focusTargetName = QuickAddFocus.YO.name },
                    onNext = { requestFocus(QuickAddFocus.TU) },
                )
                QuickConjugationRow(
                    person = "TÚ",
                    value = tu,
                    onValueChange = {
                        tu = it
                        pasteApplied = false
                    },
                    placeholder = "dices",
                    showError = conjugationValidationAttempted && tu.isBlank(),
                    focusRequester = tuFocusRequester,
                    onFocused = { focusTargetName = QuickAddFocus.TU.name },
                    onNext = { requestFocus(QuickAddFocus.EL_ELLA_USTED) },
                )
                QuickConjugationRow(
                    person = "ÉL / ELLA / USTED",
                    value = elEllaUsted,
                    onValueChange = {
                        elEllaUsted = it
                        pasteApplied = false
                    },
                    placeholder = "dice",
                    showError = conjugationValidationAttempted && elEllaUsted.isBlank(),
                    focusRequester = elEllaUstedFocusRequester,
                    onFocused = { focusTargetName = QuickAddFocus.EL_ELLA_USTED.name },
                    onNext = { requestFocus(QuickAddFocus.NOSOTROS) },
                )
                QuickConjugationRow(
                    person = "NOSOTROS / AS",
                    value = nosotros,
                    onValueChange = {
                        nosotros = it
                        pasteApplied = false
                    },
                    placeholder = "decimos",
                    showError = conjugationValidationAttempted && nosotros.isBlank(),
                    focusRequester = nosotrosFocusRequester,
                    onFocused = { focusTargetName = QuickAddFocus.NOSOTROS.name },
                    onNext = { requestFocus(QuickAddFocus.VOSOTROS) },
                )
                QuickConjugationRow(
                    person = "VOSOTROS / AS",
                    value = vosotros,
                    onValueChange = {
                        vosotros = it
                        pasteApplied = false
                    },
                    placeholder = "decís",
                    showError = conjugationValidationAttempted && vosotros.isBlank(),
                    focusRequester = vosotrosFocusRequester,
                    onFocused = { focusTargetName = QuickAddFocus.VOSOTROS.name },
                    onNext = { requestFocus(QuickAddFocus.ELLOS_ELLAS_USTEDES) },
                )
                QuickConjugationRow(
                    person = "ELLOS / ELLAS / UDS.",
                    value = ellosEllasUstedes,
                    onValueChange = {
                        ellosEllasUstedes = it
                        pasteApplied = false
                    },
                    placeholder = "dicen",
                    showError = conjugationValidationAttempted && ellosEllasUstedes.isBlank(),
                    focusRequester = ellosEllasUstedesFocusRequester,
                    onFocused = {
                        focusTargetName = QuickAddFocus.ELLOS_ELLAS_USTEDES.name
                    },
                    imeAction = ImeAction.Done,
                    onNext = { submitVerb() },
                )

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { submitVerb() },
                    enabled = !submitLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text("Add verb", style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                requestFocus(restorableFocusTarget())
            },
            title = { Text("Discard unfinished item?") },
            text = {
                val addedSummary = if (addedCount > 0) {
                    " Your $addedCount added ${if (addedCount == 1) "item is" else "items are"} kept."
                } else {
                    ""
                }
                Text("Only the item currently being entered will be lost.$addedSummary")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        requestFocus(restorableFocusTarget())
                    }
                ) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun QuickItemTypeSelector(
    selected: LearningItemType,
    onSelect: (LearningItemType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            LearningItemType.SIMPLE_WORD to "Word",
            LearningItemType.VERB_CONJUGATION to "Verb",
        ).forEach { (type, label) ->
            val isSelected = selected == type
            Surface(
                onClick = { onSelect(type) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("quick_add_type_${type.name.lowercase()}"),
                shape = RoundedCornerShape(24.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    supportingText: String?,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = isError,
        supportingText = supportingText?.let { text -> { Text(text) } },
        minLines = minLines,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions.copy(
            capitalization = KeyboardCapitalization.None,
        ),
        keyboardActions = keyboardActions,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = quickFieldColors(),
    )
}

@Composable
private fun QuickConjugationRow(
    person: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showError: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onNext: () -> Unit,
    imeAction: ImeAction = ImeAction.Next,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = person,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (showError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 120.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) onFocused()
                }
                .semantics {
                    contentDescription = "$person conjugation"
                },
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = showError,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = imeAction,
            ),
            keyboardActions = if (imeAction == ImeAction.Done) {
                KeyboardActions(onDone = { onNext() })
            } else {
                KeyboardActions(onNext = { onNext() })
            },
            shape = RoundedCornerShape(16.dp),
            colors = quickFieldColors(),
        )
    }
}

@Composable
private fun QuickRandomlyFlipToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiz both directions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Sometimes prompt with the meaning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun quickFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)

internal fun parseSixConjugationForms(raw: String): List<String>? {
    val parts = conjugationParts(raw)
    return parts.takeIf { it.size == 6 }
}

internal fun conjugationParts(raw: String): List<String> {
    val trimmedInput = raw.trim()
    if (trimmedInput.isBlank()) return emptyList()
    val strongSeparators = Regex("(?:\\r?\\n|\\t|;)")
    val chunks = if (strongSeparators.containsMatchIn(trimmedInput)) {
        trimmedInput.split(strongSeparators)
    } else {
        listOf(trimmedInput)
    }
    val rawParts = chunks.flatMap { chunk ->
        if (':' in chunk) listOf(chunk) else chunk.split(',')
    }
    return rawParts.map { part ->
        val trimmed = part.trim()
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex > 0) trimmed.substring(colonIndex + 1).trim() else trimmed
    }
        .filter { it.isNotBlank() }
}

private fun firstMissingConjugationFocus(verb: VerbConjugation): QuickAddFocus? = when {
    verb.yo.isBlank() -> QuickAddFocus.YO
    verb.tu.isBlank() -> QuickAddFocus.TU
    verb.elEllaUsted.isBlank() -> QuickAddFocus.EL_ELLA_USTED
    verb.nosotros.isBlank() -> QuickAddFocus.NOSOTROS
    verb.vosotros.isBlank() -> QuickAddFocus.VOSOTROS
    verb.ellosEllasUstedes.isBlank() -> QuickAddFocus.ELLOS_ELLAS_USTEDES
    else -> null
}

private fun sessionDuplicateWarning(
    candidate: LearningItemDraft,
    submittedDrafts: List<LearningItemDraft>,
): String? {
    val duplicate = submittedDrafts.any { existing ->
        existing.type == candidate.type &&
            existing.term.trim().equals(candidate.term.trim(), ignoreCase = true) &&
            (
                candidate.type != LearningItemType.VERB_CONJUGATION ||
                    existing.verbConjugation?.tense?.trim().equals(
                        candidate.verbConjugation?.tense?.trim(),
                        ignoreCase = true,
                    )
                )
    }
    return when {
        !duplicate -> null
        candidate.type == LearningItemType.VERB_CONJUGATION -> {
            "This verb and tense were already added in this session"
        }
        else -> "This word was already added in this session"
    }
}
