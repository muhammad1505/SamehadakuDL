package app.samehadaku.dl.data.autocheck

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCheckGateTest {

    @Test
    fun `only one check can enter until the gate is released`() {
        assertTrue(AutoCheckGate.tryEnter())
        try {
            assertFalse(AutoCheckGate.tryEnter())
        } finally {
            AutoCheckGate.exit()
        }

        assertTrue(AutoCheckGate.tryEnter())
        AutoCheckGate.exit()
    }
}
