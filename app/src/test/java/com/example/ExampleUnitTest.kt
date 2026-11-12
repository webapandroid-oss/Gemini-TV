package com.example

import com.abd.webapp.android.google.gemini.`interface`.GeminiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testDeveloperAndCoderQueries() = runBlocking {
    val service = GeminiService { "" }

    val testQueries = listOf(
      "সাধারণ প্রশ্ন (User)" to "What is photosynthesis in 2 sentences?",
      "সফটওয়্যার আর্কিটেকচার (Developer)" to "Explain MVVM architecture in Android with Kotlin",
      "অ্যালগরিদম ও কোড (Coder)" to "Write a quicksort algorithm in Python",
      "বাইনারি সার্চ (Coder)" to "How does binary search work? Write Python code",
      "কোটলিন কোরোউটিন (Developer)" to "কোটলিন কোরোউটিন (Kotlin Coroutines) কীভাবে কাজ করে কোডসহ বুঝিয়ে বলো",
      "প্যালিনড্রোম চেকার (Coder)" to "Write a palindrome checker in Python",
      "স্বাভাবিক গ্রিটিং (User)" to "Hello, how are you today?"
    )

    for ((category, query) in testQueries) {
      val result = service.generateContent(query)
      assertTrue("Result should be success", result.isSuccess)
      val reply = result.getOrNull()
      assertNotNull("Reply should not be null", reply)

      println("==================================================")
      println("[$category] প্রশ্ন: $query")
      println("উত্তর:")
      println(reply)
      println("==================================================")

      // Verify developer query does NOT return a canned greeting:
      if (query.contains("MVVM", ignoreCase = true)) {
        assertTrue("MVVM query must explain ViewModel", reply!!.contains("ViewModel"))
        assertTrue("MVVM query must not be a fake greeting", !reply.contains("I am doing great"))
      }

      // Verify coder quicksort query contains actual code:
      if (query.contains("quicksort", ignoreCase = true)) {
        assertTrue("Quicksort query must contain code block", reply!!.contains("def quicksort"))
      }

      // Verify coroutine query contains suspend or dispatchers:
      if (query.contains("Coroutines", ignoreCase = true)) {
        assertTrue("Coroutine query must explain suspend or dispatchers", reply!!.contains("suspend") || reply.contains("Dispatchers"))
      }

      // Verify greeting query does reply with greeting:
      if (query.startsWith("Hello")) {
        assertTrue("Greeting should be polite greeting", reply!!.contains("Hello") || reply.contains("assist"))
      }
    }
  }
}
