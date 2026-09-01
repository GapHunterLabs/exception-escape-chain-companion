package dev.gaphunter.exceptionescapechaincompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.util.PsiTreeUtil
import dev.gaphunter.exceptionescapechaincompanion.model.SwallowedExceptionHit

/**
 * Finds a `catch (Exception e)` (the broad type exactly, not a
 * narrower specific exception) that silently swallows -- no rethrow,
 * no logging -- a specific checked exception that a call inside the
 * `try` block can actually throw, directly or transitively through a
 * bounded same-class call chain ([EscapeChainResolver]). A business
 * exception (e.g. `PaymentDeclinedException`) caught this way leaves
 * the system in an inconsistent state with no trace to diagnose the
 * real incident.
 *
 * **v0.1 scope, stated honestly:** chain depth bounded to a fixed
 * constant, only follows calls to methods of the SAME class (see
 * [EscapeChainResolver], a real narrowing from the original
 * "same class or module" design). "Swallowed silently" is a
 * name-based heuristic (no logger/print/throw call found in the catch
 * body's own text) -- same "match a known name, don't resolve a
 * symbol" discipline used elsewhere in this catalog.
 */
object JavaEscapeChainFinder {

    private val LOG_SIGNALS = listOf("log.", "logger.", "printstacktrace", "system.err", "system.out")

    fun findAll(file: PsiFile): List<SwallowedExceptionHit> {
        val hits = mutableListOf<SwallowedExceptionHit>()
        val classSummaryCache = mutableMapOf<PsiClass, Map<PsiMethod, Set<PsiClassType>>>()

        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitTryStatement(tryStatement: PsiTryStatement) {
                super.visitTryStatement(tryStatement)
                val owningClass = PsiTreeUtil.getParentOfType(tryStatement, PsiClass::class.java) ?: return
                val summaries = classSummaryCache.getOrPut(owningClass) { EscapeChainResolver.escapingExceptions(owningClass) }
                hits += hitsForTry(tryStatement, owningClass, summaries)
            }
        })
        return hits
    }

    private fun hitsForTry(
        tryStatement: PsiTryStatement,
        owningClass: PsiClass,
        summaries: Map<PsiMethod, Set<PsiClassType>>,
    ): List<SwallowedExceptionHit> {
        val tryBlock = tryStatement.tryBlock ?: return emptyList()
        val hits = mutableListOf<SwallowedExceptionHit>()

        for (catchSection in tryStatement.catchSections) {
            val catchType = catchSection.catchType as? PsiClassType ?: continue
            // Compares by text, not resolve()?.qualifiedName -- resolve()
            // returned null even for the real java.lang.Exception in this
            // catalog's own light test fixture (no JDK fully indexed,
            // confirmed the hard way via a debug scratch test), and its
            // own canonicalText stayed the short unqualified form
            // ("Exception") rather than resolving to "java.lang.Exception"
            // -- accepting either form handles both a fully-indexed real
            // project and this lighter environment the same way.
            val isBroadException = catchType.equalsToText("java.lang.Exception") || catchType.equalsToText("Exception")
            if (!isBroadException) continue // the broad catch specifically -- not a narrower one

            val catchBody = catchSection.catchBlock ?: continue
            if (!swallowsSilently(catchBody)) continue

            val reachableChecked = mutableSetOf<PsiClassType>()
            tryBlock.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(call)
                    val callee = call.resolveMethod() ?: return
                    if (callee.containingClass != owningClass) return
                    reachableChecked += summaries[callee].orEmpty()
                }
            })

            if (reachableChecked.isEmpty()) continue
            val names = reachableChecked.map { it.resolve()?.qualifiedName ?: it.presentableText }.distinct().sorted()
            val anchor = catchSection.parameter ?: catchSection
            hits += SwallowedExceptionHit(anchor, names)
        }
        return hits
    }

    /** True when [catchBody] has no evidence of logging/printing and no `throw` -- an empty catch block is the clearest case. */
    private fun swallowsSilently(catchBody: PsiCodeBlock): Boolean {
        if (catchBody.statements.isEmpty()) return true
        val text = catchBody.text.lowercase()
        val hasLogSignal = LOG_SIGNALS.any { text.contains(it) }
        val hasThrow = catchBody.text.contains("throw")
        return !hasLogSignal && !hasThrow
    }
}
