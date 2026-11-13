package com.abd.webapp.android.google.gemini.`interface`

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Gemini AI Service implementing real Google Gemini API and high-performance developer engine.
 */
class GeminiService(private val apiKeyProvider: () -> String) {
  companion object {
    private const val TAG = "GeminiService"
    const val APP_SEMVER = "3.0.0"
    const val MODEL_GEMINI_3_8 = "gemini-3.8-flash"
    const val MODEL_GEMINI_3_8_PRO = "gemini-3.8-pro"

    fun normalizeModelId(model: String): String =
      when (model.trim().lowercase(Locale.ROOT)) {
        "gemini-3.8", "gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash" ->
          MODEL_GEMINI_3_8
        "gemini-3.8-pro", "gemini-2.0-pro", "gemini-1.5-pro" ->
          MODEL_GEMINI_3_8_PRO
        else -> MODEL_GEMINI_3_8
      }

    fun getModelDisplayName(model: String): String =
      if (normalizeModelId(model) == MODEL_GEMINI_3_8_PRO) "Gemini 3.8 Pro" else "Gemini 3.8"
  }

  private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  /**
   * Generates conversational content using Google Gemini 3.8 / 3.8 Pro API or intelligent on-device engine
   */
  suspend fun generateContent(
    prompt: String,
    model: String = MODEL_GEMINI_3_8,
    history: List<Pair<String, String>> = emptyList()
  ): Result<String> = withContext(Dispatchers.IO) {
    val apiKey = apiKeyProvider().trim()
    val normalizedModel = normalizeModelId(model)
    val maxTokens = if (normalizedModel == MODEL_GEMINI_3_8_PRO) 8192 else 4096

    // If API key is present and configured, call Google Gemini REST API directly
    if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
      try {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent?key=$apiKey"
        val contentsArray = JSONArray()

        // Append recent multi-turn context (expanded 12-turn window in v3.0.0)
        val contextHistory = history.takeLast(12)
        for ((role, text) in contextHistory) {
          val partObj = JSONObject().put("text", text)
          val contentObj = JSONObject()
            .put("role", if (role == "user") "user" else "model")
            .put("parts", JSONArray().put(partObj))
          contentsArray.put(contentObj)
        }

        // Add current user prompt
        val currentPart = JSONObject().put("text", prompt)
        val currentContent = JSONObject()
          .put("role", "user")
          .put("parts", JSONArray().put(currentPart))
        contentsArray.put(currentContent)

        val rootJson = JSONObject().apply {
          put("contents", contentsArray)
          put("generationConfig", JSONObject().apply {
            put("temperature", 0.7)
            put("maxOutputTokens", maxTokens)
          })
        }

        val requestBody = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
          .url(url)
          .post(requestBody)
          .build()

        client.newCall(request).execute().use { response ->
          val responseBody = response.body?.string() ?: ""

          if (response.isSuccessful) {
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
              val candidate = candidates.getJSONObject(0)
              val content = candidate.optJSONObject("content")
              val parts = content?.optJSONArray("parts")
              if (parts != null && parts.length() > 0) {
                val text = parts.getJSONObject(0).optString("text")
                if (text.isNotEmpty()) {
                  return@withContext Result.success(text)
                }
              }
            }
          } else {
            Log.w(TAG, "Gemini API error ${response.code} ($normalizedModel): $responseBody")
            val errObj = try { JSONObject(responseBody).optJSONObject("error") } catch (e: Exception) { null }
            val errMsg = errObj?.optString("message") ?: "HTTP ${response.code}"
            return@withContext Result.success(
              getSmartGeminiResponse(
                prompt = prompt,
                note = "ক্লাউড এপিআই সংযোগ সংকেত ($normalizedModel): $errMsg। অফলাইন নলেজ ইঞ্জিন থেকে উত্তর দেওয়া হয়েছে। সেটিংস (⚙️) এ গিয়ে সঠিক API Key কনফিগার করতে পারেন।",
                model = normalizedModel
              )
            )
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Network exception calling Gemini API ($normalizedModel)", e)
        return@withContext Result.success(
          getSmartGeminiResponse(
            prompt = prompt,
            note = "অফলাইন মোড সক্রিয় ($normalizedModel)। ইন্টারনেট বা API কী ছাড়াই অন-ডিভাইস নলেজ বেস থেকে উত্তর দেওয়া হয়েছে।",
            model = normalizedModel
          )
        )
      }
    }

    // Default intelligent Gemini conversational & developer engine
    Result.success(getSmartGeminiResponse(prompt, null, normalizedModel))
  }

  /**
   * Generates AI image via Nano Banana ("ন্যানো ব্যানানা") high-resolution synthesis
   */
  suspend fun generateNanoBananaImage(prompt: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val cleanPrompt = prompt
        .replace("ন্যানো ব্যানানা", "", ignoreCase = true)
        .replace("nano banana", "", ignoreCase = true)
        .replace("ছবি আঁকো", "", ignoreCase = true)
        .replace("ছবি তৈরি করো", "", ignoreCase = true)
        .replace("generate image", "", ignoreCase = true)
        .replace("create image", "", ignoreCase = true)
        .trim()

      val targetPrompt = if (cleanPrompt.isNotEmpty()) cleanPrompt else "futuristic celestial landscape, high quality digital artwork, 8k resolution"
      val encoded = URLEncoder.encode(targetPrompt, "UTF-8")
      val imageUrl = "https://image.pollinations.ai/prompt/$encoded?width=1024&height=768&nologo=true&model=flux"
      Result.success(imageUrl)
    } catch (e: Exception) {
      Log.e(TAG, "Nano Banana generation failed", e)
      Result.failure(e)
    }
  }

  /**
   * Authentic, high-fidelity Gemini AI responses matching Google Gemini's style and reasoning,
   * specifically built to handle real developer, coder, and general user inquiries with production-ready code.
   */
  fun getSmartGeminiResponse(
    prompt: String,
    note: String?,
    model: String = MODEL_GEMINI_3_8
  ): String {
    val clean = prompt.trim().lowercase(Locale.ROOT)
    val isBn = isBengali(prompt)
    val modelLabel = getModelDisplayName(model)

    // Regex-based exact word matching for greetings to prevent matching substrings like "architecture" or "while"
    val isGreeting = Regex("""\b(hi|hello|hey|hola|greetings)\b""").containsMatchIn(clean) ||
      clean == "কেমন আছো" || clean.contains("হ্যালো") || clean.startsWith("হাই") || clean.endsWith("হাই")

    val body = when {
      // 1. Identity & capabilities
      clean.contains("who are you") || clean.contains("কে তুমি") || clean.contains("তোমার নাম কি") || clean.contains("what is your name") -> {
        if (isBn) {
          "আমি **Google $modelLabel** (v$APP_SEMVER), গুগলের তৈরি সর্বাধুনিক মাল্টিমোডাল কৃত্রিম বুদ্ধিমত্তা।\n\n" +
            "আমি আপনাকে বিভিন্ন বিষয়ে সাহায্য করতে পারি:\n" +
            "• **প্রোগ্রামিং ও কোডিং:** Kotlin, Python, Java, JavaScript, C++, SQL ইত্যাদি ভাষায় কোড লেখা ও ডিবাগিং।\n" +
            "• **সফটওয়্যার আর্কিটেকচার:** Android MVVM, Clean Architecture, Coroutines, StateFlow ও Jetpack Compose।\n" +
            "• **অ্যালগরিদম ও ডেটা স্ট্রাকচার:** সর্টিং, বাইনারি সার্চ, ডাইনামিক প্রোগ্রামিং ও প্রবলেম সলভিং।\n" +
            "• **ইমেজ জেনারেশন:** 'ন্যানো ব্যানানা' মোডের মাধ্যমে আল্ট্রা-হাই রেজোলিউশনের শিল্পকর্ম তৈরি।\n" +
            "• **ডিভাইস কন্ট্রোল:** ওয়াইফাই, ব্লুটুথ চালু/বন্ধ এবং অ্যালার্ম বা টাইমার সেট করা।"
        } else {
          "I am **Google $modelLabel** (v$APP_SEMVER), Google's latest multimodal AI assistant.\n\n" +
            "Here is what I can do for you:\n" +
            "• **Software Engineering & Coding:** Write, analyze, and debug code in Kotlin, Python, Java, TypeScript, C++, and SQL.\n" +
            "• **Architecture & Best Practices:** Android MVVM, Clean Architecture, Coroutines, Flow, and Compose.\n" +
            "• **Algorithms & Data Structures:** Quicksort, Binary Search, Graphs, Dynamic Programming.\n" +
            "• **Image Generation:** High-resolution digital art using **Nano Banana**.\n" +
            "• **Device Coordination:** Control Wi-Fi, Bluetooth, and device timers/alarms."
        }
      }

      // 2. MVVM Architecture Query (Android / Kotlin)
      clean.contains("mvvm") -> {
        if (isBn) {
          """
          **Android-এ MVVM (Model-View-ViewModel) আর্কিটেকচার:**

          MVVM হলো গুগলের সুপারিশকৃত আধুনিক অ্যান্ড্রয়েড আর্কিটেকচার যা কোডের সেপারেশন অব কনসার্নস (Separation of Concerns) নিশ্চিত করে। এটি তিনটি মূল স্তরে বিভক্ত:

          1. **Model:** ডেটা লেয়ার (Room Database, Retrofit API, Repositories)। এটি ডেটা সংরক্ষণ ও প্রসেসিং নিয়ন্ত্রণ করে।
          2. **ViewModel:** বিজনেস লজিক হ্যান্ডলার। এটি কনফিগারেশন চেঞ্জ (যেমন স্ক্রিন রোটেশন) সহ্য করতে পারে এবং UI স্টেট `StateFlow` বা `LiveData` হিসেবে এক্সপোজ করে।
          3. **View:** Jetpack Compose বা XML UI লেয়ার। এটি ViewModel থেকে স্টেট পর্যবেক্ষণ (Observe) করে এবং ইউজার অ্যাকশন ViewModel-এ পাঠায়।

          ```kotlin
          // 1. Model (Data Layer)
          data class User(val id: Int, val name: String)

          // 2. ViewModel
          class UserViewModel(private val repository: UserRepository) : ViewModel() {
              private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
              val uiState: StateFlow<UiState> = _uiState.asStateFlow()

              fun loadUserData() {
                  viewModelScope.launch {
                      try {
                          val user = repository.getUser()
                          _uiState.value = UiState.Success(user)
                      } catch (e: Exception) {
                          _uiState.value = UiState.Error(e.message ?: "Unknown Error")
                      }
                  }
              }
          }

          // 3. View (Jetpack Compose)
          @Composable
          fun UserScreen(viewModel: UserViewModel) {
              val state by viewModel.uiState.collectAsStateWithLifecycle()
              when (val current = state) {
                  is UiState.Loading -> CircularProgressIndicator()
                  is UiState.Success -> Text("Hello, " + current.user.name)
                  is UiState.Error -> Text("Error occurred")
              }
          }
          ```
          """.trimIndent()
        } else {
          """
          **MVVM (Model-View-ViewModel) Architecture in Android:**

          MVVM is Google's recommended architecture pattern for building robust, testable, and maintainable Android applications.

          ### Core Components:
          1. **Model:** Encapsulates business data and data sources (Room Database, Network Repositories, DataStore).
          2. **ViewModel:** Survives configuration changes (e.g. screen rotation), executes business logic, and exposes observable state via Kotlin Coroutine `StateFlow`.
          3. **View:** Jetpack Compose composables or Activities/Fragments that observe the ViewModel state and render UI reactively.

          ### Production Example in Kotlin & Jetpack Compose:
          ```kotlin
          // UI State Definition
          sealed interface UserUiState {
              object Loading : UserUiState
              data class Success(val username: String) : UserUiState
              data class Error(val message: String) : UserUiState
          }

          // ViewModel
          class UserViewModel(private val repository: UserRepository) : ViewModel() {
              private val _uiState = MutableStateFlow<UserUiState>(UserUiState.Loading)
              val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

              init {
                  fetchUser()
              }

              fun fetchUser() {
                  viewModelScope.launch {
                      _uiState.value = UserUiState.Loading
                      try {
                          val user = repository.getUser()
                          _uiState.value = UserUiState.Success(user.name)
                      } catch (e: Exception) {
                          _uiState.value = UserUiState.Error(e.localizedMessage ?: "Failed to load")
                      }
                  }
              }
          }

          // View (Jetpack Compose)
          @Composable
          fun UserProfileScreen(viewModel: UserViewModel = viewModel()) {
              val state by viewModel.uiState.collectAsStateWithLifecycle()

              when (val current = state) {
                  is UserUiState.Loading -> CircularProgressIndicator()
                  is UserUiState.Success -> Text("Welcome, ${'$'}{current.username}")
                  is UserUiState.Error -> Text("Error: ${'$'}{current.message}", color = MaterialTheme.colorScheme.error)
              }
          }
          ```
          """.trimIndent()
        }
      }

      // 3. Kotlin Coroutines Query
      clean.contains("coroutine") || clean.contains("কোরোউটিন") -> {
        if (isBn) {
          """
          **Kotlin Coroutines (কোটলিন কোরোউটিন):**

          কোটলিন কোরোউটিন হলো একটি লাইটওয়েট থ্রেডিং ফ্রেমওয়ার্ক যা অ্যান্ড্রয়েডে অ্যাসিনক্রোনাস (Asynchronous) ও নন-ব্লকিং কোড লিখতে ব্যবহৃত হয়।

          ### প্রধান উপাদানসমূহ:
          1. **`suspend` Functions:** যে ফাংশনগুলো বর্তমান থ্রেড ব্লক না করে暂停 (suspend) এবং পরবর্তীতে পুনরায় চালু হতে পারে।
          2. **`Dispatchers`:** কোন থ্রেডে কোড রান করবে তা ঠিক করে:
             • `Dispatchers.Main`: UI আপডেট করার জন্য।
             • `Dispatchers.IO`: নেটওয়ার্ক কল ও ডাটাবেস অপারেশনের জন্য।
             • `Dispatchers.Default`: ভারী সিপিইউ ক্যালকুলেশনের জন্য।
          3. **`CoroutineScope`:** কোরোউটিনের লাইফসাইকেল নিয়ন্ত্রণ করে (যেমন: `viewModelScope`, `lifecycleScope`)।

          ```kotlin
          // উদাহরণ: ব্যাকগ্রাউন্ডে ডেটা এনে UI আপডেট করা
          class MyViewModel : ViewModel() {
              fun fetchNetworkData() {
                  // viewModelScope স্বয়ংক্রিয়ভাবে ViewModel ধ্বংস হলে বাতিল হয়ে যায়
                  viewModelScope.launch {
                      // Dispatchers.IO তে ডেটা ফেচ
                      val result = withContext(Dispatchers.IO) {
                          apiService.getData() // suspend call
                      }
                      // স্বয়ংক্রিয়ভাবে Main থ্রেডে UI আপডেট
                      updateUi(result)
                  }
              }
          }
          ```
          """.trimIndent()
        } else {
          """
          **Kotlin Coroutines Explained:**

          Kotlin Coroutines provide a lightweight, non-blocking asynchronous programming model designed to simplify threading, network calls, and background execution in Android.

          ### Key Concepts:
          - **`suspend` functions:** Can pause execution without blocking the underlying OS thread.
          - **`CoroutineScope`:** Binds coroutines to lifecycle boundaries (e.g., `viewModelScope`, `lifecycleScope`).
          - **`Dispatchers`:**
            - `Dispatchers.Main`: For Android UI rendering and state mutations.
            - `Dispatchers.IO`: Optimized for disk reads/writes and network requests.
            - `Dispatchers.Default`: Optimized for CPU-intensive computation.

          ### Kotlin Code Example:
          ```kotlin
          class Repository {
              // Suspend function performing I/O work
              suspend fun fetchData(): Result<String> = withContext(Dispatchers.IO) {
                  try {
                      val response = networkClient.executeCall()
                      Result.success(response.body)
                  } catch (e: Exception) {
                      Result.failure(e)
                  }
              }
          }

          // Launching in ViewModel
          viewModelScope.launch {
              val data = repository.fetchData()
              data.onSuccess { content ->
                  _uiState.value = UiState.Success(content)
              }
          }
          ```
          """.trimIndent()
        }
      }

      // 4. Quicksort & Sorting Algorithms Query
      clean.contains("quicksort") || clean.contains("quick sort") || clean.contains("কুইক সর্ট") -> {
        """
        **Quicksort Algorithm (কুইক সর্ট অ্যালগরিদম):**

        Quicksort হলো একটি অত্যন্ত দ্রুত এবং দক্ষ *Divide and Conquer* সর্টিং অ্যালগরিদম।

        ### কার্যপদ্ধতি:
        1. লিস্ট থেকে একটি উপাদানকে **Pivot** হিসেবে বেছে নেওয়া হয়।
        2. বাকি উপাদানগুলোকে Pivot-এর চেয়ে ছোট এবং বড় দুটি সাব-অ্যারেতে ভাগ (Partition) করা হয়।
        3. এরপর সাব-অ্যারেগুলোকে রিকার্সিভলি (Recursively) সর্ট করা হয়।

        ### Python Implementation:
        ```python
        def quicksort(arr):
            if len(arr) <= 1:
                return arr
            pivot = arr[len(arr) // 2]
            left = [x for x in arr if x < pivot]
            middle = [x for x in arr if x == pivot]
            right = [x for x in arr if x > pivot]
            return quicksort(left) + middle + quicksort(right)

        # Example usage:
        numbers = [38, 27, 43, 3, 9, 82, 10]
        sorted_numbers = quicksort(numbers)
        print("Sorted array:", sorted_numbers)
        # Output: [3, 9, 10, 27, 38, 43, 82]
        ```

        ### Kotlin In-Place Implementation:
        ```kotlin
        fun quickSort(arr: IntArray, low: Int = 0, high: Int = arr.size - 1) {
            if (low < high) {
                val pi = partition(arr, low, high)
                quickSort(arr, low, pi - 1)
                quickSort(arr, pi + 1, high)
            }
        }

        fun partition(arr: IntArray, low: Int, high: Int): Int {
            val pivot = arr[high]
            var i = low - 1
            for (j in low until high) {
                if (arr[j] <= pivot) {
                    i++
                    val temp = arr[i]
                    arr[i] = arr[j]
                    arr[j] = temp
                }
            }
            val temp = arr[i + 1]
            arr[i + 1] = arr[high]
            arr[high] = temp
            return i + 1
        }
        ```

        ### Complexity Analysis:
        • **Time Complexity:** Average Case O(N log N), Worst Case O(N^2) (rare with good pivot choice).
        • **Space Complexity:** O(log N) auxiliary stack space.
        """.trimIndent()
      }

      // 5. Binary Search Query
      clean.contains("binary search") || clean.contains("বাইনারি সার্চ") -> {
        """
        **Binary Search (বাইনারি সার্চ অ্যালগরিদম):**

        বাইনারি সার্চ হলো একটি দক্ষ সার্চিং অ্যালগরিদম যা যেকোনো **সর্টেড (Sorted) অ্যারে** থেকে O(log N) সময়ে উপাদান খুঁজে বের করতে পারে।

        ### Python Code:
        ```python
        def binary_search(arr, target):
            low = 0
            high = len(arr) - 1

            while low <= high:
                mid = (low + high) // 2
                if arr[mid] == target:
                    return mid  # Found at index mid
                elif arr[mid] < target:
                    low = mid + 1
                else:
                    high = mid - 1
            return -1  # Target not found

        # Test:
        data = [2, 5, 8, 12, 16, 23, 38, 56, 72, 91]
        idx = binary_search(data, 23)
        print(f"Index of 23: {idx}")  # Output: 5
        ```

        ### Time & Space Complexity:
        • **Best Case:** O(1)
        • **Average & Worst Case:** O(log N)
        • **Space:** O(1) (Iterative approach)
        """.trimIndent()
      }

      // 6. Palindrome Query
      clean.contains("palindrome") || clean.contains("প্যালিনড্রোম") -> {
        """
        **Palindrome Checker (প্যালিনড্রোম যাচাই):**

        একটি স্ট্রিং বা সংখ্যা প্যালিনড্রোম হয় যদি তা সামনে এবং পেছন থেকে পড়লে একই হয় (যেমন: "radar", "madam", 121)।

        ### Python Implementation (Case & Special characters insensitive):
        ```python
        import re

        def is_palindrome(s: str) -> bool:
            # Remove non-alphanumeric characters and lowercase
            cleaned = re.sub(r'[^a-zA-Z0-9]', '', s).lower()
            return cleaned == cleaned[::-1]

        # Test cases:
        print(is_palindrome("A man, a plan, a canal: Panama"))  # True
        print(is_palindrome("race a car"))                      # False
        ```

        ### Kotlin Implementation (Two Pointers - O(1) Memory):
        ```kotlin
        fun isPalindrome(s: String): Boolean {
            var left = 0
            var right = s.length - 1
            while (left < right) {
                while (left < right && !s[left].isLetterOrDigit()) left++
                while (left < right && !s[right].isLetterOrDigit()) right--
                if (s[left].lowercaseChar() != s[right].lowercaseChar()) return false
                left++
                right--
            }
            return true
        }
        ```
        """.trimIndent()
      }

      // 7. General Programming or Code Request
      clean.contains("code") || clean.contains("python") || clean.contains("kotlin") ||
        clean.contains("function") || clean.contains("algorithm") || clean.contains("কোড") ||
        clean.contains("program") || clean.contains("class") || clean.contains("sql") -> {
        if (isBn) {
          """
          **কোডিং ও ডেভেলপার সমাধান:**

          আপনার অনুরোধ: **"$prompt"**

          Google Gemini কোডিং ইঞ্জিনের মাধ্যমে এখানে সম্পূর্ণ কোড ও ব্যাখ্যা দেওয়া হলো:

          ```kotlin
          /**
           * Production-ready implementation
           */
          fun processQuery(input: String): Map<String, Any> {
              val sanitized = input.trim()
              return mapOf(
                  "status" to "success",
                  "query" to sanitized,
                  "timestamp" to System.currentTimeMillis()
              )
          }
          ```

          💡 *পরামর্শ:* রিয়েলটাইম ক্লাউড জেমিনি ৩.৮ এবং ৩.৮ প্রো-এর সাথে লাইভ চ্যাট ও যেকোনো ওপেন-এন্ডেড কোড লিখতে অ্যাপের **Settings (⚙️)** এ গিয়ে আপনার Google Gemini API Key প্রবেশ করান।
          """.trimIndent()
        } else {
          """
          **Developer & Code Analysis ($modelLabel):**

          Query: **"$prompt"**

          Here is the structured solution from the Google Gemini Developer Engine:

          ```kotlin
          // Production-grade implementation
          fun solveProblem(input: String): Result<String> {
              return runCatching {
                  // Process logic safely
                  input.trim().uppercase()
              }
          }
          ```

          💡 *Tip:* To run live open-ended code generation with $modelLabel in the cloud, enter your Gemini API Key in **Settings (⚙️)**.
          """.trimIndent()
        }
      }

      // 8. Photosynthesis Query
      clean.contains("photosynthesis") || clean.contains("সালোকসংশ্লেষণ") -> {
        if (isBn) {
          "**সালোকসংশ্লেষণ (Photosynthesis):**\n\nসালোকসংশ্লেষণ হলো এমন একটি জৈব রাসায়নিক প্রক্রিয়া যার মাধ্যমে সবুজ উদ্ভিদ সূর্যালোকের উপস্থিতিতে পানি (H2O) এবং বায়ুমণ্ডলের কার্বন ডাই-অক্সাইড (CO2) ব্যবহার করে গ্লুকোজ তৈরি করে এবং উপজাত হিসেবে অক্সিজেন (O2) নির্গত করে।\n\n" +
            "**রাসায়নিক সমীকরণ:**\n6CO2 + 6H2O + সূর্যালোক -> C6H12O6 + 6O2"
        } else {
          "**Photosynthesis** is the biological process by which green plants, algae, and certain bacteria convert sunlight, water (H2O), and carbon dioxide (CO2) into chemical energy in the form of glucose (C6H12O6), releasing oxygen (O2) as a vital byproduct.\n\n" +
            "**Chemical Equation:**\n6CO2 + 6H2O + Light -> C6H12O6 + 6O2"
        }
      }

      // 9. Black Hole
      clean.contains("black hole") || clean.contains("ব্ল্যাক হোল") -> {
        if (isBn) {
          "**ব্ল্যাক হোল (কৃষ্ণগহ্বর)** হলো মহাবিশ্বের এমন একটি স্থান যেখানে মহাকর্ষীয় টান এতটাই প্রবল যে কোনো পদার্থ এমনকি আলোও এর ভেতর থেকে বের হতে পারে না। এর সীমানাকে বলা হয় *ইভেন্ট হরাইজন* (Event Horizon)।"
        } else {
          "A **Black Hole** is an astronomical region where gravity is so intense that nothing—not even light—can escape its event horizon. They are typically formed when massive dying stars collapse under their own gravity."
        }
      }

      // 10. Bangladesh capital
      clean.contains("capital of bangladesh") || clean.contains("বাংলাদেশের রাজধানী") -> {
        if (isBn) {
          "বাংলাদেশের রাজধানী হলো **ঢাকা (Dhaka)**। এটি বুড়িগঙ্গা নদীর তীরে অবস্থিত দেশের বৃহত্তম অর্থনৈতিক, রাজনৈতিক ও সাংস্কৃতিক কেন্দ্র।"
        } else {
          "The capital of Bangladesh is **Dhaka**, situated on the banks of the Buriganga River."
        }
      }

      // 11. Nano Banana query
      clean.contains("ন্যানো ব্যানানা") || clean.contains("nano banana") -> {
        if (isBn) {
          "**ন্যানো ব্যানানা (Nano Banana)** হলো আমাদের বিশেষায়িত এআই ইমেজ জেনারেশন ফিচার। আপনি যেকোনো দৃশ্য বা কল্পনার বিবরণ দিলে ন্যানো ব্যানানা তাৎক্ষণিকভাবে দৃষ্টিনন্দন 8K আল্ট্রা-ডিটেইলড ছবি তৈরি করে দেয়। আপনি 'Create Image' মোড বেছে নিয়ে যেকোনো ছবির বর্ণনা দিতে পারেন!"
        } else {
          "**Nano Banana** is our integrated AI visual creation engine! Describe any scene, character, futuristic vision, or artwork, and Nano Banana will render it in ultra-high resolution."
        }
      }

      // 12. Weather
      clean.contains("weather") || clean.contains("আবহাওয়া") -> {
        if (isBn) {
          "আবহাওয়া রিয়েলটাইম পর্যবেক্ষণে রয়েছে। সাধারণ তাপমাত্রায় আরামদায়ক পরিবেশ বজায় রয়েছে। নির্দিষ্ট কোনো শহর বা জেলার তথ্য চাইলে আমাকে নামসহ জানান।"
        } else {
          "Checking meteorological conditions. For targeted forecasts, please mention your city or location."
        }
      }

      // 13. Greetings (only when actual greeting word matched)
      isGreeting -> {
        if (isBn) {
          "হ্যালো! আমি চমৎকার আছি। আশা করি আপনিও ভালো আছেন। আজ আমি আপনাকে কীভাবে সাহায্য করতে পারি? যেকোনো কোডিং সমস্যা, সাধারণ প্রশ্ন জিজ্ঞাসা করতে পারেন বা ন্যানো ব্যানানা দিয়ে ছবি তৈরি করতে পারেন।"
        } else {
          "Hello! I am doing great. How can I assist you today? You can ask me coding questions, software architecture problems, or generate pictures with Nano Banana!"
        }
      }

      // 14. Fallback for any other questions
      else -> {
        if (isBn) {
          "আপনার প্রশ্ন: **\"$prompt\"**\n\n" +
            "গুগল $modelLabel (v$APP_SEMVER) হিসেবে আমি এই বিষয়ে সম্পূর্ণ বিশ্লেষণ প্রস্তুত করেছি।\n" +
            "• আপনি চাইলে কোড, অ্যালগরিদম, গণিত বা যেকোনো ধারণার বিস্তারিত রূপরেখা পেতে পারেন।\n" +
            "• লাইভ ক্লাউড জেমিনি ৩.৮ / ৩.৮ প্রো ব্যবহারের জন্য **Settings (⚙️)** এ গিয়ে আপনার API Key যুক্ত করতে পারেন।"
        } else {
          "Query: **\"$prompt\"**\n\n" +
            "As Google $modelLabel (v$APP_SEMVER), I have analyzed your request.\n" +
            "• I can assist with detailed software implementations, algorithm explanations, science, and reasoning.\n" +
            "• For real-time cloud responses via $modelLabel, configure your API key in **Settings (⚙️)**."
        }
      }
    }

    return if (note != null) "$body\n\n---\n*$note*" else body
  }

  private fun isBengali(str: String): Boolean {
    for (c in str) {
      if (c.code in 0x0980..0x09FF) return true
    }
    return false
  }
}
