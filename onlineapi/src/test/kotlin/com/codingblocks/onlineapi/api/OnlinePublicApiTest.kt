package com.codingblocks.onlineapi.api

import com.codingblocks.onlineapi.Clients
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlinePublicApiTest {
    val api = Clients.onlineV2PublicClient

    @Test
    fun `GET courses`() {
        val courses = api.courses.execute().body()
        courses?.let {
            assertEquals(20, it.size)
        }
    }


    @Test
    fun `GET courses|{id}`() {
        val courses = api.courseById("26").execute().body()
        courses?.let {
            assertEquals("Algo++ Online", it.title)
        }
    }

    @Test
    fun `GET instructors`() {
        val courses = api.instructors.execute().body()
        courses?.let {
            assertEquals(13, it.size)
        }
    }

    @Test
    fun `GET instructors?include=courses`() {
        val courses = api.instructors(arrayOf("courses")).execute().body()
        courses?.let {
            assertEquals(13, it.size)
        }
    }

    @Test
    fun `GET recommended`() {
        val courses = api.getRecommendedCourses().execute().body()
        courses?.let {
            assertEquals(10, it.size)
        }
    }

    @Test
    fun `GET section`() {
        val courses = api.getSections("795").execute().body()
        courses?.let {
            assertEquals("Python Basics", it.name)
        }
    }
}