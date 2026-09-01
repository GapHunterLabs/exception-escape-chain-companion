package dev.gaphunter.exceptionescapechaincompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EscapeChainInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(EscapeChainInspection::class.java)
    }

    fun `test empty catch swallowing a directly-thrown checked exception is flagged`() {
        myFixture.configureByText(
            "PaymentService.java",
            """
            class PaymentDeclinedException extends Exception {}

            class PaymentService {
                void charge() {
                    try {
                        doCharge();
                    } catch (Exception e) {
                    }
                }

                void doCharge() throws PaymentDeclinedException {}
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("silently swallows") == true })
    }

    fun `test a checked exception reachable two calls deep is still flagged`() {
        myFixture.configureByText(
            "PaymentService2.java",
            """
            class PaymentDeclinedException2 extends Exception {}

            class PaymentService2 {
                void charge() {
                    try {
                        outer();
                    } catch (Exception e) {
                    }
                }

                void outer() throws PaymentDeclinedException2 {
                    inner();
                }

                void inner() throws PaymentDeclinedException2 {}
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("silently swallows") == true })
    }

    fun `test a catch that logs the exception is not flagged`() {
        myFixture.configureByText(
            "PaymentService3.java",
            """
            class PaymentDeclinedException3 extends Exception {}

            class PaymentService3 {
                void charge() {
                    try {
                        doCharge();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                void doCharge() throws PaymentDeclinedException3 {}
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("silently swallows") == true })
    }

    fun `test a catch that rethrows is not flagged`() {
        myFixture.configureByText(
            "PaymentService4.java",
            """
            class PaymentDeclinedException4 extends Exception {}

            class PaymentService4 {
                void charge() throws Exception {
                    try {
                        doCharge();
                    } catch (Exception e) {
                        throw e;
                    }
                }

                void doCharge() throws PaymentDeclinedException4 {}
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("silently swallows") == true })
    }

    fun `test catching a specific narrower exception type is not flagged`() {
        myFixture.configureByText(
            "PaymentService5.java",
            """
            class PaymentDeclinedException5 extends Exception {}

            class PaymentService5 {
                void charge() {
                    try {
                        doCharge();
                    } catch (PaymentDeclinedException5 e) {
                    }
                }

                void doCharge() throws PaymentDeclinedException5 {}
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("silently swallows") == true })
    }

    fun `test a call that only throws runtime exceptions is not flagged`() {
        myFixture.configureByText(
            "PlainService.java",
            """
            class PlainService {
                void charge() {
                    try {
                        doCharge();
                    } catch (Exception e) {
                    }
                }

                void doCharge() {
                    throw new IllegalStateException();
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("silently swallows") == true })
    }

    fun `test a call to a method of a different class does not count -- chain cuts there`() {
        myFixture.configureByText(
            "PaymentService6.java",
            """
            class PaymentDeclinedException6 extends Exception {}

            class OtherService {
                void doCharge() throws PaymentDeclinedException6 {}
            }

            class PaymentService6 {
                void charge(OtherService other) {
                    try {
                        other.doCharge();
                    } catch (Exception e) {
                    }
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("silently swallows") == true })
    }
}
