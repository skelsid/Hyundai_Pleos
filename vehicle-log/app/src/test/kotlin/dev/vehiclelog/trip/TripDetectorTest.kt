package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDetectorTest {

    @Test
    fun test_onGear_parkToMovingToPark_startsThenEnds() {
        val detector = TripDetector()
        assertNull(detector.onGear(Gear.PARK))
        assertEquals(TripEvent.STARTED, detector.onGear(Gear.MOVING))
        assertEquals(TripEvent.ENDED, detector.onGear(Gear.PARK))
        assertFalse(detector.tripOpen)
    }

    @Test
    fun test_onGear_repeatedMoving_startsOnce() {
        val detector = TripDetector()
        assertEquals(TripEvent.STARTED, detector.onGear(Gear.MOVING))
        assertNull(detector.onGear(Gear.MOVING))
    }

    @Test
    fun test_onGear_unknownDuringTrip_keepsTripOpen() {
        val detector = TripDetector()
        detector.onGear(Gear.MOVING)
        assertNull(detector.onGear(Gear.UNKNOWN))
        assertTrue(detector.tripOpen)
        assertEquals(TripEvent.ENDED, detector.onGear(Gear.PARK))
    }

    @Test
    fun test_onGear_recoveredOpenTripThenPark_ends() {
        val detector = TripDetector(tripOpen = true)
        assertNull(detector.onGear(Gear.MOVING))
        assertEquals(TripEvent.ENDED, detector.onGear(Gear.PARK))
    }

    @Test
    fun test_onGear_parkWithoutTrip_noEvent() {
        val detector = TripDetector()
        assertNull(detector.onGear(Gear.PARK))
        assertNull(detector.onGear(Gear.UNKNOWN))
        assertFalse(detector.tripOpen)
    }
}
