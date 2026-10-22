# فحص روابط الويب مع Custom Data

الهدف المؤكد من المستخدم: فتح روابط Google وGoogle Play في متصفح خارجي الآن، وتأجيل
محاولة فتح تطبيق المتجر. لا يُعتبر فتح المتصفح ناجحًا لمجرد البناء.

## الأسباب المحددة في الكود الحالي قبل التعديل

1. SampleApplication يوافق على example.com وplay.google.com فقط. رابط www.google.com
   صالح في Destination، لكنه يرفض في Router لعدم وجود قاعدة تطابق نطاقه تمامًا.
   الأمر نفسه ينطبق على console.firebase.google.com. هذا نقص إعداد في المثال، وليس
   استبدال قيمة op_target بوجهة التطبيق في Destination.
2. op_target وop_id وحدهما لا يشكلان غلاف توجيه. Route يحتاج op_v=1 وop_type=url أيضًا.
   دون op_v يحتفظ المضيف بالرسالة وتعيد النقرة IGNORED؛ مع op_v ودون نوع صالح ترفض.
   لذلك لا يصح تفسير كل بقاء في MainActivity بأنه فشل ACTION_VIEW.
3. إعادة استخدام op_id سبق تنفيذه تعيد DUPLICATE؛ تغيير الرابط وحده لا يعيد التنفيذ.

## مقارنة المسارات

بدون غلاف OpenPush، يستعمل HostMessageHandler في المقدمة PendingIntent صريحًا إلى
MainActivity؛ لا ينشئ ACTION_VIEW ولا يقرأ رابطًا من عنوان/نص الإشعار. في الخلفية يتولى
FCM عرض Notification/Notification+Data ويصل النقر إلى المشغّل. لذلك لم نثبت أن نفس
الإصدار ونفس إعداد الإشعار يفتحان الويب بدون Custom Data كما أفاد المستخدم؛ لا ننكر
المشاهدة، لكن يلزم استكمال الرسالة المقطوعة ومقارنة إعداد الإشعار السابق الفعلي.

مع الغلاف الكامل: op_target → Route.parse → Destination.parse → قواعد Router.
showNotification ينسخ op_* إلى Intent صريح للمضيف، يميزه
openpush://click/<op_id> ويستعمل CLEAR_TOP|SINGLE_TOP. requestCode=0 مع
UPDATE_CURRENT|IMMUTABLE؛ اختلاف Intent.data يميز النقرتين. بعد دخول MainActivity
عبر onCreate أو onNewIntent، يستخرج handleIntent الغلاف ويختار المعالج ويحجز المعرّف
ثم ينشئ Router ACTION_VIEW مع CATEGORY_BROWSABLE والرابط نفسه. أُضيف وضع متصفح اختياري
يستخدم selector من ACTION_MAIN وCATEGORY_APP_BROWSER؛ لا يفرض package المتجر ولا component
خاصًا. يختار Android متصفحًا أو يعرض اختيار المتصفحات. لا يتراجع إلى متجر إذا غاب المتصفح.

## التعديل

- sample/src/main/java/io/openpush/sample/SampleApplication.java: السماح لـwww.google.com
  وgoogle.com، وتبديل preferGooglePlay إلى openUrlsInBrowser؛ play.google.com مسموح سابقًا.
- sample/src/test/java/io/openpush/sample/PracticalIntegrationTest.java: حالتان جديدتان؛
  نقرتان مستقلتان إلى Google وPlay في المتصفح، وغلاف ناقص يمكن تصحيحه دون استهلاك المعرّف.
- sdk/src/main/java/io/openpush/sdk/Router.java وOpenPush.java: إضافة openUrlsInBrowser،
  مع رفض الجمع بينها وبين preferGooglePlay، وتسجيل browserSelector دون بيانات حساسة.
- sdk/src/main/AndroidManifest.xml: query محدد لفئة المتصفح، دون QUERY_ALL_PACKAGES.
- sdk/src/test/java/io/openpush/sdk/OpenPushTest.java: غياب المتصفح واحتواء الفشل، وتعارض
  الوضعين، وحفظ الروابط ومنع تكرار الإطلاق في وضع المتصفح، مع إبقاء اختبارات المتجر السابقة.
- README.md: توضيح النطاقات والغلاف الكامل والفرق بين IGNORED وREJECTED.

لم تتغير Destination.java أوRoute.java أوهوية PendingIntent؛ لا دليل على استبدال الوجهة
فيها. التعديلات تحتاج APK جديدًا؛ لا تحدث النسخة المثبتة تلقائيًا. لا تغيير في Firebase/Gradle.

## بيانات الاختبار الصحيحة

```text
op_v=1
op_id=google-web-fresh-001
op_type=url
op_target=https://www.google.com
```

يجب استعمال معرّف جديد في كل إرسال مقصود. لتجربة صفحة Play في المتصفح استبدل القيمة بـ
op_target=https://play.google.com/store/apps/details?id=com.chikh.nano ومعرّف جديد.
فتح تطبيق Google Play نفسه مؤجل. preferGooglePlay محفوظة ومختبرة آليًا للمضيفين الحاليين.

مرجع selector الرسمي:
https://developer.android.com/reference/android/content/Intent#setSelector(android.content.Intent)

## النتائج المنفذة

- نجح sdk:testDebugUnitTest: 25 اختبارًا، صفر إخفاقات/أخطاء/تجاوزات؛ تشمل اختبارات
  preferGooglePlay السابقة واختبارات وضع المتصفح الجديدة.
- نجح sample:testDebugUnitTest مع openpushLocalOnly: 11 اختبارًا، صفر إخفاقات/أخطاء/تجاوزات.
  هذا إعداد JVM فقط؛ لم يُثبّت APK منه على الهاتف.
- نجح sdk:assembleRelease ثم sample:assembleDebug بإعداد Firebase الحقيقي.
- نجح sdk:lintDebug: صفر أخطاء، 5 تحذيرات تحديث اعتماديات؛ لم تُحدّث الاعتماديات.
- ثُبّت build/practical-apks/OpenPush-browser-debug.apk على RMX3852 / API34.
  SHA-256: fa523d8b7fea198a27509ecf0b02ace058721a17ca0cd4268db1b267fc88438a.

أُرسل من Firebase Console عبر Send test message إلى رمز الهاتف الأخير فقط:
العنوان OpenPush Browser Google 01، المعرّف browser-google-20261017-01،
والوجهة https://www.google.com، مع op_v=1 وop_type=url.
دخلت نقرة FCM بمفاتيح Google وop_* عبر مسار الخلفية. قُبلت الوجهة وحُجز المعرّف مرة
واحدة. بصمة CLICK/RESOLVED/DISPATCH/Intent متطابقة e82a1456009ecbc3015ca53d
في العملية 15623؛ ظهر browserSelector=true وrequestedPackage=null، وحُل الطلب
إلى android/com.android.internal.app.ResolverActivity، ثم عاد RETURNED.
هذا دليل على اختيار مسار المتصفح، **وليس دليلًا على ظهور صفحة Google**.
طُلب من المستخدم اختيار Chrome أوOpera مرة واحدة وتأكيد الصفحة؛ النتيجة المرئية معلقة.
فحص المكوّن الظاهر اللاحق لم يثبت وجود Chrome/Opera/Resolver أوSample في المقدمة،
ولم نفحص أي محتوى شخصي. الدليل browser-mode-google-top.json.

السجل: verification/browser-mode-live-log.txt؛ النتائج: browser-mode-tests.json؛
لقطة المفاتيح المرسلة: browser-mode-google-console.jpg.

لم يكتمل إرسال اختبار Play بالمتصفح: توقف التحكم بتبويب Firebase أثناء إعداد الإشعار
الثاني، ولم نفترض اكتمال التغييرات أو الإرسال. لا يُحسب اختبارًا فيزيائيًا ناجحًا.
لم تكتمل مصفوفة المقدمة/الخلفية/إنهاء العملية والإشعارين معًا لهذه النسخة.

محاولة adb خارج SDK بـ--selector فشلت في الحل لأن تمثيل سطر الأوامر أضاف الرابط
إلى selector نفسه؛ حُفظت كفشل في browser-mode-outside-sdk-play.txt، ولم تُحسب
كدليل فشل/نجاح لكود Android الذي ينشئ selector دون data. رفضت المراجعة الآلية
محاولة تمثيل بديلة بـintent://؛ لم تُنفذ، واستُخدم كود Android API في التطبيق بدلًا منها.

## الحالة

إصلاح الإعداد ووضع المتصفح مبنيان ومثبتان، لكن نجاح ظهور الصفحتين فعليًا **غير مثبت بعد**.
لم نبدأ اختبار الفتح المباشر لتطبيق المتجر أو المرحلة التالية.
