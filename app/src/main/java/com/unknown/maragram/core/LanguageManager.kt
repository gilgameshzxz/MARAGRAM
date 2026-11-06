package com.unknown.maragram.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SupportedLanguage(val code: String) {
    ENGLISH("en"),
    ARABIC("ar")
}

object LanguageManager {
    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANG = "selected_language"

    var selectedLanguage by mutableStateOf(SupportedLanguage.ENGLISH)
        private set

    var isInitialized by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val langCode = prefs.getString(KEY_LANG, null)
        if (langCode != null) {
            selectedLanguage = SupportedLanguage.entries.find { it.code == langCode } ?: SupportedLanguage.ENGLISH
            isInitialized = true
        }
    }

    fun setLanguage(context: Context, language: SupportedLanguage) {
        selectedLanguage = language
        isInitialized = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, language.code).apply()
    }
}

data class LocalizedConcept(
    val english: String,
    val arabic: String
)

fun LocalizedConcept.get(): String = if (LanguageManager.selectedLanguage == SupportedLanguage.ARABIC) arabic else english

object SystemConcepts {
    val ABSOLUTE_LANGUAGE = LocalizedConcept(
        english = "the holy absolute language will go insane and fail",
        arabic = "اللغة المقدسة المطلقة سوف يجن جنونها وتفشل"
    )
    val DID_YOU_CHOOSE_NATIVE = LocalizedConcept(
        english = "Did you choose your native language?",
        arabic = "هل اخترت لغتك الأم؟"
    )
    val NATIVE_EXPLANATION = LocalizedConcept(
        english = "Some concepts are designed for native speakers.",
        arabic = "بعض المفاهيم مصممة للذين لغتهم الأم هي العربية"
    )
    val PERMANENT_DECISION = LocalizedConcept(
        english = "This decision is permanent\nand cannot be changed later.",
        arabic = "هذا القرار نهائي\nولا يمكن تغييره لاحقًا."
    )
    val YES = LocalizedConcept(
        english = "Yes",
        arabic = "نعم"
    )
    val NO = LocalizedConcept(
        english = "No",
        arabic = "لا"
    )
    val ENGLISH_LABEL = LocalizedConcept(
        english = "English",
        arabic = "English"
    )
    val ARABIC_LABEL = LocalizedConcept(
        english = "العربية",
        arabic = "العربية"
    )

    // Task Management Terms
    val TASK = LocalizedConcept("Task", "مهمة")
    val NEW_TASK = LocalizedConcept("New Task", "مهمة جديدة")
    val COMPLETE = LocalizedConcept("Complete", "إكمال")
    val DELETE = LocalizedConcept("Delete", "حذف")
    val EDIT = LocalizedConcept("Edit", "تعديل")
    val DUE_DATE = LocalizedConcept("Due Date", "تاريخ الاستحقاق")
    val PRIORITY = LocalizedConcept("Priority", "الأولوية")
    val REMINDER = LocalizedConcept("Reminder", "تذكير")
    val TODAY = LocalizedConcept("Today", "اليوم")
    val TOMORROW = LocalizedConcept("Tomorrow", "غدًا")
    val COMPLETED = LocalizedConcept("Completed", "مكتمل")
    val PENDING = LocalizedConcept("Pending", "قيد الانتظار")
    val CANCEL = LocalizedConcept("Cancel", "إلغاء")
    val SAVE = LocalizedConcept("Save", "حفظ")

    // General UI
    val SETTINGS = LocalizedConcept("Settings", "الإعدادات")
    val ENVIRONMENT_SETTINGS = LocalizedConcept("ENVIRONMENT SETTINGS", "إعدادات البيئة")
    val APPEARANCE = LocalizedConcept("Appearance", "المظهر")
    val WALLPAPER = LocalizedConcept("Wallpaper", "خلفية الشاشة")
    val PRO_PERSPECTIVES = LocalizedConcept("Pro Perspectives", "منظور احترافي")
    val SEARCH_UNIVERSE = LocalizedConcept("SEARCH UNIVERSE", "البحث في الكون")
    val SYSTEM_INITIALIZING = LocalizedConcept("SYSTEM INITIALIZING...", "جاري تهيئة النظام...")
    val ACCESSING_CORE = LocalizedConcept("[ ACCESSING CORE ]", "[ جاري الوصول إلى النواة ]")
    
    // General Concepts
    val EXISTENCE = LocalizedConcept("existence", "الوجود")
    val NOTHING = LocalizedConcept("nothing", "العدم")
    val SOMETHING = LocalizedConcept("something", "شيء ما")
    val DISTINCTION = LocalizedConcept("distinction", "التمايز")
    val IDENTITY = LocalizedConcept("identity", "الهوية")
    val TRUTH = LocalizedConcept("truth", "الحقيقة")
    val FALSEHOOD = LocalizedConcept("falsehood", "الزيف")
    val POSSIBILITY = LocalizedConcept("possibility", "الإمكان")
    val NECESSITY = LocalizedConcept("necessity", "الضرورة")
    val MEANING = LocalizedConcept("meaning", "المعنى")
    val KNOWLEDGE = LocalizedConcept("knowledge", "المعرفة")
    val UNCERTAINTY = LocalizedConcept("uncertainty", "عدم اليقين")
    val OBSERVER = LocalizedConcept("observer", "المراقب")
    val SELF = LocalizedConcept("self", "الذات")
    val REALITY = LocalizedConcept("reality", "الواقع")
    val ABSOLUTE = LocalizedConcept("absolute", "المطلق")
    val FUNDAMENTAL = LocalizedConcept("fundamental", "الجوهري")
    val ASSUMPTION = LocalizedConcept("assumption", "افتراض")
    val QUESTION = LocalizedConcept("question", "سؤال")
    val ANSWER = LocalizedConcept("answer", "إجابة")
    val LANGUAGE = LocalizedConcept("language", "اللغة")
    val RELATION = LocalizedConcept("relation", "العلاقة")
    val TIME = LocalizedConcept("time", "الزمان")
    val SPACE = LocalizedConcept("space", "المكان")
    val CAUSALITY = LocalizedConcept("causality", "السببية")

    val UNDEFINED_ENVIRONMENT = LocalizedConcept("ENVIRONMENT [ %s ] IS CURRENTLY UNDEFINED", "البيئة [ %s ] غير معرفة حالياً")

    // Project Titles
    val TITLE_MARAGRAM = LocalizedConcept("Maragram", "ماراغرام")
    val IDENTITY_SUBTITLE = LocalizedConcept("Identity", "الهوية")

    // Telegram Auth
    val INITIALIZE_SESSION = LocalizedConcept("Initialize Session", "بدء الجلسة")
    val PHONE_IDENTIFIER = LocalizedConcept("Phone Identifier", "معرف الهاتف")
    val ESTABLISH_CONNECTION = LocalizedConcept("ESTABLISH CONNECTION", "تأسيس الاتصال")
    val ACCESS_PROTOCOL = LocalizedConcept("Access Protocol", "بروتوكول الوصول")
    val VERIFY_ACCESS_CODE = LocalizedConcept("VERIFY TEMPORARY ACCESS CODE", "تحقق من رمز الوصول المؤقت")
    val AUTH_CODE = LocalizedConcept("Authentication Code", "رمز المصادقة")
    val AUTHORIZE = LocalizedConcept("AUTHORIZE", "مصادقة")
    val DECRYPT_WORKSPACE = LocalizedConcept("Decrypt Workspace", "فك تشفير مساحة العمل")
    val ENTER_SYSTEM_KEY = LocalizedConcept("ENTER SYSTEM KEY", "أدخل مفتاح النظام")
    val SYSTEM_PASSWORD = LocalizedConcept("System Password", "كلمة مرور النظام")
    val DECRYPT = LocalizedConcept("DECRYPT", "فك التشفير")
    val RETURN = LocalizedConcept("← RETURN", "← العودة")

    // Chat
    val MESSAGE_RECEIVED = LocalizedConcept("Message received", "تم استلام رسالة")
    val NO_MESSAGES = LocalizedConcept("No messages", "لا توجد رسائل")
    val PROFILE_PICTURE = LocalizedConcept("Profile Picture", "الصورة الشخصية")
    val SYSTEM_ORIGIN = LocalizedConcept("System Origin", "أصل النظام")
}
