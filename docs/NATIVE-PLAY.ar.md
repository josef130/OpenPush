# Google Play مباشرة مع بقاء اختيار متصفح الويب

شرط القبول: مشاهدة صفحة com.chikh.nano داخل com.android.vending بعد نقرة FCM، مع
بقاء اختيار المتصفح لرابط الويب. البناء وعودة startActivity لا يكفيان لإثبات ذلك.

## التغيير

في التطبيق التجريبي نجمع الآن الخيارين:

```java
.httpsHost("www.google.com")
.httpsHost("play.google.com")
.openUrlsInBrowser()
.preferGooglePlay()
```

كانت النسخة السابقة ترفض الجمع بين الخيارين وتنفذ browser selector أولًا.
أُزيل التعارض، وأصبحت محاولة Google Play تسبق تطبيق سياسة المتصفح على روابط Play فقط.
إذا استعمل المضيف openUrlsInBrowser وحدها يبقى سلوك النسخة السابقة. إذا استعمل
preferGooglePlay وحدها يبقى خيار HTTPS العام دون selector كما في المضيفين السابقين.

Destination.parse الموجود يقرأ URI ويتحقق من اسم المضيف والمسار /store/apps/details
ومعامل id النصي بعد فك الترميز، ويرفض id الفارغ/المكرر/غير الصالح وuserinfo وfragment
والمسار المشفر غير المطابق. لا يعتمد على احتواء النص على اسم النطاق. اسم الحزمة الصحيح
يصبح رابط HTTPS canonical إلى صفحة التطبيق نفسها. Route والبروتوكول لم يتغيرا.
روابط صفحات Play الأخرى لا تُخمن كصفحات تطبيق؛ تبقى روابط ويب إن وافق المضيف على نطاقها.

يستعمل مسار المتجر ACTION_VIEW مع الرابط الموحّد وsetPackage("com.android.vending")
دون selector متصفح أو أسماء Activities خاصة. هذه الآلية موثقة رسميًا:
https://developer.android.com/distribute/marketing-tools/linking-to-google-play#OpeningDetails

عند ActivityNotFoundException أوSecurityException أوفشل Runtime متزامن يُسجل سبب الفشل
ثم STORE_FALLBACK. يفتح الرابط HTTPS نفسه بسياسة المتصفح الحالية؛ لا يُفتح التطبيق
المستهدف ولا الصفحة الرئيسية للمتجر كبديل. إذا فشل المتصفح أيضًا تُحتوى الاستثناءات في
HANDLER_FAILED، ويبقى المعرّف محجوزًا وفق حماية النقرات القائمة.

إذا أعاد resolveActivity حزمة مختلفة لطلب مقيّد بحزمة Google Play، يُسجل
STORE_HANDLER_MISMATCH ولا تُطلق هذه الحزمة بوصفها Google Play. يلي ذلك HTTPS fallback.
هذه حالة مرصودة على الهاتف، وليست طريقة لتعطيل مكوّن نظام أو تجاوز إعداد أمني.
إذا عاد resolver فارغًا بسبب قيود الرؤية، لا نمنع المحاولة الأصلية؛ startActivity هو الحكم
على إتاحة المعالج المتزامنة. لا يستطيع SDK التقاط انهيار عملية خارجية بعد عودة الإطلاق.

سجلات الإطلاق لا تحتوي URL أوquery أوFCM token أوpayload أوexception message.
تحتوي action/scheme/requestedPackage/resolvedComponent/outcome/browserSelector وبصمة
وجهة مملحة لكل عملية، كما في التتبع السابق. RETURNED وOPENED ليسا إثبات ظهور صفحة.

لم تتغير هوية PendingIntent: requestCode=0 مع data فريد openpush://click/<op_id> و
UPDATE_CURRENT|IMMUTABLE، وCLEAR_TOP|SINGLE_TOP. القيم الأصلية op_* تبقى في extras.
لم تُحذف حماية التكرار أوتتغير قواعد deep links أوخدمة FCM للمضيف أوFirebase/Gradle.
الإشعار العادي بلا غلاف توجيه يبقى على فتح التطبيق؛ غلاف op_* غير المكتمل يرفض أو يبقى
تابعًا للمضيف عند غياب op_v، ولا يُخمن SDK رابطًا بديلًا له.

## الملفات المعدلة في هذه الجولة

- sdk/src/main/java/io/openpush/sdk/Router.java: تركيب سياستي المتجر والمتصفح، فحص اختلاف
  الحزمة المعادة، وإشارة STORE_FALLBACK.
- sample/src/main/java/io/openpush/sample/SampleApplication.java: تفعيل preferGooglePlay
  مع خيار المتصفح القائم.
- sdk/src/test/java/io/openpush/sdk/OpenPushTest.java: اختبار السياستين معًا، غياب المتجر،
  غياب المتجر والمتصفح، ومحاكاة اختلاف OEM دون قبول فتح السوق الوسيط.
- sample/src/test/java/io/openpush/sample/PracticalIntegrationTest.java: توقع Play أصليًا
  والويب بمتصفح، مع نقرتين مستقلتين وحماية التكرار.
- README.md، هذا التقرير، وملفات الأدلة/بوابة القبول.

## التشغيل الأول

core:contractTest نجح في 76 فحصًا. SDK شغّل 28 اختبارًا: 27 نجحت، وواحد فشل لأن
ShadowPackageManager القياسي رفض حقن معالج من حزمة مختلفة. أُصلحت محاكاة الاختبار
بـshadow محلي لذلك الاختبار فقط يعيد حالة OEM المرصودة؛ لم نضعف assertion.
التشغيل الأول محفوظ في verification/native-store-initial-sdk-tests.xml.
أُعيد تشغيل المجموعة بعد تصحيح المحاكاة: نجحت 28/28 اختبار SDK بصفر أخطاء/تجاوزات،
ثم 11/11 اختبار تكامل Robolectric للتطبيق. نجح sdk:assembleRelease.
نتيجة JSON: verification/native-store-results.json. البناء بإعداد localOnly لهذا التشغيل
مخصص لاختبارات JVM، ولا يُثبت APK منه على الهاتف. نسخة Firebase الفعلية تُبنى منفصلة.

## الهاتف المتاح والمقارنة خارج SDK

adb أظهر هاتفًا واحدًا c7fd4eae؛ لا AVD معدّ. طُلب هاتف ثانٍ من المستخدم للمقارنة.
Google Play مثبت ومفعّل؛ metadata الفعلي محفوظ في native-store-device-package.txt.

استعلام HTTPS listing المقيّد بـvending أعاد
com.daemon.shelper/.intercept.ost.MarketDeepLinkHandlerActivity. واستعلام market scheme
للمقارنة أعاد المكوّن نفسه؛ لم نغيّر بروتوكول SDK إلى market ولم نطلق صيغة intent://.
طلب ACTION_VIEW/HTTPS خارج SDK مع setPackage(vending) أطلق
com.heytap.market/.oaps.compatibility.WebBridgeCompatibleActivity مجددًا.
الدليل: verification/native-store-outside-sdk-current.txt.
هذا لا يحقق شرط صفحة Google Play، حتى إن أعاد am start الحالة ok.

## اختبارات الإشعارات المخطط تشغيلها بعد تثبيت النسخة

A: رابط Play الكامل، op_id=native-split-a-20261017-01.
B: com.chikh.nano، معرّف B جديد.
C: https://www.google.com، معرّف C جديد، اختيار المتصفح القائم.
D: رابط listing بـid غير صالح، معرّف D جديد، دون فتح وجهة أوتعطل.
كل رسالة تستعمل op_v=1 وop_type=url وop_id وop_target. لا Review/Publish لحملة عامة؛
فقط Send test message إلى رمز هاتف الاختبار. تُسجل النتائج الفعلية أدناه بعد التنفيذ.

الحالة الحالية: قبول فتح صفحة Google Play فعليًا غير مثبت، والمرحلة التالية ممنوعة.
