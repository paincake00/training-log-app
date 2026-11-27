package com.denispodpovetniy.traininglog.ui.plan

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.denispodpovetniy.traininglog.R
import com.denispodpovetniy.traininglog.data.repository.PlanImportSummary
import com.denispodpovetniy.traininglog.ui.components.EditorDialog

/** Предпросмотр импорта: что изменится и предупреждение о замене плана. */
@Composable
fun PlanImportDialog(
    summary: PlanImportSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    EditorDialog(
        title = stringResource(R.string.plan_import_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.plan_import_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.dialog_cancel),
    ) {
        Text(
            text = stringResource(
                R.string.plan_import_summary,
                summary.addedExercises,
                summary.updatedExercises,
                summary.planRows,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.plan_import_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
