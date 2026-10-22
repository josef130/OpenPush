# إعادة اختبار الوصول والصورة ورابط اللعبة

> تقرير تاريخي قبل إصلاح التصنيف. رفض الباكاج أدناه يصف النسخة القديمة فقط.
> النسخة الحالية تحوله تلقائيًا؛ راجع [الإصلاح الحالي](ROUTING-FIX.ar.md).

2026-10-17 — الهاتف RMX3852، Android 14 / API 34.

## الوصول

عند بدء التحقيق كان Android يسجل stopped=true لحزمة io.openpush.sample، مع بقاء إذن
POST_NOTIFICATIONS ممنوحًا. لا يحدد هذا القياس من أوقف الحزمة أو سبب إيقافها. فُتح التطبيق
مجدّدًا وتأكد stopped=false. تجنب Force stop أثناء اختبار الاستقبال؛ بعده افتح التطبيق يدويًا.
أداة scripts/test-device.ps1 تعيد الآن فتح المثال بعد الاختبارات، وتحفظ رمز خروج instrumentation
قبل تنفيذ ذلك حتى لا تخفي فشل الاختبارات.

أُرسلت رسائل اختبار إلى الهاتف المحدد فقط، دون نشر حملة عامة. وصلت رسالة الرابط الأولى
ورسالة الباكاج الخام فعلًا، وكلتاهما تحتوي hasPicture=true وفق NotificationManager للتطبيق نفسه.
الصورة المستخدمة في Notification image (optional):
https://i.ytimg.com/vi/N8iX6yl5GYQ/maxresdefault.jpg

الصورة في الخلفية يعرضها Firebase Messaging. SDK الحالي في المقدمة يعرض نص الإشعار ولا
ينزّل صورًا عن بعد؛ إضافة رابط صورة في Console وحدها لا تضيف تنزيل الصور لمسار المقدمة.

## البيانات اللازمة

استخدم هذه Custom Data، مع op_id جديد لكل رسالة/وجهة جديدة:

```text
op_v = 1
op_id = play-url-image-20261017-02
op_type = url
op_target = https://play.google.com/store/apps/details?id=com.chikh.nano
```

أضيف `.httpsHost("play.google.com")` إلى SampleApplication صراحةً. التطبيقات المستضيفة
تحدد نطاقاتها المسموحة بنفسها. لم تُغيّر مكتبات Firebase أو إعدادات المشروع المستضيف.
تكرار op_id يمنع تكرار فتح الوجهة؛ وهو ليس ضمانًا لمنع إعادة عرض الرسالة من FCM.

## الباكاج وحده

نُفذ اختبار Console فعلي بهذه البيانات:

```text
op_v = 1
op_id = play-bare-image-20261017-01
op_type = url
op_target = com.chikh.nano
```

وصل الإشعار والصورة في الخلفية، ثم رُفض التوجيه عند تشغيل PendingIntent:
REJECTED زاد من 6 إلى 7 وبقي OPENED=12. **لا يوجد تحويل تلقائي للباكاج إلى رابط**.
في المقدمة قد يُرفض هذا الغلاف غير الصحيح قبل إنشاء إشعار؛ رؤية إشعار الخلفية لا تثبت صحة الغلاف.
الدليل: verification/device-bare-package-image.json. نجاح الاختبار هنا يعني إثبات الرفض الآمن.

لإرسال النوع url استخدم رابط HTTPS الكامل. دعم الباكاج كإجراء مستقل يتطلب تسجيل معالج
صريح عبر action؛ لا تغيّر تفسير url بصمت. الصيغة الرسمية لرابط Google Play موضحة في
[وثائق Android](https://developer.android.com/distribute/marketing-tools/linking-to-google-play).

## فتح الرابط الكامل

محاولة play-url-image-20261017-01: وصل الإشعار والصورة؛ سجل المحرك OPENED، لكن اختبار
ظهور Google Play فشل: الحزمة المرصودة com.android.systemui. أكد المستخدم أنه لم تظهر
وجهة أو نافذة. لا تُعد OPENED وحدها إثباتًا لظهور الوجهة؛ تعني أن طلب التنقل لم يرم استثناءً.
الدليل: verification/device-play-url-image.json.

أُرسلت محاولة ثانية بالمعرّف play-url-image-20261017-02 للتحقق بالنقر اليدوي؛ أكد المستخدم
عدم فتح وجهة. أظهر resolve-activity أن معالج Android الافتراضي لهذا الرابط هو
com.daemon.shelper/.intercept.ost.MarketDeepLinkHandlerActivity، وليس Google Play.

أُضيف خيار صريح لاختيار تطبيق Google Play الرسمي لهذا النوع من الروابط:

```java
.httpsHost("play.google.com")
.preferGooglePlay()
```

لا يضيف الخيار نطاقات مسموحة ضمنيًا ولا يغير روابط HTTPS الأخرى. يستعمل ACTION_VIEW
مع setPackage("com.android.vending")، وإذا لم يتوفر Google Play يعود إلى رابط HTTPS نفسه.
لم تُغير إعدادات الحماية أو التطبيقات الافتراضية على الهاتف. على هذا الجهاز أعاد استعلام
resolve-activity الحزمة الوسيطة نفسها حتى مع تحديد com.android.vending، وكذلك مع market://details.
أكد اختبار am start المباشر خارج SDK أن Activity الفعلية هي MarketDeepLinkHandlerActivity.
الدليل verification/device-play-explicit-handler.txt. لذلك لا يُعد الخيار ضمانًا لتجاوز
اعتراض الجهاز أو إصلاحًا مثبتًا لفتح المتجر عليه.

المحاولة النهائية play-native-image-20261017-03 على النسخة المحدثة لم تجتز اختبار الوجهة:
انتهى startActivitySync في أداة الاختبار بمهلة 45 ثانية، قبل توليد دليل وجهة جديد.
راجع verification/device-play-native-test.txt وdevice-play-native-image.json. لم تُحسب
سجلات cache الأقدم على أنها دليل لهذه المحاولة. القياس اللاحق لم يجد إشعارًا معلّقًا؛ لا
يُستنتج منه وحده نجاح التسليم أو الوجهة. أُعيد فتح التطبيق وتأكد stopped=false في النهاية.

## البناء والفحوص

- نجح بناء APK المهيأ بـFirebase وحزمة اختبارات الجهاز.
- نجح بناء Release AAR واختبارات SDK الستة عشر، بما فيها الخيار الصريح، وبقاء الموافقة
  على النطاق إلزامية، والمحافظة على السلوك الافتراضي، والرجوع إلى HTTPS عند غياب Play، ومنع التكرار.
- نجحت اختبارات الجهاز السبعة بعد تثبيت النسخة الجديدة.
- نجحت اختبارات المثال التسعة تحت Robolectric، ومنها الموافقة على رابط Play الكامل
  ورفض الباكاج الخام. لا تثبت هذه الاختبارات تسليم FCM أو ظهور Play Store.
- APK المثبت: build/practical-apks/OpenPush-play-debug.apk، بفحص 128 إدخالًا وصفر مؤشرات
  لمفاتيح خادم ضمن القواعد المحدودة. التقرير verification/apk-audit.play.json.
- بصمة APK: e765d646ea8fbaa4a795910176965f7c5f50f4da5eb69a7e37d3ff60851be176.

نسخة الخيار الصريح المثبتة لاحقًا: build/practical-apks/OpenPush-play-native-debug.apk،
بصمة f00d60b07fa9252ef288646e71dc25f0d717a3570b6872ab9fb9667bfcdcee54.
أُعيدت اختبارات الجهاز السبعة بنجاح، وأُعيد فحص 128 إدخال APK دون مؤشرات سرية مطابقة؛
التقرير verification/apk-audit.play-native.json. هذا لا يثبت فتح المتجر في وجود اعتراض النظام.
