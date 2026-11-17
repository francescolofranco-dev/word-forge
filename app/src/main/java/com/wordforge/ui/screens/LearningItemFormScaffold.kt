package com.wordforge.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.wordforge.data.LearningItemDraft
import com.wordforge.data.LearningItemType
import com.wordforge.data.VerbConjugation
import com.wordforge.ui.components.VerbTenseDropdown
import com.wordforge.ui.components.WordForgeSnackbarHost
import kotlinx.coroutines.launch

/**
 * Shared, type-aware form behind the add and edit screens.
 */
@Composable
fun LearningItemFormScaffold(
    topBarLabel: String,
    headline: String,
    subtitle: String,
    submitLabel: String,
    onSubmit: (LearningItemDraft) -> Unit,
    onNavigateBack: () -> Unit,
    initialDraft: LearningItemDraft = LearningItemDraft(),
    allowTypeSelection: Boolean = true,
    clearAfterSubmit: Boolean = false,
    successMessage: String? = null,
    autoFocus: Boolean = false,
    itemWarning: (LearningItemDraft) -> String? = { null },
) {
    var selectedTypeName by rememberSaveable {
        mutableStateOf(initialDraft.type.name)
    }
    val selectedType = LearningItemType.valueOf(selectedTypeName)
    var term by rememberSaveable { mutableStateOf(initialDraft.term) }
    var meaning by rememberSaveable { mutableStateOf(initialDraft.meaning) }
    var randomlyFlip by rememberSaveable { mutableStateOf(initialDraft.randomlyFlip) }
    val initialVerb = initialDraft.verbConjugation ?: VerbConjugation.Empty
    var tense by rememberSaveable { mutableStateOf(initialVerb.tense) }
    var yo by rememberSaveable { mutableStateOf(initialVerb.yo) }
    var tu by rememberSaveable { mutableStateOf(initialVerb.tu) }
    var elEllaUsted by rememberSaveable { mutableStateOf(initialVerb.elEllaUsted) }
    var nosotros by rememberSaveable { mutableStateOf(initialVerb.nosotros) }
    var vosotros by rememberSaveable { mutableStateOf(initialVerb.vosotros) }
    var ellosEllasUstedes by rememberSaveable {
        mutableStateOf(initialVerb.ellosEllasUstedes)
    }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var tenseMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val currentVerb = VerbConjugation(
        tense = tense,
        yo = yo,
        tu = tu,
        elEllaUsted = elEllaUsted,
        nosotros = nosotros,
        vosotros = vosotros,
        ellosEllasUstedes = ellosEllasUstedes,
    )
    val currentDraft = LearningItemDraft(
        type = selectedType,
        term = term,
        meaning = if (selectedType == LearningItemType.SIMPLE_WORD) meaning else "",
        randomlyFlip = randomlyFlip,
        verbConjugation = if (selectedType == LearningItemType.VERB_CONJUGATION) {
            currentVerb
        } else {
            null
        },
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val duplicateWarning = if (term.isBlank()) null else itemWarning(currentDraft)
    val termFocusRequester = remember { FocusRequester() }
    val meaningFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasEnteredContent = term.isNotBlank() ||
        (selectedType == LearningItemType.SIMPLE_WORD && meaning.isNotBlank()) ||
        tense.isNotBlank() ||
        yo.isNotBlank() ||
        tu.isNotBlank() ||
        elEllaUsted.isNotBlank() ||
        nosotros.isNotBlank() ||
        vosotros.isNotBlank() ||
        ellosEllasUstedes.isNotBlank()
    val hasUnsavedChanges = if (clearAfterSubmit && !hasEnteredContent) {
        false
    } else {
        currentDraft != initialDraft
    }

    fun clearFieldsAfterSubmit() {
        term = ""
        meaning = ""
        randomlyFlip = initialDraft.randomlyFlip
        tense = ""
        yo = ""
        tu = ""
        elEllaUsted = ""
        nosotros = ""
        vosotros = ""
        ellosEllasUstedes = ""
        validationAttempted = false
        tenseMenuExpanded = false
    }

    fun submitForm() {
        validationAttempted = true
        if (!currentDraft.isComplete) return

        focusManager.clearFocus()
        keyboardController?.hide()
        onSubmit(currentDraft.normalized())
        if (clearAfterSubmit) clearFieldsAfterSubmit()
        if (successMessage != null) {
            scope.launch { snackbarHostState.showSnackbar(successMessage) }
        }
    }

    fun openTenseMenu() {
        focusManager.clearFocus()
        keyboardController?.hide()
        tenseMenuExpanded = true
    }

    fun requestNavigateBack() {
        if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
    }

    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }
    LaunchedEffect(autoFocus) {
        if (autoFocus) termFocusRequester.requestFocus()
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
                        contentDescription = "Go back",
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = topBarLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        snackbarHost = { WordForgeSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = headline,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            FieldLabel("ITEM TYPE")
            Spacer(modifier = Modifier.height(10.dp))
            if (allowTypeSelection) {
                ItemTypeSelector(
                    selected = selectedType,
                    onSelect = {
                        selectedTypeName = it.name
                        tenseMenuExpanded = false
                    },
                )
            } else {
                FixedItemType(type = selectedType)
            }

            Spacer(modifier = Modifier.height(28.dp))

            FieldLabel(
                if (selectedType == LearningItemType.VERB_CONJUGATION) {
                    "INFINITIVE"
                } else {
                    "WORD OR PHRASE"
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            FormTextField(
                value = term,
                onValueChange = { term = it },
                placeholder = if (selectedType == LearningItemType.VERB_CONJUGATION) {
                    "e.g. decir"
                } else {
                    "e.g. petrichor"
                },
                isError = validationAttempted && term.isBlank(),
                supportingText = when {
                    validationAttempted && term.isBlank() -> {
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
                            meaningFocusRequester.requestFocus()
                        } else {
                            openTenseMenu()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(termFocusRequester),
            )

            if (selectedType == LearningItemType.SIMPLE_WORD) {
                Spacer(modifier = Modifier.height(24.dp))
                FieldLabel("MEANING")
                Spacer(modifier = Modifier.height(10.dp))
                FormTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    placeholder = "e.g. a pleasant smell after rain",
                    isError = validationAttempted && meaning.isBlank(),
                    supportingText = if (validationAttempted && meaning.isBlank()) {
                        "Add a meaning"
                    } else {
                        null
                    },
                    minLines = 5,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitForm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(meaningFocusRequester),
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tip — paraphrasing in your own words builds stronger memory than copy-pasting a definition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(28.dp))
                RandomlyFlipToggle(
                    checked = randomlyFlip,
                    onCheckedChange = { randomlyFlip = it },
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                FieldLabel("TENSE / MOOD")
                Spacer(modifier = Modifier.height(10.dp))
                VerbTenseDropdown(
                    value = tense,
                    expanded = tenseMenuExpanded,
                    onExpandedChange = { tenseMenuExpanded = it },
                    onTenseSelected = { tense = it },
                    isError = validationAttempted && tense.isBlank(),
                    supportingText = when {
                        validationAttempted && tense.isBlank() -> "Add the tense or mood"
                        duplicateWarning != null -> duplicateWarning
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    testTag = "edit_verb_tense",
                )

                Spacer(modifier = Modifier.height(32.dp))
                FieldLabel("CONJUGATION")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter every form. They will be revealed one at a time during review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))

                ConjugationField(
                    person = "YO",
                    placeholder = "Enter form",
                    value = yo,
                    onValueChange = { yo = it },
                    showError = validationAttempted && yo.isBlank(),
                )
                ConjugationField(
                    person = "TÚ",
                    placeholder = "Enter form",
                    value = tu,
                    onValueChange = { tu = it },
                    showError = validationAttempted && tu.isBlank(),
                )
                ConjugationField(
                    person = "ÉL / ELLA / USTED",
                    placeholder = "Enter form",
                    value = elEllaUsted,
                    onValueChange = { elEllaUsted = it },
                    showError = validationAttempted && elEllaUsted.isBlank(),
                )
                ConjugationField(
                    person = "NOSOTROS / NOSOTRAS",
                    placeholder = "Enter form",
                    value = nosotros,
                    onValueChange = { nosotros = it },
                    showError = validationAttempted && nosotros.isBlank(),
                )
                ConjugationField(
                    person = "VOSOTROS / VOSOTRAS",
                    placeholder = "Enter form",
                    value = vosotros,
                    onValueChange = { vosotros = it },
                    showError = validationAttempted && vosotros.isBlank(),
                )
                ConjugationField(
                    person = "ELLOS / ELLAS / USTEDES",
                    placeholder = "Enter form",
                    value = ellosEllasUstedes,
                    onValueChange = { ellosEllasUstedes = it },
                    showError = validationAttempted && ellosEllasUstedes.isBlank(),
                    imeAction = ImeAction.Done,
                    onDone = ::submitForm,
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = ::submitForm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(
                    text = submitLabel,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unfinished edits will be lost.") },
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
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun ItemTypeSelector(
    selected: LearningItemType,
    onSelect: (LearningItemType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LearningItemType.entries.forEach { type ->
            val isSelected = type == selected
            Surface(
                onClick = { onSelect(type) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 92.dp),
                shape = RoundedCornerShape(20.dp),
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
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when (type) {
                            LearningItemType.SIMPLE_WORD -> "A word, phrase, or expression"
                            LearningItemType.VERB_CONJUGATION -> "One verb in one tense"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FixedItemType(type: LearningItemType) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Item type can’t be changed after creation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConjugationField(
    person: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    showError: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    FieldLabel(person)
    Spacer(modifier = Modifier.height(8.dp))
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        isError = showError,
        supportingText = if (showError) "Add this conjugation" else null,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = if (onDone != null) {
            KeyboardActions(onDone = { onDone() })
        } else {
            KeyboardActions.Default
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(18.dp))
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
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
        placeholder = { Text(placeholder) },
        isError = isError,
        supportingText = supportingText?.let { text -> { Text(text) } },
        minLines = minLines,
        singleLine = singleLine,
        // Learning content follows dictionary-style lowercase across every field.
        keyboardOptions = keyboardOptions.copy(
            capitalization = KeyboardCapitalization.None,
        ),
        keyboardActions = keyboardActions,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = filledFieldColors(),
    )
}

@Composable
private fun RandomlyFlipToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Randomly flip",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sometimes quiz in reverse by showing the meaning and asking for the word.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun filledFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)
