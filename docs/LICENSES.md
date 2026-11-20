# مجوزها و منابع

این فایل بر اساس PRD §۲۵ نگه داشته می‌شود. هر منبعی که وارد اپ می‌شود، باید اینجا ثبت شود.

## فونت‌ها

| فونت | نسخه/فایل | مجوز |
|---|---|---|
| Vazirmatn | `res/font/vazirmatn.ttf`, `vazirmatn_medium.ttf`, `vazirmatn_bold.ttf` | SIL Open Font License 1.1 |
| Inter | `res/font/inter.ttf` (فونت متغیر) | SIL Open Font License 1.1 |

فونت‌ها همراه اپ عرضه می‌شوند و در زمان اجرا دانلود نمی‌شوند.

## کتابخانه‌ها

| کتابخانه | مجوز |
|---|---|
| AndroidX (Core, Activity, Lifecycle, Navigation, Room, WorkManager, DataStore, Glance) | Apache License 2.0 |
| Jetpack Compose (UI, Material 3) | Apache License 2.0 |
| Dagger / Hilt | Apache License 2.0 |
| Kotlin, kotlinx-coroutines, kotlinx-datetime, kotlinx-serialization | Apache License 2.0 |

## الگوریتم

الگوریتم FSRS-6 بر اساس مستندات عمومی پروژهٔ
[open-spaced-repetition](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm)
پیاده‌سازی شده است. پارامترهای پیش‌فرض (۲۱ مقدار) از همان سند آمده‌اند. کد کتابخانه‌ای
کپی نشده و پیاده‌سازی، توابع خالص Kotlin است.

## محتوای دک استارتر

۶۰۳ مدخل در `app/src/main/assets/starter_deck.csv`:

- فهرست واژگان: انتخاب دستی بر اساس بسامد پایهٔ انگلیسی (A1–B1).
- ترجمه‌ها، جمله‌های مثال و IPA: تولید محتوای این پروژه.
- وضعیت بازبینی: نیازمند بازبینی انسانی نهایی پیش از انتشار در Google Play
  (معیار PRD §13.4: نمونهٔ تصادفی ۱۰۰تایی بدون غلط معنایی).

## صدا

هیچ فایل صوتی همراه اپ نیست. تلفظ فقط از موتور TTS سیستم می‌آید (PRD §15).
