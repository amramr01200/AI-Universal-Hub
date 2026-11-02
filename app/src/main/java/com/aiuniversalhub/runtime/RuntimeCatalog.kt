package com.aiuniversalhub.runtime

data class RuntimeComponent(
    val id: String,
    val name: String,
    val category: String,
    val purposeAr: String,
    val purposeEn: String,
    val installCommand: String,
    val noteAr: String,
    val noteEn: String
)

object RuntimeCatalog {
    val components = listOf(
        RuntimeComponent("python", "Python", "Language", "تشغيل سكريبتات وأدوات الذكاء الاصطناعي", "Run scripts and AI utilities", "pkg install python", "يحتاج بيئة Linux متوافقة مثل Termux.", "Requires a compatible Linux userspace such as Termux."),
        RuntimeComponent("pip", "pip", "Python packages", "فحص مدير حزم Python", "Check the Python package installer", "python -m pip --version", "فحص فقط؛ تثبيت مكتبة محددة يحتاج اسم الحزمة وموافقتك.", "Check only; installing a specific library requires its name and your approval."),
        RuntimeComponent("uv", "uv", "Python packages", "إدارة سريعة لبيئات وحزم Python", "Fast Python project and package manager", "python -m pip install uv", "يتطلب Python وبيئة متوافقة.", "Requires Python and a compatible environment."),
        RuntimeComponent("node", "Node.js + npm", "JavaScript", "تشغيل أدوات JavaScript وإدارة حزم npm", "Run JavaScript tools and manage npm packages", "pkg install nodejs-lts", "npm يأتي مع حزمة Node.js في Termux.", "npm is bundled with the Node.js package in Termux."),
        RuntimeComponent("git", "Git", "Developer tools", "تنزيل وإدارة المشاريع والإصدارات", "Clone and manage source projects", "pkg install git", "يمكن ربطه بمستودعات GitHub بعد إعداد الصلاحيات.", "Can connect to GitHub after permissions are configured."),
        RuntimeComponent("clang", "Clang", "Build tools", "أدوات ترجمة وبناء بعض الحزم", "Compiler toolchain for compatible packages", "pkg install clang", "قد تحتاج بعض الحزم إلى إعدادات بناء إضافية.", "Some packages need additional build configuration."),
        RuntimeComponent("cmake", "CMake", "Build tools", "إدارة عمليات بناء المشاريع", "Project build configuration", "pkg install cmake", "ليس بديلًا عن Android SDK أو NDK.", "Does not replace the Android SDK or NDK."),
        RuntimeComponent("rust", "Rust", "Language", "بناء أدوات ومكونات مكتوبة بـ Rust", "Build Rust-based tools and components", "pkg install rust", "التوافق يعتمد على المعمارية والحزم المطلوبة.", "Compatibility depends on architecture and dependencies."),
        RuntimeComponent("ffmpeg", "FFmpeg", "Media tools", "معالجة وتحويل الوسائط", "Process and convert media", "pkg install ffmpeg", "يتطلب مساحة تخزين مناسبة.", "Requires sufficient storage."),
        RuntimeComponent("proot", "proot-distro", "Linux environments", "تشغيل توزيعات Linux معزولة نسبيًا", "Run isolated Linux userlands", "pkg install proot-distro", "لا يوفر حاويات Docker أو صلاحيات root حقيقية.", "Does not provide Docker containers or real root privileges."),
        RuntimeComponent("apt", "pkg / apt", "Package manager", "تحديث فهرس الحزم في Termux", "Refresh package indexes in Termux", "pkg update", "التحديث لا يثبت أو يحدّث كل البرامج تلقائيًا.", "Refreshes package indexes; it does not automatically upgrade every package.")
    )
}
