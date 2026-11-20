# انتشار در Google Play — چک‌لیست و مراحل

این سند وضعیت استانداردهای لازم برای انتشار و کارهایی را که فقط از داخل Play
Console یا روی دستگاه واقعی انجام می‌شود فهرست می‌کند.

## ۱. نتیجهٔ بررسی استانداردها (در همین مخزن)

| مورد | وضعیت | توضیح |
|---|---|---|
| مجوز اینترنت | ✅ ندارد | `INTERNET` در مانيفست نیست؛ اپ کاملاً آفلاین است |
| ترافیک cleartext | ✅ مسدود | `android:usesCleartextTraffic="false"` |
| هدف API | ✅ ۳۶ | `compileSdk`/`targetSdk` = 36 |
| حداقل API | ✅ ۲۶ | `minSdk` = 26 |
| ۱۶KB page size | ✅ تأیید شد | همهٔ `lib/*.so` با `LOAD align = 0x4000` |
| R8/Shrink | ✅ روشن | `isMinifyEnabled` + `isShrinkResources` برای release |
| Debggable در release | ✅ خاموش | با `aapt2 dump badging` بررسی شد |
| لاگ/چاپ داده | ✅ ندارد | هیچ `Log.`، `printStackTrace` یا `System.out` در کد نیست |
| کوئری SQL دستی | ✅ ندارد | همه از Room و پارامترهای bound |
| PendingIntent | ✅ IMMUTABLE | در `DailyReminderWorker` |
| مؤلفه‌های exported | ✅ حداقلی | فقط `MainActivity` (لانچر/دیپ‌لینک) و receiver ویجت |
| بکاپ | ✅ محدود | فقط `wazhebox.db`، `sharedpref` و `datastore` |
| امضای upload key | ⚠️ باید ساخته شود | فعلاً `artifacts/wazhebox-test.jks` فقط برای تست محلی است |
| سیاست حریم خصوصی | ⚠️ باید میزبانی شود | متن در `PRIVACY.md` |
| دارایی‌های فروشگاه | ⚠️ باید آماده شود | آیکون ۵۱۲، feature graphic، اسکرین‌شات‌ها |

## ۲. کلید امضا (upload key)

برای Play هرگز از کلید تست استفاده نکن. یک کلید تازه بساز و **جای امن** نگه دار؛
اگر گم شود باید از Google درخواست ریست کلید بدهی.

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

سپس در ریشهٔ پروژه فایل `keystore.properties` بساز (در `.gitignore` هست و
هیچ‌وقت کامیت نمی‌شود):

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

با وجود این فایل، `assembleRelease` و `bundleRelease` خودکار با همین کلید امضا
می‌شوند. بدون آن، بیلد بدون امضا می‌ماند و برای بازرسی محلی به کار می‌رود.

در Play Console گزینهٔ **Play App Signing** را فعال کن: Google کلید نهایی توزیع
را نگه می‌دارد و کلید تو فقط «upload key» است.

## ۳. ساخت بسته‌ها

```bash
export JAVA_HOME=… ANDROID_HOME=…
./gradlew :app:testDebugUnitTest :app:bundleRelease :app:assembleRelease
```

خروجی‌ها:

- `app/build/outputs/bundle/release/app-release.aab` — همان چیزی که آپلود می‌شود
- `app/build/outputs/apk/release/app-release.apk` — برای نصب دستی/تست
- `app/build/outputs/mapping/release/mapping.txt` — **نگه دار و در هر انتشار
  آپلود کن** تا کرش‌های R8 روی Play قابل خواندن شوند
- `app/build/outputs/mapping/release/configuration.txt` و `seeds.txt` — برای بازرسی

نسخه را قبل از هر آپلود بالا ببر (در `app/build.gradle.kts`):
`versionCode` باید یک عدد صعودی باشد؛ `versionName` متن نمایشی است.

## ۴. فیلدهای Play Console

**Data safety** (فرم ایمنی داده):

- Does your app collect or share any of the required user data types? → **No**
- Is all of the user data encrypted in transit? → لازم نیست (هیچ داده‌ای ارسال نمی‌شود)
- Do you provide a way for users to request that their data be deleted? → **No**
  (هیچ داده‌ای روی سرور نیست؛ حذف اپ همه‌چیز را پاک می‌کند)

**App access:** همهٔ امکانات بدون ورود در دسترس است → «All functionality is
available without restrictions».

**Ads:** اپ هیچ تبلیغی ندارد → No ads.

**Content rating:** پرسش‌نامه را با «هیچ محتوای حساس/خشونت/محتوای کاربرساخته»
پر کن؛ نتیجه معمولاً درجهٔ ۳+ یا همهٔ سنین است.

**Target audience:** ۱۳+ یا همهٔ سنین؛ چون هیچ داده‌ای جمع نمی‌شود، الزامات
خانواده (Families policy) لازم نمی‌شود.

**Permissions:** فقط `POST_NOTIFICATIONS` و `VIBRATE` (به‌همراه مجوزهای
WorkManager). هیچ مجوز حساسی (مکان، مخاطبین، دوربین، اینترنت) وجود ندارد.

**Privacy policy URL:** متن `PRIVACY.md` را روی یک صفحهٔ عمومی (مثلاً GitHub
Pages) منتشر کن و لینکش را وارد کن. ایمیل پشتیبانی را جای `<YOUR-SUPPORT-EMAIL>`
بگذار.

## ۵. صفحهٔ فروشگاه

محدودیت طول: عنوان ≤ ۳۰ نویسه، توضیح کوتاه ≤ ۸۰، توضیح کامل ≤ ۴۰۰۰.

- **عنوان:** واژه‌باکس — مرور لغت با جعبهٔ لایتنر
- **توضیح کوتاه:** ۱۲۹۵ واژهٔ آماده، مرور جعبه‌ای آفلاین، بدون تبلیغ و بدون اینترنت.
- **توضیح کامل (پیش‌نویس):** واژه‌باکس یک اپ ساده و آفلاین برای مرور واژگان
  انگلیسی است. دک آمادهٔ ۹۰روزه با ۱۲۹۵ واژه (A1 تا B1)، کارت سه‌بعدی با ضربه،
  زمان‌بندی FSRS، آمار روزانه، ویجت صفحهٔ خانه و پشتیبان‌گیری JSON/CSV. هیچ
  مجوز اینترنتی ندارد؛ داده‌ها روی گوشی خودت می‌مانند.

دارایی‌های گرافیکی لازم (باید روی دستگاه/ابزار طراحی ساخته شوند؛ در این محیط
بدون امولاتور قابل تولید نیستند):

- آیکون اپ ۵۱۲×۵۱۲ PNG (از همان آیکون adaptive موجود اکسپورت شود)
- Feature graphic ۱۰۲۴×۵۰۰
- حداقل ۲ اسکرین‌شات گوشی (خانه، کارت مرور، آمار) — نسبت ۱۶:۹ یا ۹:۱۶

## ۶. مسیر انتشار

1. Internal testing: AAB را آپلود کن، روی حداقل دو گوشی تست کن (Android 10 و 15).
2. Closed testing (اختیاری، ۱۲ تستر) برای گرفتن بازخورد چیدمان فارسی.
3. Production با staged rollout (۱۰٪ → ۵۰٪ → ۱۰۰٪).

## ۷. کارهای باقی‌مانده پیش از آپلود

- [ ] ساخت upload key و نگه‌داری نسخهٔ پشتیبان آن
- [ ] میزبانی `PRIVACY.md` و جای‌گذاری ایمیل پشتیبانی
- [ ] گرفتن اسکرین‌شات‌ها و ساخت آیکون ۵۱۲ و feature graphic
- [ ] تست دستی روی دستگاه: نصب تازه، آنبوردینگ، جلسهٔ مرور، ویجت، اعلان
- [ ] بررسی ظاهری حالت تاریک و فونت بزرگ سیستم
- [ ] آپلود `mapping.txt` همراه بسته
