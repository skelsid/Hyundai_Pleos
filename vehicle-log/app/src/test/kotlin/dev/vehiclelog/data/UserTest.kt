package dev.vehiclelog.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserTest {

    private val kimSales = User(1, "Kim", "Sales")
    private val kimNoDepartment = User(2, "Kim", null)

    @Test
    fun test_isSamePerson_sameNameAndDepartment_true() {
        assertTrue(kimSales.isSamePerson("Kim", "Sales"))
    }

    @Test
    fun test_isSamePerson_caseAndSpacesDiffer_true() {
        assertTrue(kimSales.isSamePerson(" kim ", " SALES "))
    }

    @Test
    fun test_isSamePerson_differentDepartment_false() {
        // 이름이 같아도 부서가 다르면 다른 사람으로 본다
        assertFalse(kimSales.isSamePerson("Kim", "Finance"))
        assertFalse(kimSales.isSamePerson("Kim", null))
    }

    @Test
    fun test_isSamePerson_noDepartmentBothSides_true() {
        assertTrue(kimNoDepartment.isSamePerson("Kim", null))
        assertTrue(kimNoDepartment.isSamePerson("Kim", "  "))
    }

    @Test
    fun test_isSamePerson_differentName_false() {
        assertFalse(kimSales.isSamePerson("Lee", "Sales"))
    }

    @Test
    fun test_label_withDepartment_showsDepartmentInBrackets() {
        assertEquals("Kim (Sales)", kimSales.label)
    }

    @Test
    fun test_label_noDepartment_nameOnly() {
        assertEquals("Kim", kimNoDepartment.label)
        assertEquals("Kim", User(3, "Kim", " ").label)
    }
}
