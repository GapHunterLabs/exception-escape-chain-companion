package dev.gaphunter.exceptionescapechaincompanion.model

import com.intellij.psi.PsiElement

/** One `catch (Exception e)` that silently swallows a specific checked exception reachable (directly or transitively, same class, bounded depth) from a call inside its `try` block. */
data class SwallowedExceptionHit(val anchor: PsiElement, val exceptionNames: List<String>)
