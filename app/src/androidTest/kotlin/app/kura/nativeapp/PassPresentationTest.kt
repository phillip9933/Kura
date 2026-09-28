package app.kura.nativeapp

import app.kura.feature.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class PassPresentationTest {
    private fun item(json: String) = VaultItem("passes",1,"Organization","Description","generic",false,json)
    @Test fun rgbAndRgbaParsingRejectsInvalidComponents() {
        assertEquals(Color.Red,parsePassColor("rgb(255, 0, 0)"))
        assertEquals(.5f,parsePassColor("rgba(10, 20, 30, .5)")!!.alpha,1f/255f)
        for(value in listOf("rgb(256,0,0)","rgb(-1,0,0)","rgba(0,0,0,2)","rgb(NaN,0,0)","rgb(1,2)","rgba(1,2,3)","garbage",""))
            assertNull(value,parsePassColor(value))
    }
    @Test fun unreadableAndMissingColorsUseThemeFallback() {
        for(scheme in listOf(lightColorScheme(),darkColorScheme(),kuraColorScheme(false),kuraColorScheme(true))) {
            assertTrue(contrastRatio(scheme.onSurfaceVariant,scheme.surfaceVariant)>=4.5f)
            val missing=PassPresentation.from(item("{}"))
            assertEquals(scheme.surfaceVariant,passPalette(missing,scheme).background)
            val bad=missing.copy(background="rgb(255,255,255)",foreground="#ffffff")
            assertEquals(scheme.surfaceVariant,passPalette(bad,scheme).background)
        }
    }
    @Test fun labelColorAlsoMeetsSmallTextContrast() {
        val pass=PassPresentation.from(item("{}")).copy(background="rgb(0,0,0)",foreground="#ffffff",label="#111111")
        val palette=passPalette(pass,lightColorScheme())
        assertEquals(Color.Black,palette.background)
        assertTrue(contrastRatio(palette.label,palette.background)>=4.5f)
        assertEquals(Color.White,palette.label)
    }
    @Test fun missingNullAndMalformedFieldsBindSafely() {
        for(json in listOf("{}","""{"fields":null,"logoImagePath":null}""","""{"fields":"bad"}""","""{"fields":"{\"primaryFields\":[null,12,{\"label\":null,\"value\":null}]}"}""")) {
            val pass=PassPresentation.from(item(json))
            assertFalse(pass.logo=="null")
            assertTrue(pass.primary.all { it.label!="null" && it.value!="null" })
        }
        val empty=PassPresentation.from(VaultItem("passes",1,"null","null","generic",false,"""{"description":null,"organizationName":null}"""))
        assertEquals("Pass",empty.title);assertEquals("",empty.description)
        val value=PassPresentation.from(item("""{"type":"boardingPass","fields":"{\"primaryFields\":[{\"label\":\"FROM\",\"value\":\"TYO\"},{\"label\":\"TO\",\"value\":\"KIX\"}],\"headerFields\":[{\"label\":\"FLIGHT\",\"value\":\"TEST123\"}]}"}"""))
        assertEquals(listOf("TYO","KIX"),value.primary.map {it.value})
        assertEquals("TEST123",value.header.single().value)
    }
    @Test fun linksAllowOnlyWebAndDialTargets() {
        val links=passLinks("Visit https://example.org/help or call +81 90 1234 5678. javascript:alert(1)")
        assertTrue("https://example.org/help" in links)
        assertTrue("tel:+819012345678" in links)
        assertFalse(links.any {it.startsWith("javascript:")})
        assertEquals(listOf("https://example.org/Help"),passLinks("HTTPS://example.org/Help"))
    }
}
