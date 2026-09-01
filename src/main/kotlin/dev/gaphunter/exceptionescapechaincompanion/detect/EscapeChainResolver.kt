package dev.gaphunter.exceptionescapechaincompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.InheritanceUtil

/**
 * Computes, for every method of a class, the real set of CHECKED
 * exception types that can escape up to a caller -- not just the
 * method's own `throws` clause, but merged transitively through calls
 * to OTHER methods of the SAME class, up to [MAX_DEPTH] levels deep.
 *
 * **v0.1 scope, stated honestly (a real narrowing from the original
 * design):** only follows calls to methods of the SAME class -- a call
 * to a method of a different class (including one in the same module)
 * is treated as unknown and cuts the chain there, same as a call to an
 * external library. Cross-class same-module resolution would need the
 * same heavier module-graph machinery `module-boundary-leak-companion`
 * uses; staying same-class-only keeps this mechanism honestly testable
 * within this catalog's PSI-only, no-backend constraint.
 *
 * Memoized with cycle protection, same shape as
 * `deadlock-lock-order-companion`'s `TransitiveLockResolver` -- a real
 * call cycle between methods contributes an empty set rather than
 * recursing forever.
 */
object EscapeChainResolver {

    private const val MAX_DEPTH = 3
    private const val MAX_METHODS_PER_CLASS = 200

    fun escapingExceptions(psiClass: PsiClass): Map<PsiMethod, Set<PsiClassType>> {
        val memo = mutableMapOf<PsiMethod, Set<PsiClassType>>()
        if (psiClass.methods.size > MAX_METHODS_PER_CLASS) return memo
        for (method in psiClass.methods) {
            resolve(method, psiClass, memo, linkedSetOf(), depth = 0)
        }
        return memo
    }

    private fun resolve(
        method: PsiMethod,
        owningClass: PsiClass,
        memo: MutableMap<PsiMethod, Set<PsiClassType>>,
        inProgress: LinkedHashSet<PsiMethod>,
        depth: Int,
    ): Set<PsiClassType> {
        memo[method]?.let { return it }
        if (method in inProgress) return emptySet() // real call cycle -- break it here

        inProgress += method
        val checked = mutableSetOf<PsiClassType>()

        for (declaredType in method.throwsList.referencedTypes) {
            if (isCheckedException(declaredType)) checked += declaredType
        }

        if (depth < MAX_DEPTH) {
            method.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(call)
                    val callee = call.resolveMethod() ?: return
                    if (callee.containingClass != owningClass) return // a different class -- unknown, chain cuts here
                    checked += resolve(callee, owningClass, memo, inProgress, depth + 1)
                }
            })
        }

        inProgress -= method
        memo[method] = checked
        return checked
    }

    /** True for a real checked exception -- excludes `RuntimeException`/`Error` subtypes (and anything unresolved is treated as checked, the safer default). */
    private fun isCheckedException(type: PsiClassType): Boolean {
        val resolved = type.resolve() ?: return true
        return !InheritanceUtil.isInheritor(resolved, "java.lang.RuntimeException") &&
            !InheritanceUtil.isInheritor(resolved, "java.lang.Error")
    }
}
