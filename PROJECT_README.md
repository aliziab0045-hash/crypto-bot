# ربات معاملاتی اسکالپینگ (نسخه چند صرافی + طلا با MT5)

این ربات حالا می‌تونه به چند صرافی/بروکر وصل بشه: **Binance, Bybit, KuCoin, Nobitex, Toobit, LBank** (کریپتو) و **MT5** برای طلا و فارکس از طریق **آمارکتس (AMarkets)** یا **ای‌پلنت (ePlanet)** — اضافه کردن صرافی جدید هم راحته.

## طلا از طریق آمارکتس / ای‌پلنت (MT5)
نه آمارکتس و نه ای‌پلنت هیچ‌کدوم یه API مستقیم ندارن — این دو بروکر (مثل تقریباً همه بروکرهای فارکس) فقط از طریق نرم‌افزار دسکتاپ/موبایل **MetaTrader 5** کار می‌کنن، نه یه وب-API معمولی مثل صرافی‌های کریپتو.
برای وصل کردن ربات به هر حساب MT5ای (چه آمارکتس، چه ای‌پلنت، چه هر بروکر دیگه)، از یه سرویس واسط معتبر و رایج به اسم **MetaApi.cloud** استفاده شده که دقیقاً همین کار رو می‌کنه: به حساب MT5ت وصل می‌شه و یه REST API ساده در اختیارت می‌ذاره — برای یک حساب، رایگانه.

نحوه استفاده:
1. اول تو آمارکتس یا ای‌پلنت یه حساب (دمو یا واقعی) بساز و لاگین/پسورد/اسم سرور MT5 رو یادداشت کن
2. یه حساب رایگان تو https://app.metaapi.cloud بساز
3. تو پنل MetaApi، «Add account» بزن و همون لاگین/پسورد/سرور آمارکتس یا ای‌پلنت رو وارد کن
4. بعد از اضافه شدن، از صفحه حساب یه **API token** و یه **account id** می‌گیری
5. تو اپ: اسم صرافی = `amarkets` یا `eplanet` (هر دو دقیقاً یکسان کار می‌کنن)، جفت‌ارز = `XAUUSD`، API Key = همون token، API Secret = همون account id

فعلاً baseUrl کد روی ریجن پیش‌فرض MetaApi (`new-york`) تنظیمه؛ اگه حسابت تو ریجن دیگه‌ای ساخته شده بود، تو پنل MetaApi ریجن درست رو ببین و به Claude بگو تا آدرس رو اصلاح کنه.

فعلاً بقیه صرافی‌های کریپتو روی حالت‌های متفاوتی تنظیم شدن (Binance/Bybit روی Testnet، بقیه روی آدرس اصلی)، برای تست امن.

## ساختار فایل‌ها

```
com.example.cryptobot/
├── MainActivity.kt          -> صفحه اصلی اپ (دکمه شروع/توقف، لاگ زنده)
├── ScalpingStrategy.kt      -> منطق تصمیم‌گیری خرید/فروش
├── RiskManager.kt           -> حد ضرر، حد سود، مبلغ هر معامله
├── TradingBot.kt            -> حلقه اصلی ربات (با هر صرافی کار می‌کنه)
└── exchanges/
    ├── ExchangeClient.kt    -> رابط مشترک همه صرافی‌ها
    ├── ExchangeHttp.kt      -> توابع مشترک HTTP/امضا
    ├── ExchangeFactory.kt   -> ساخت کلاینت صرافی با اسم
    ├── BinanceExchange.kt
    ├── BybitExchange.kt
    └── KuCoinExchange.kt

res/layout/
└── activity_main.xml        -> چیدمان صفحه اصلی (فیلدهای ورودی + دکمه‌ها + لاگ)
```

## قدم‌های راه‌اندازی

1. یه پروژه Android خالی (Empty Activity) بساز، زبان: **Kotlin**
2. این خط‌ها رو به `build.gradle` (ماژول app) اضافه کن:
   ```gradle
   dependencies {
       implementation("com.squareup.okhttp3:okhttp:4.12.0")
       implementation("org.json:json:20231013")
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
       implementation("androidx.appcompat:appcompat:1.7.0")
   }
   ```
3. همه فایل‌های Kotlin رو با همون ساختار پوشه‌ای بالا داخل `app/src/main/java/com/example/cryptobot/` بذار
4. فایل `activity_main.xml` رو داخل `app/src/main/res/layout/` بذار (اگه از قبل یه `activity_main.xml` داری، جایگزینش کن)
5. مطمئن شو در `AndroidManifest.xml`، `MainActivity` به‌عنوان launcher activity ثبت شده و اجازه اینترنت اضافه شده:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />

   <application ...>
       <activity android:name=".MainActivity" android:exported="true">
           <intent-filter>
               <action android:name="android.intent.action.MAIN" />
               <category android:name="android.intent.category.LAUNCHER" />
           </intent-filter>
       </activity>
   </application>
   ```

## گرفتن API Key تست از هر صرافی

| صرافی | آدرس تست | نکته |
|---|---|---|
| Binance | testnet.binance.vision | فقط Key + Secret |
| Bybit | testnet.bybit.com | فقط Key + Secret |
| KuCoin | sandbox.kucoin.com | Key + Secret + **Passphrase** |

## نحوه استفاده از اپ

وقتی اپ رو اجرا کردی، یه صفحه ساده می‌بینی:

1. اسم صرافی رو بنویس: `binance` یا `bybit` یا `kucoin`
2. جفت ارز رو بنویس، مثلاً `BTCUSDT`
3. API Key و Secret تستت رو وارد کن (برای KuCoin، Passphrase هم لازمه)
4. دکمه **شروع** رو بزن
5. لاگ زنده ربات (قیمت‌ها، تصمیم‌ها، خرید/فروش) پایین صفحه نمایش داده می‌شه
6. با دکمه **توقف** هر وقت خواستی ربات رو متوقف کن

فعلاً اپ فقط یه صرافی رو در آن واحد اجرا می‌کنه؛ برای اجرای هم‌زمان چند صرافی، باید چند نمونه `TradingBot` بسازیم و UI رو گسترش بدیم — اگه لازم شد بگو تا اضافه کنم.

## اضافه کردن یه صرافی جدید بعداً (مثلاً OKX یا Coinbase)

1. یه فایل جدید بساز، مثلاً `OkxExchange.kt` داخل پوشه `exchanges/`
2. رابط `ExchangeClient` رو پیاده‌سازی کن (۴ تابع: name، getCurrentPrice، getRecentCloses، placeMarketOrder) — از `BinanceExchange.kt` یا `BybitExchange.kt` به عنوان الگو استفاده کن
3. یه خط به `ExchangeFactory.kt` اضافه کن
همین! بقیه ربات (استراتژی، مدیریت ریسک، UI) دست‌نخورده می‌مونه.

## تنظیمات فعلی ربات

- تایم‌فریم سیگنال: **4H**
- سرمایه پیش‌فرض هر BotSlot: **100 USD**
- ریسک هر معامله: **1%** از سرمایه
- حد ضرر: **1%**
- حد سود: **1.5%**
- بررسی وضعیت: هر **60 ثانیه**؛ سیگنال بر اساس 20 کندل 4H
- Telegram بعد از هر BUY/SELL ارسال می‌شود و خطای Telegram در لاگ قابل پیگیری است.

> نکته: مقدار 1% ریسک در این نسخه بر اساس equity تنظیم‌شده در اپ محاسبه می‌شود، نه موجودی زنده حساب صرافی. قبل از اتصال پول واقعی باید endpoint موجودی هر صرافی هم اضافه و با quantity/lot-size همان صرافی اعتبارسنجی شود.

## قدم‌های بعدی

- [ ] اضافه کردن صرافی‌های بیشتر (OKX, Coinbase, MEXC, Gate.io, ...)
- [ ] ساخت رابط کاربری (نمایش سود/زیان هر صرافی، دکمه شروع/توقف)
- [ ] بهبود استراتژی با اندیکاتورهای بیشتر یا مدل هوش مصنوعی واقعی
- [ ] ذخیره تاریخچه معاملات
- [ ] فقط بعد از تست کامل، به صرافی‌ها و پول واقعی وصل بشیم (تغییر baseUrl از sandbox به آدرس اصلی)

## ⚠️ نکته مهم

هنوز روی Sandbox/Testnet هستیم — عمداً. قبل از استفاده با پول واقعی، حتماً باید:
1. استراتژی رو مدت‌طولانی‌تری تست کنیم
2. مطمئن بشیم امضای هر صرافی درست کار می‌کنه (بعضی صرافی‌ها جزئیات امنیتی‌شون فرق داره)
3. کلیدهای API واقعی رو هیچ‌وقت داخل کد ننویسیم — از فایل امن جدا (مثل local.properties) بخونیمشون
