package com.clkyigit.toraksb.geometry

import com.clkyigit.toraksb.data.StudyFormat
import kotlin.test.assertEquals
import kotlin.test.Test

class CsvTest {
    @Test
    fun parsesQuotedFields() {
        assertEquals(listOf("a", "b,c", "d\"e", ""), StudyFormat.parse("a,\"b,c\",\"d\"\"e\","))
    }

    @Test
    fun columnsUnique() {
        val keys = StudyFormat.COLUMNS.map { it.first }
        assertEquals(keys.size, keys.toSet().size)
    }
}

class NumFormatTest {
    @Test
    fun formatsFixed() {
        kotlin.test.assertEquals("12.35", StudyFormat.num(12.345))
        kotlin.test.assertEquals("-0.50", StudyFormat.num(-0.5))
        kotlin.test.assertEquals("0.00", StudyFormat.num(-0.001))
        kotlin.test.assertEquals("3", StudyFormat.num(2.6, 0))
        kotlin.test.assertEquals("", StudyFormat.num(null))
    }
}
