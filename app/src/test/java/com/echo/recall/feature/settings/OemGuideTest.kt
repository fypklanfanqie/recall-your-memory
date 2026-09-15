package com.echo.recall.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemGuideTest {

    @Test
    fun `maps xiaomi brands to a concrete guide`() {
        val guide = OemGuides.forManufacturer("Xiaomi")
        assertEquals("小米 / Redmi", guide.brand)
        assertTrue(guide.steps.isNotEmpty())
        assertTrue(guide.steps.any { it.contains("省电策略") })
    }

    @Test
    fun `redmi and poco share the xiaomi guide`() {
        val redmi = OemGuides.forManufacturer("Redmi")
        val poco = OemGuides.forManufacturer("POCO")
        assertEquals(redmi.brand, poco.brand)
    }

    @Test
    fun `is case and whitespace insensitive`() {
        val guide = OemGuides.forManufacturer("  HUAWEI ")
        assertEquals("华为 / 荣耀", guide.brand)
    }

    @Test
    fun `unknown or missing manufacturer falls back to a generic guide`() {
        val unknown = OemGuides.forManufacturer("SomeBrand")
        val nullBrand = OemGuides.forManufacturer(null)
        assertEquals("通用设置", unknown.brand)
        assertEquals("通用设置", nullBrand.brand)
        assertTrue(unknown.steps.isNotEmpty())
    }

    @Test
    fun `every known brand has at least three steps`() {
        val brands = listOf("xiaomi", "huawei", "honor", "oppo", "realme", "vivo", "samsung", "meizu", "unknown")
        brands.forEach { brand ->
            assertTrue("$brand should have steps", OemGuides.forManufacturer(brand).steps.size >= 3)
        }
    }
}
