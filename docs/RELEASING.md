# نسخه‌بندی و انتشار تکرارپذیر

منبع حقیقت نسخه، فایل [`version.properties`](../version.properties) است:

```properties
versionCode=1
versionName=1.0.0
```

Gradle همین دو مقدار را می‌خواند (`app/build.gradle.kts`)، پس **هیچ‌وقت** برای
انتشار فایل بیلد را دستی ویرایش نمی‌کنیم. `versionCode` باید با هر آپلود در Play
یک واحد بالا برود؛ `versionName` چیزی است که کاربر می‌بیند.

## دستورها

```bash
python3 tools/release.py show          # نسخهٔ فعلی
python3 tools/release.py bump --type patch --note "رفع بریدگی فهرست کتابخانه"
python3 tools/release.py bump --type minor --note "افزودن حالت شب" --build
```

- `--type patch|minor|major` بخشی از semver را بالا می‌برد.
- `--version 1.2.0` مقدار دقیق را جای محاسبهٔ خودکار می‌گذارد.
- `--note` الزامی است و می‌تواند چند بار بیاید؛ هر کدام یک بولت در `CHANGELOG.md`.
- `--build` تست‌ها، `lintRelease`، `bundleRelease` و `assembleRelease` را اجرا
  می‌کند و خروجی‌ها را در `artifacts/` می‌گذارد.
- `--dry-run` فقط نشان می‌دهد چه چیزی عوض می‌شود؛ چیزی نمی‌نویسد.

## خروجی `--build`

```
artifacts/wazhebox-<versionName>-release.aab          ← برای آپلود در Play
artifacts/wazhebox-<versionName>-release-signed.apk   ← نصب دستی/تست
artifacts/mapping-<versionName>.txt                   ← فایل deobfuscation
artifacts/wazhebox-<versionName>-release.sha256       ← هش هر دو بسته
```

اسکریپت اگر `keystore.properties` در ریشه باشد با همان کلید امضا می‌کند و اگر
نباشد هشدار «UNSIGNED» می‌دهد. ساختن کلید و ثبت آن در Play App Signing در
[`PLAY_RELEASE.md`](PLAY_RELEASE.md) آمده است.

## ترتیب کار پیشنهادی برای هر انتشار

1. تغییرها را روی `main` کامیت کن (و push).
2. `python3 tools/release.py bump --type … --note "…"` ← فقط نسخه و changelog.
3. `git commit -am "release: vX.Y.Z"` و `git tag vX.Y.Z`.
4. `python3 tools/release.py bump --build` را دوباره اجرا **نکن**؛ به‌جای آن
   اگر بسته را جدا می‌خواهی: `./gradlew :app:bundleRelease :app:assembleRelease`.
   (اگر مرحلهٔ ۲ را با `--build` زدی، همین کافی است و نسخه فقط یک بار بالا رفته.)
5. AAB و `mapping-<version>.txt` را در Play Console آپلود کن.
6. پس از تأیید، internal → closed → production (staged rollout).

## قواعد سخت

- `versionCode` هرگز پایین نمی‌آید و در دو بیلد تکرار نمی‌شود؛ Play آپلود تکراری
  را رد می‌کند.
- `mapping.txt` هر انتشار را نگه دار؛ بدون آن کرش‌های R8 روی Play خوانا نیستند.
- `keystore.properties`، `*.jks` و `artifacts/` در گیت نیستند. کلید امضا را
  جداگانه و امن پشتیبان بگیر.
- نسخه را فقط از این مسیر عوض کن؛ ویرایش دستی `version.properties` هم مجاز است
  ولی بعدش یادت باشد `CHANGELOG.md` را هم به‌روز کنی.

## اگر بیلد شکست خورد

اسکریپت به‌محض خطای Gradle با کد خطا خارج می‌شود و نسخه را **قبلاً** نوشته است.
اگر بیلد شکست خورد: علت را رفع کن و دوباره `--build` بزن؛ نسخه را دستی پایین
نیاور و دوباره bump نکن (وگرنه یک versionCode بی‌استفاده می‌سوزد که اشکالی ندارد،
اما changelog دو ورودی می‌گیرد).
