package dev.gaphunter.exceptionescapechaincompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import dev.gaphunter.exceptionescapechaincompanion.detect.JavaEscapeChainFinder
import dev.gaphunter.exceptionescapechaincompanion.model.SwallowedExceptionHit
import dev.gaphunter.exceptionescapechaincompanion.review.ReviewPrompt

/**
 * Flags a `catch (Exception e)` that silently swallows a specific
 * checked exception a call inside its `try` block can actually
 * throw -- a business exception caught this way leaves the system in
 * an inconsistent state with no trace to diagnose the real incident.
 *
 * Runs via `checkFile` (same shape as every other inspection in this
 * catalog); [JavaEscapeChainFinder] does the real PSI walk.
 */
class EscapeChainInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null
        if (file !is PsiJavaFile) return null

        val hits = JavaEscapeChainFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: SwallowedExceptionHit): String {
        val names = hit.exceptionNames.joinToString(", ")
        return "catch (Exception e) silently swallows $names, reachable from a call in this try block -- " +
            "a real business exception caught this way leaves the system in an inconsistent state with no " +
            "trace to diagnose the incident"
    }
}
