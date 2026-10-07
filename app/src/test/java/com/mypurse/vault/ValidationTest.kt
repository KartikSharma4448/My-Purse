package com.mypurse.vault

import com.mypurse.vault.data.CardValidation
import com.mypurse.vault.data.VaultRepository
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun optionalCardNumberAndExpiryAreAccepted() {
        assertNull(CardValidation.error("Travel","Bank","Holder","",""))
    }
    @Test fun malformedCardDetailsAreRejected() {
        assertNotNull(CardValidation.error("Travel","Bank","Holder","123abc","12/30"))
        assertNotNull(CardValidation.error("Travel","Bank","Holder","4242424242424242","13/30"))
        assertNotNull(CardValidation.error("","Bank","Holder","",""))
        assertNull(CardValidation.error("Travel","Bank","Holder","4242424242424242","12/30"))
    }
    @Test fun fileContentDeterminesAcceptedType() {
        val pdf="%PDF-1.7____".toByteArray()
        assertEquals("application/pdf",VaultRepository.detectMime(pdf,pdf.size))
        assertEquals("",VaultRepository.detectMime("not a pdf___".toByteArray(),12))
        assertEquals("",VaultRepository.detectMime(byteArrayOf(),0))
        assertEquals("image/png",VaultRepository.detectMime(byteArrayOf(137.toByte(),80,78,71,13,10,26,10),8))
    }
}
