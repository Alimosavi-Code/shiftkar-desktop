# ShiftKar Neo — شیفت‌کار نئو (Desktop Edition v6.2.0) 🖥️

نسخهٔ دسکتاپ **شیفت‌کار نئو** با **Kotlin + Compose Multiplatform for Desktop** برای:
- 🐧 **Linux** (`.deb`, `.rpm`, `.AppImage`)
- 🍎 **macOS** (`.dmg`)
- 🪟 **Windows** (`.msi`, `.exe`)

*(بدون هیچ کد اضافهٔ iOS یا Android — کاملاً اختصاصی برای دسکتاپ)*

---

## 🔑 آیا نسخهٔ دسکتاپ به Keystore یا تنظیمات خاصی نیاز دارد؟

**خیر! برخلاف اندروید، برای ساخت و اجرای نسخه‌های دسکتاپ به هیچ Keystore یا فایل `local.properties` یا اکانت پولی نیاز ندارید.**

- **Linux (`.deb` / `.rpm`)**: هیچ نیازی به امضای دیجیتال (Code Signing) ندارد و مستقیم نصب و اجرا می‌شود.
- **Windows (`.msi` / `.exe`)**: بدون هیچ گواهی‌نامه‌ای ساخته و نصب می‌شود. (در اولین اجرا روی ویندوز، اگر گواهی تجاری چندصد دلاری EV نداشته باشید، پنجرهٔ آبی Windows SmartScreen ظاهر می‌شود که کاربر با زدن **More info → Run anyway** برنامه را نصب می‌کند. همچنین گزینهٔ `perUserInstall = true` فعال شده تا حتی بدون نیاز به دسترسی Administrator هم نصب شود).
- **macOS (`.dmg`)**: بدون اکانت اپل ساخته و نصب می‌شود. (اگر گواهی Developer ID اپل نداشته باشید، کاربر در اولین باز کردن روی مک کافی است روی آیکون برنامه **راست‌کلیک کرده و Open را بزند** یا در `System Settings → Privacy & Security` گزینهٔ **Open Anyway** را بزند).

---

## 💻 ۱. روش اجرا و ساخت روی سیستم خودتان (Local Build)

### پیش‌نیازها:
- نصب بودن **JDK 17** یا بالاتر (پیشنهاد: Eclipse Temurin 17).
- *(نکته مهم)*: ابزار بسته‌بندی دسکتاپ (`jpackage`) فقط می‌تواند نصابِ **همین سیستم‌عاملی که الان رویش هستید** را به صورت محلی بسازد (مثلاً روی لینوکس `.deb` می‌سازد و روی ویندوز `.msi`). برای ساخت هر ۳ سیستم‌عامل به‌صورت هم‌زمان، از روش آنلاین (GitHub Actions) در بخش ۲ استفاده کنید.

### دستورات ترمینال:

```bash
# ۱. اجرای مستقیم و زنده برنامه روی دسکتاپ (بدون نیاز به ساخت نصاب):
./gradlew :composeApp:run

# ۲. اجرای برنامه به صورت مستقل (Unpackaged binary) برای تست سرعت نهایی:
./gradlew :composeApp:runDistributable

# ۳. ساخت فایل نصاب برای سیستم‌عامل فعلی شما:
./gradlew :composeApp:packageDistributionForCurrentOS
```

یا ساخت فرمت مشخص بر اساس سیستم‌عامل فعلی:
- **روی Linux** (نیاز به بستهٔ پایهٔ `fakeroot` و `dpkg-dev` که معمولاً روی اوبونتو/دبیان نصب است):
  ```bash
  ./gradlew :composeApp:packageDeb
  # خروجی در: composeApp/build/compose/binaries/main/deb/shiftkar_6.2.0-1_amd64.deb
  ```
- **روی Windows** (نیاز به نصب بودن [WiX Toolset v3](https://wixtoolset.org/) برای `.msi` یا Inno Setup برای `.exe`):
  ```powershell
  .\gradlew.bat :composeApp:packageMsi
  # خروجی در: composeApp\build\compose\binaries\main\msi\ShiftKar-6.2.0.msi
  ```
- **روی macOS**:
  ```bash
  ./gradlew :composeApp:packageDmg
  # خروجی در: composeApp/build/compose/binaries/main/dmg/ShiftKar-6.2.0.dmg
  ```

---

## ☁️ ۲. روش ساخت آنلاین هر ۳ سیستم‌عامل بدون نیاز به مک یا ویندوز (GitHub Actions)

فایل گردش‌کار آماده در مسیر `.github/workflows/build-desktop.yml` قرار داده شده است. سرورهای رایگان GitHub Actions تمام ابزارهای لازم (JDK 17، WiX Toolset روی ویندوز، ابزارهای DMG روی مک و ابزارهای DEB روی اوبونتو) را از قبل نصب دارند!

1. یک مخزن (Repository) در GitHub بسازید و کل محتویات این پوشه را در آن Push کنید:
   ```bash
   git init
   git add .
   git commit -m "Initial ShiftKar Desktop v6.2.0"
   git branch -M main
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
   git push -u origin main
   ```
2. وارد تب **Actions** در مخزن گیت‌هاب خود شوید.
3. گردش‌کار **Build Desktop Installers** به صورت خودکار شروع می‌شود (یا می‌توانید دکمهٔ **Run workflow** را بزنید).
4. پس از چند دقیقه، در پایین صفحهٔ اجرا در بخش **Artifacts** هر ۳ خروجی آمادهٔ دانلود هستند:
   - 📦 **`ShiftKar-Linux-DEB`** (فایل نصاب `.deb` برای لینوکس)
   - 🍎 **`ShiftKar-macOS-DMG`** (فایل نصاب `.dmg` برای مک)
   - 🪟 **`ShiftKar-Windows-Installers`** (فایل‌های نصاب `.msi` و `.exe` برای ویندوز)
